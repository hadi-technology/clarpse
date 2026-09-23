package com.hadi.clarpse.compiler;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Represents a collection of source files to be parsed by Clarpse.
 *
 * <p>This class handles loading source files from various sources including:
 * <ul>
 *   <li>Local directories</li>
 *   <li>ZIP archives</li>
 *   <li>Individual files</li>
 * </ul>
 *
 * <p>Files are organized by programming language and can be filtered by
 * language when needed. The class also manages temporary directories for
 * extracted archives and ensures proper cleanup via {@link AutoCloseable}.
 *
 * <p>Usage example:
 * <pre>{@code
 * try (ProjectFiles files = new ProjectFiles("/path/to/project")) {
 *     List<ProjectFile> javaFiles = files.files(Lang.JAVA);
 *     // Process files...
 * }
 * }</pre>
 */
public class ProjectFiles implements AutoCloseable {

    private static final Logger LOGGER = LogManager.getLogger(ProjectFiles.class);

    private static final int MAX_ZIP_ENTRIES = ClarpseProperties.getInt("clarpse.zip.maxEntries", 100000);
    private static final long MAX_TOTAL_UNCOMPRESSED_BYTES =
            ClarpseProperties.getLong("clarpse.zip.maxTotalUncompressedBytes", 200L * 1024 * 1024);
    private static final long MAX_ENTRY_UNCOMPRESSED_BYTES =
            ClarpseProperties.getLong("clarpse.zip.maxEntryUncompressedBytes", 10L * 1024 * 1024);
    /**
     * How many paths of files in languages this library does not read one instance may hold. Only
     * paths are kept, so the cost is a few dozen bytes each; the cap is what stops a pathological
     * archive from turning that into an unbounded one. Past it the record stops growing and reports
     * itself incomplete, rather than dropping paths and calling the result exhaustive.
     */
    private static final int MAX_UNREAD_SOURCE_PATHS =
            ClarpseProperties.getInt("clarpse.unreadSourceFiles.maxPaths", 20000);
    private final Map<Lang, List<ProjectFile>> langToFilesMap = new HashMap<>();
    private int size = 0;
    private String projectDir;
    private boolean tempProjectDir = false;
    private final Map<String, String> configFiles = new HashMap<>();
    private final Set<String> unreadSourcePaths = new LinkedHashSet<>();
    private boolean unreadSourcePathsComplete = true;

    /**
     * Constructs a ProjectFiles instance from a path to a local directory or zip file.
     */
    public ProjectFiles(final String projectPath) throws Exception {
        File projectFiles = new File(projectPath);
        LOGGER.info("Project source files location: " + projectFiles.getPath());
        if (!projectFiles.exists()) {
            throw new IllegalArgumentException("The given path does not exist!");
        } else if (projectFiles.isFile()
                && anyMatchExtensions(projectFiles.getName(), new String[]{".zip"})) {
            initFilesFromZipPath(projectFiles);
        } else if (projectFiles.isDirectory()) {
            initFilesFromDir(projectFiles);
        } else {
            throw new IllegalArgumentException(
                    "The supplied project path must either be a local directory path or a "
                            + "local zip file path.");
        }
    }

    public ProjectFiles(InputStream zipFileInputStream) throws Exception {
        extractProjectFilesFromStream(zipFileInputStream, null);
    }

    /**
     * Reads a zip archive from a stream, handing every entry it does not keep to the given
     * observer. The files this instance holds are the files it holds without an observer; see
     * {@link DiscardedEntryObserver} for what an observer may and may not do. The stream is closed.
     *
     * @param zipFileInputStream The archive to read.
     * @param observer The observer for the entries this instance does not keep.
     * @return The source and configuration files of the archive.
     * @throws Exception If the archive cannot be read, or the observer throws.
     */
    public static ProjectFiles fromZip(final InputStream zipFileInputStream,
                                       final DiscardedEntryObserver observer) throws Exception {
        Objects.requireNonNull(zipFileInputStream, "A zip input stream is required.");
        Objects.requireNonNull(observer, "An observer is required; use the constructor without one.");
        final ProjectFiles projectFiles = new ProjectFiles();
        projectFiles.extractProjectFilesFromStream(zipFileInputStream, observer);
        return projectFiles;
    }

    /**
     * Reads a local zip archive, handing every entry it does not keep to the given observer. The
     * files this instance holds are the files it holds without an observer; see
     * {@link DiscardedEntryObserver} for what an observer may and may not do.
     *
     * @param zipFile The archive to read.
     * @param observer The observer for the entries this instance does not keep.
     * @return The source and configuration files of the archive.
     * @throws Exception If the archive cannot be read, or the observer throws.
     */
    public static ProjectFiles fromZip(final File zipFile,
                                       final DiscardedEntryObserver observer) throws Exception {
        Objects.requireNonNull(zipFile, "A zip file is required.");
        try (InputStream io = FileUtils.openInputStream(zipFile)) {
            return fromZip(io, observer);
        }
    }

    public ProjectFiles(final Collection<ProjectFile> projectFiles) {
        projectFiles.forEach(this::insertFile);
    }

    public ProjectFiles() {
    }

    /**
     * Copy constructor - creates a deep copy of an existing ProjectFiles instance.
     *
     * @param other the ProjectFiles instance to copy
     */
    public ProjectFiles(final ProjectFiles other) {
        this.projectDir = other.projectDir;
        this.tempProjectDir = false; // Copy doesn't own the temp dir
        this.size = other.size;
        // Deep copy langToFilesMap with cloned ProjectFile instances
        other.langToFilesMap.forEach((lang, files) -> {
            List<ProjectFile> copiedFiles = new ArrayList<>();
            for (ProjectFile file : files) {
                copiedFiles.add(file.copy());
            }
            this.langToFilesMap.put(lang, copiedFiles);
        });
        // Deep copy configFiles
        this.configFiles.putAll(other.configFiles);
        this.unreadSourcePaths.addAll(other.unreadSourcePaths);
        this.unreadSourcePathsComplete = other.unreadSourcePathsComplete;
    }

    /**
     * Creates a deep copy of this ProjectFiles instance.
     *
     * @return a new ProjectFiles instance with the same files
     */
    public ProjectFiles copy() {
        return new ProjectFiles(this);
    }

    private void initFilesFromZipPath(File projectFiles) throws Exception {
        try (InputStream io = FileUtils.openInputStream(projectFiles)) {
            LOGGER.info("Converted zip path to an input stream..");
            extractProjectFilesFromStream(io, null);
        }
    }

    /**
     * For all files, the immediate root subdirectories (and any files directly within) are
     * deleted and all remaining subdirectories are shifted over in its place. If there are any
     * files within the current root directory which will get deleted as a result, an exception
     * is thrown.
     * <p>
     * Sample transformation: /test/foo/cakes/lol.txt  ---> /foo/cakes/lol.txt
     */
    public void shiftSubDirsLeft() {
        LOGGER.info("Shifting all source files sub-dirs left..");
        final String separator = "/";
        this.langToFilesMap.forEach((lang, files) -> this.langToFilesMap.put(lang, files.stream().map(file -> {
            if (StringUtils.countMatches(file.path(), separator) > 1) {
                return new ProjectFile(file.path().substring(
                        StringUtils.ordinalIndexOf(file.path(), separator, 2)
                ), file.content());
            } else {
                throw new IllegalArgumentException("Cannot shift file: " + file.path() + ".");
            }
        }).collect(Collectors.toList())));
        final Map<String, String> shiftedConfigFiles = new HashMap<>();
        this.configFiles.forEach((path, content) -> shiftedConfigFiles.put(shiftConfigPath(path), content));
        this.configFiles.clear();
        this.configFiles.putAll(shiftedConfigFiles);
        shiftUnreadSourcePaths();
        deleteTempDir();
    }

    public int size() {
        return this.size;
    }

    private void initFilesFromDir(File projectFiles) throws IOException {
        LOGGER.info("Reading source files from dir: " + projectFiles.getPath());
        this.projectDir = projectFiles.getAbsolutePath();
        this.tempProjectDir = false;
        Iterator<File> it = FileUtils.iterateFiles(projectFiles, null, true);
        while (it.hasNext()) {
            File nextFile = it.next();
            if (nextFile.isFile()) {
                if (shouldLoadPath(nextFile.getAbsolutePath())) {
                    String content = FileUtils.readFileToString(nextFile, StandardCharsets.UTF_8);
                    this.insertFile(new ProjectFile(nextFile.getAbsolutePath(), content));
                } else {
                    recordUnreadSourceFile(nextFile.getAbsolutePath());
                }
            }
        }
        LOGGER.info("Read " + this.size + " files.");
    }

    private void extractProjectFilesFromStream(final InputStream is, final DiscardedEntryObserver observer)
            throws Exception {
        LOGGER.info("Extracting source files from input stream..");
        int filesCounter = 0;
        int entryCounter = 0;
        long totalBytes = 0;
        try (ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                entryCounter += 1;
                if (entryCounter > MAX_ZIP_ENTRIES) {
                    throw new IllegalArgumentException("Zip contains too many entries.");
                }
                try {
                    if (!entry.isDirectory()) {
                        String safeName = sanitizeEntryName(entry.getName());
                        if (safeName == null) {
                            noteUnrecordableEntry(entry.getName());
                            throw new IllegalArgumentException("Unsafe zip entry path: " + entry.getName());
                        }
                        String entryPath = safeName.replace('\\', '/');
                        String fileName = Paths.get(safeName).getFileName().toString();
                        Lang lang = Lang.langFromExtn(FilenameUtils.getExtension(fileName));
                        boolean configFile = isConfigFile(entryPath);
                        boolean kept = lang != null || configFile;
                        if (!kept) {
                            recordUnreadSourceFile(File.separator + safeName.replace(" ", "_"));
                        }
                        boolean observed = !kept && observer != null && observesPath(observer, entryPath);
                        byte[] content = null;
                        if (kept || observed) {
                            content = readEntryBytes(zis, MAX_ENTRY_UNCOMPRESSED_BYTES, safeName);
                            totalBytes += content.length;
                        } else {
                            totalBytes += countEntryBytes(zis, MAX_ENTRY_UNCOMPRESSED_BYTES, safeName);
                        }
                        if (totalBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                            throw new IllegalArgumentException("Zip exceeds maximum uncompressed size.");
                        }
                        if (kept) {
                            String textContent = new String(content, StandardCharsets.UTF_8);
                            if (configFile) {
                                insertConfigFile(new ProjectFile(entryPath, textContent));
                            }
                            if (lang != null) {
                                ProjectFile newFile = new ProjectFile(
                                        File.separator + safeName.replace(" ", "_"),
                                        textContent);
                                LOGGER.debug("Extracted project file " + newFile + ".");
                                this.insertFile(newFile);
                                filesCounter += 1;
                            }
                        } else if (observed) {
                            observe(observer, entryPath, content, entry);
                        }
                    }
                } catch (final IllegalArgumentException e) {
                    LOGGER.warn("Skipping problematic zip entry {}: {}", entry.getName(), e.getMessage());
                }
                try {
                    zis.closeEntry();
                } catch (final IOException e) {
                    LOGGER.warn("Failed closing zip entry {}.", entry.getName(), e);
                }
                entry = zis.getNextEntry();
            }
        } catch (final ObserverFailure e) {
            throw e.failure();
        } catch (final IllegalArgumentException e) {
            throw e;
        } catch (final Exception e) {
            throw new Exception("Error while  reading  files from zip!", e);
        }
        LOGGER.info("Extracted " + filesCounter + " files.");
    }

    /**
     * Asks the observer whether an entry's bytes are worth reading. An observer that throws must
     * not be mistaken for a problematic entry, which extraction skips and carries on from, so its
     * exception travels wrapped past that recovery and is unwrapped once the archive is closed.
     */
    private static boolean observesPath(final DiscardedEntryObserver observer, final String entryPath) {
        try {
            return observer.observesPath(entryPath);
        } catch (final RuntimeException e) {
            throw new ObserverFailure(e);
        }
    }

    /** Hands a discarded entry to the observer, whose exceptions travel as {@link ObserverFailure}. */
    private static void observe(final DiscardedEntryObserver observer, final String entryPath,
                                final byte[] content, final ZipEntry entry) {
        final Instant lastModified = lastModifiedOf(entry);
        try {
            observer.observe(entryPath, content, lastModified);
        } catch (final RuntimeException e) {
            throw new ObserverFailure(e);
        }
    }

    private static Instant lastModifiedOf(final ZipEntry entry) {
        final FileTime lastModified = entry.getLastModifiedTime();
        if (lastModified == null) {
            return null;
        }
        return lastModified.toInstant();
    }

    /** Carries an observer's exception past the per-entry recovery that would otherwise swallow it. */
    private static final class ObserverFailure extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final RuntimeException failure;

        private ObserverFailure(final RuntimeException failure) {
            super(failure);
            this.failure = failure;
        }

        private RuntimeException failure() {
            return this.failure;
        }
    }

    private String sanitizeEntryName(String entryName) {
        if (entryName == null || entryName.trim().isEmpty()) {
            return null;
        }
        String normalized = entryName.replace("\\", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.contains(":")) {
            return null;
        }
        Path normalizedPath = Paths.get(normalized).normalize();
        for (Path part : normalizedPath) {
            if ("..".equals(part.toString())) {
                return null;
            }
        }
        return normalizedPath.toString();
    }

    private byte[] readEntryBytes(InputStream inputStream, long maxBytes, String entryName) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IllegalArgumentException(
                        "Zip entry exceeds maximum allowed size: " + entryName + ".");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    /**
     * Reads past an entry whose bytes nothing needs, returning how many bytes it held so it counts
     * against the archive's uncompressed-size limit like an entry that was kept.
     */
    private long countEntryBytes(InputStream inputStream, long maxBytes, String entryName) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IllegalArgumentException(
                        "Zip entry exceeds maximum allowed size: " + entryName + ".");
            }
        }
        return total;
    }

    private boolean anyMatchExtensions(String s, String[] extn) {
        return Arrays.stream(extn).anyMatch(ending -> s.toLowerCase(Locale.ROOT).endsWith(ending));
    }

    private String shiftConfigPath(final String configPath) {
        if (configPath == null) {
            throw new IllegalArgumentException("Cannot shift config path: null");
        }
        final String normalized = configPath.replace('\\', '/');
        final int firstSeparator = normalized.indexOf('/');
        if (firstSeparator < 0 || firstSeparator + 1 >= normalized.length()) {
            throw new IllegalArgumentException("Cannot shift config path: " + configPath + ".");
        }
        return normalized.substring(firstSeparator + 1);
    }

    public final void insertFile(final ProjectFile file) {
        Lang fileLang = Lang.langFromExtn(file.extension());
        if (fileLang != null) {
            this.insertFile(file, fileLang);
        } else if (isConfigFile(file.path())) {
            this.insertConfigFile(file);
        } else {
            recordUnreadSourceFile(file.path());
            LOGGER.debug("Skipping file: " + file.path() + ".");
        }
    }

    private void insertFile(final ProjectFile file, Lang lang) {
        if (this.langToFilesMap.containsKey(lang)) {
            this.langToFilesMap.get(lang).add(file);
        } else {
            this.langToFilesMap.put(lang, new ArrayList<>());
            this.langToFilesMap.get(lang).add(file);
        }
        this.size += 1;
        LOGGER.debug("Inserted file " + file + ".");
    }

    private void insertConfigFile(final ProjectFile file) {
        this.configFiles.put(normalizeConfigPath(file.path()), file.content());
        this.size += 1;
        LOGGER.debug("Inserted config file " + file + ".");
    }

    /**
     * Removes a file from this ProjectFiles instance by path. A path in a language this library
     * does not read leaves {@link #unreadSourceFiles()}, so the record does not outlive the file,
     * but such a file was never held here and removing one is not a removal: the return value and
     * {@link #size()} answer for the files this instance holds, as they always have.
     *
     * @param path the path of the file to remove
     * @return true if the file was found and removed, false otherwise
     */
    public boolean removeFile(final String path) {
        forgetUnreadSourceFile(path);
        int removedCount = 0;
        for (Map.Entry<Lang, List<ProjectFile>> entry : this.langToFilesMap.entrySet()) {
            List<ProjectFile> files = entry.getValue();
            int beforeSize = files.size();
            files.removeIf(file -> file.path().equals(path));
            removedCount += (beforeSize - files.size());
        }
        if (removedCount > 0) {
            this.size -= removedCount;
            LOGGER.debug("Removed " + removedCount + " file(s) at path: " + path + ".");
            return true;
        }
        String normalizedConfigPath = normalizeConfigPath(path);
        if (this.configFiles.remove(normalizedConfigPath) != null) {
            this.size -= 1;
            LOGGER.debug("Removed config file at path: " + normalizedConfigPath + ".");
            return true;
        }
        return false;
    }

    /**
     * Removes a file from this ProjectFiles instance.
     *
     * @param file the ProjectFile to remove
     * @return true if the file was found and removed, false otherwise
     */
    public boolean removeFile(final ProjectFile file) {
        if (file == null) {
            return false;
        }
        return removeFile(file.path());
    }

    public final Collection<ProjectFile> files(Lang language) {
        return List.copyOf(this.langToFilesMap.getOrDefault(language, new ArrayList<>()));
    }

    public final Collection<ProjectFile> files() {
        Set<ProjectFile> allFiles = new HashSet<>();
        this.langToFilesMap.forEach((lang, files) -> allFiles.addAll(files));
        this.configFiles.forEach((path, content) -> allFiles.add(new ProjectFile(path, content)));
        return Set.copyOf(allFiles);
    }

    public String projectDir() {
        if (this.projectDir != null && !this.projectDir.isEmpty()) {
            return this.projectDir;
        } else {
            this.persistDir();
            return this.projectDir;
        }
    }

    public boolean isTempProjectDir() {
        return this.tempProjectDir;
    }

    /**
     * Deletes the temporary directory this instance wrote its files to, if it wrote them. A later
     * {@link #projectDir()} writes them again. A directory this instance was read from is never
     * deleted.
     */
    @Override
    public void close() {
        deleteTempDir();
    }

    /** Whether this instance has written its files to a temporary directory not yet deleted. */
    boolean hasTempDir() {
        return this.tempProjectDir && this.projectDir != null && !this.projectDir.isEmpty();
    }

    /** Deletes the temporary directory this instance wrote its files to, if there is one. */
    void deleteTempDir() {
        if (hasTempDir()) {
            ClarpseTempDirs.delete(Paths.get(this.projectDir));
            this.tempProjectDir = false;
            this.projectDir = null;
        }
    }

    /**
     * Deletes the temporary directories Clarpse left under {@code java.io.tmpdir}: those whose names
     * start with {@code clarpse-}, last modified longer ago than {@code olderThan}, and not open in
     * this JVM.
     *
     * <p>Every temporary directory Clarpse creates is deleted when its owner is closed, and by a JVM
     * shutdown hook otherwise. A process that is killed, or dies out of memory, runs no hook; calling
     * this at startup, or periodically, removes what such a process left. Directories another
     * running JVM has open are not known to this one, so {@code olderThan} must exceed the longest
     * analysis any such JVM runs.
     *
     * @param olderThan The age past which a directory is stale.
     * @return The directories deleted.
     */
    public static List<Path> deleteStaleTempDirs(final Duration olderThan) {
        return ClarpseTempDirs.deleteStale(olderThan);
    }

    private void persistDir() {
        long startTime = System.currentTimeMillis();
        Set<String> dirs = new HashSet<>();
        final String rootDir;
        try {
            rootDir = ClarpseTempDirs.create("src").toString();
        } catch (final IOException e) {
            throw new UncheckedIOException("Could not create a temporary directory for the project files.", e);
        }
        LOGGER.info("Persisting files to " + rootDir);
        dirs.add(rootDir);
        this.langToFilesMap.forEach((lang, projectFiles) -> projectFiles.forEach(projectFile -> {
            final String relativePath = resolvePersistedRelativePath(projectFile.path());
            final String filePath = rootDir + File.separator + relativePath;
            final File file = new File(filePath);
            File parent = new File(file.getParent());
            try {
                if (!parent.exists()) {
                    Files.createDirectories(parent.toPath());
                }
                while (!dirs.contains(parent.getPath())) {
                    dirs.add(parent.getPath());
                    parent = new File(parent.getParent());
                }
                try (PrintWriter printWriter = new PrintWriter(new FileWriter(filePath, StandardCharsets.UTF_8))) {
                    printWriter.print(projectFile.content());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
        this.configFiles.forEach((relativePath, content) -> {
            Path target = Paths.get(rootDir, relativePath.split("/"));
            try {
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                Files.write(target, content.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        long elapsedTime = System.currentTimeMillis() - startTime;
        LOGGER.info(this.size() + " files were persisted in " + elapsedTime + " ms");
        this.projectDir = rootDir;
        this.tempProjectDir = true;
    }

    private String resolvePersistedRelativePath(final String projectPath) {
        if (projectPath == null || projectPath.isEmpty()) {
            throw new IllegalArgumentException("Cannot persist an empty project path.");
        }
        String normalized = projectPath.replace('\\', '/');
        if (CompilerSupport.isAbsolutePath(normalized)) {
            if (normalized.length() > 2
                    && Character.isLetter(normalized.charAt(0))
                    && normalized.charAt(1) == ':') {
                normalized = normalized.substring(2);
            }
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Cannot persist project path: " + projectPath);
        }
        return normalized.replace('/', File.separatorChar);
    }

    public Set<ProjectFile> matchingFilesByName(String matchName) {
        Set<ProjectFile> result = new HashSet<>();
        this.langToFilesMap.forEach((lang, files) -> result.addAll(files.stream().filter(
                file -> file.name().equals(matchName)).collect(Collectors.toList())));
        this.configFiles.forEach((path, content) -> {
            ProjectFile file = new ProjectFile(path, content);
            if (file.name().equals(matchName)) {
                result.add(file);
            }
        });
        return result;
    }

    private boolean isConfigFile(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String normalized = normalizeConfigPath(path).toLowerCase(Locale.ROOT);
        return normalized.equals("tsconfig.json")
                || normalized.endsWith("/tsconfig.json")
                // A tsconfig variant at the repository root has no directory in front of it, so a
                // pattern anchored on a leading slash never matches one and the file is dropped.
                // `tsconfig.base.json` beside `tsconfig.json` is the ordinary layout, and every
                // config that extends it fails to read it once it has been dropped.
                || normalized.matches("tsconfig\\.[^/]+\\.json")
                || normalized.matches(".*/tsconfig\\.[^/]+\\.json$")
                || normalized.equals("jsconfig.json")
                || normalized.endsWith("/jsconfig.json")
                || normalized.equals("package.json")
                || normalized.endsWith("/package.json")
                || normalized.equals("pyrightconfig.json")
                || normalized.endsWith("/pyrightconfig.json")
                || normalized.equals("pyproject.toml")
                || normalized.endsWith("/pyproject.toml");
    }

    private boolean shouldLoadPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        return isConfigFile(path)
                || Lang.langFromExtn(FilenameUtils.getExtension(path)) != null;
    }

    /**
     * The files this instance was given whose extension names a programming language this library
     * has no parser for. They are not parsed and are held nowhere else: a caller that asks
     * {@link #files()} whether a project holds a Kotlin or Scala file is told no whether or not the
     * project holds one, and this is what tells the two apart.
     *
     * @return A record of those files, which reports itself incomplete rather than answer for a
     *         path it could not keep.
     */
    public UnreadSourceFiles unreadSourceFiles() {
        return new UnreadSourceFiles(this.unreadSourcePaths, this.unreadSourcePathsComplete);
    }

    /**
     * Records a path this instance is discarding, when it names a source file in a language this
     * library does not read. A path that cannot be recorded leaves the record incomplete instead of
     * being dropped quietly.
     */
    private void recordUnreadSourceFile(final String rawPath) {
        if (!UnreadSourceFiles.isUnreadSourceFile(rawPath)) {
            return;
        }
        if (this.unreadSourcePaths.size() >= MAX_UNREAD_SOURCE_PATHS) {
            this.unreadSourcePathsComplete = false;
            LOGGER.warn("Reached the cap of {} unread source file paths; the record is no longer "
                    + "complete.", MAX_UNREAD_SOURCE_PATHS);
            return;
        }
        try {
            this.unreadSourcePaths.add(ProjectFile.normalizePath(rawPath));
        } catch (final IllegalArgumentException e) {
            this.unreadSourcePathsComplete = false;
            LOGGER.warn("Cannot record the unread source file {}: {}", rawPath, e.getMessage());
        }
    }

    /**
     * Notes an archive entry that holds a source file in a language this library does not read but
     * whose path cannot be kept, so the record reports the file as unknown rather than absent.
     */
    private void noteUnrecordableEntry(final String entryName) {
        if (UnreadSourceFiles.isUnreadSourceFile(entryName)) {
            this.unreadSourcePathsComplete = false;
            LOGGER.warn("Cannot record the unread source file at the unsafe entry path {}.", entryName);
        }
    }

    /** Drops a path from the unread-source-file record, so the record does not outlive the file. */
    private void forgetUnreadSourceFile(final String path) {
        if (this.unreadSourcePaths.isEmpty() || !UnreadSourceFiles.isUnreadSourceFile(path)) {
            return;
        }
        try {
            this.unreadSourcePaths.remove(ProjectFile.normalizePath(path));
        } catch (final IllegalArgumentException e) {
            LOGGER.debug("Cannot match {} against the unread source files.", path);
        }
    }

    /**
     * Shifts the unread-source-file paths the way {@link #shiftSubDirsLeft()} shifts the files this
     * instance holds. A path with no subdirectory to shift into cannot be shifted; it leaves the
     * record incomplete rather than throwing, because these files were never what the caller asked
     * to shift.
     */
    private void shiftUnreadSourcePaths() {
        final Set<String> shifted = new LinkedHashSet<>();
        for (final String path : this.unreadSourcePaths) {
            if (StringUtils.countMatches(path, "/") > 1) {
                shifted.add(path.substring(StringUtils.ordinalIndexOf(path, "/", 2)));
            } else {
                this.unreadSourcePathsComplete = false;
                LOGGER.warn("Cannot shift the unread source file {}; the record is no longer "
                        + "complete.", path);
            }
        }
        this.unreadSourcePaths.clear();
        this.unreadSourcePaths.addAll(shifted);
    }

    private String normalizeConfigPath(String path) {
        if (path == null || path.isBlank()) {
            return path;
        }
        String normalized = path.replace('\\', '/');
        if (CompilerSupport.isAbsolutePath(normalized)) {
            if (normalized.length() > 2
                    && Character.isLetter(normalized.charAt(0))
                    && normalized.charAt(1) == ':') {
                normalized = normalized.substring(2);
            }
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}
