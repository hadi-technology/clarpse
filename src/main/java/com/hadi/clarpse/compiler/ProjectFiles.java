package com.hadi.clarpse.compiler;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

    /**
     * Temp dirs created by {@link #persistDir()} that have not yet been cleaned up. A single
     * JVM-shutdown hook deletes whatever remains, so an interrupted or crashed process can no longer
     * leak extracted-source temp dirs. Normal cleanup is still {@link #close()} (and the in-place
     * cleanup in {@link #shiftSubDirsLeft()}), which also deregister here — so in steady state this
     * set only holds currently-open projects and does not grow.
     */
    private static final Set<String> PENDING_TEMP_DIRS = ConcurrentHashMap.newKeySet();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (final String dir : PENDING_TEMP_DIRS) {
                FileUtils.deleteQuietly(new File(dir));
            }
        }, "clarpse-projectfiles-tempdir-cleanup"));
    }

    private static final int MAX_ZIP_ENTRIES = ClarpseProperties.getInt("clarpse.zip.maxEntries", 100000);
    private static final long MAX_TOTAL_UNCOMPRESSED_BYTES =
            ClarpseProperties.getLong("clarpse.zip.maxTotalUncompressedBytes", 200L * 1024 * 1024);
    private static final long MAX_ENTRY_UNCOMPRESSED_BYTES =
            ClarpseProperties.getLong("clarpse.zip.maxEntryUncompressedBytes", 10L * 1024 * 1024);
    private final Map<Lang, List<ProjectFile>> langToFilesMap = new HashMap<>();
    private int size = 0;
    private String projectDir;
    private boolean tempProjectDir = false;
    private final Map<String, String> configFiles = new HashMap<>();

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
        extractProjectFilesFromStream(zipFileInputStream);
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
            extractProjectFilesFromStream(io);
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
        if (this.tempProjectDir && this.projectDir != null && !this.projectDir.isEmpty()) {
            FileUtils.deleteQuietly(new File(this.projectDir));
            PENDING_TEMP_DIRS.remove(this.projectDir);
            this.tempProjectDir = false;
            this.projectDir = null;
        }
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
            if (nextFile.isFile() && shouldLoadPath(nextFile.getAbsolutePath())) {
                String content = FileUtils.readFileToString(nextFile, StandardCharsets.UTF_8);
                this.insertFile(new ProjectFile(nextFile.getAbsolutePath(), content));
            }
        }
        LOGGER.info("Read " + this.size + " files.");
    }

    private void extractProjectFilesFromStream(final InputStream is)
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
                            throw new IllegalArgumentException("Unsafe zip entry path: " + entry.getName());
                        }
                        byte[] content = readEntryBytes(zis, MAX_ENTRY_UNCOMPRESSED_BYTES, safeName);
                        totalBytes += content.length;
                        if (totalBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                            throw new IllegalArgumentException("Zip exceeds maximum uncompressed size.");
                        }
                        String fileName = Paths.get(safeName).getFileName().toString();
                        String textContent = new String(content, StandardCharsets.UTF_8);
                        handlePotentialConfigFile(safeName, textContent);
                        Lang lang = Lang.langFromExtn(FilenameUtils.getExtension(fileName));
                        if (lang != null) {
                            ProjectFile newFile = new ProjectFile(
                                    File.separator + safeName.replace(" ", "_"),
                                    textContent);
                            LOGGER.debug("Extracted project file " + newFile + ".");
                            this.insertFile(newFile);
                            filesCounter += 1;
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
        } catch (final IllegalArgumentException e) {
            throw e;
        } catch (final Exception e) {
            throw new Exception("Error while  reading  files from zip!", e);
        }
        LOGGER.info("Extracted " + filesCounter + " files.");
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

    private void handlePotentialConfigFile(String safeName, String content) {
        String normalizedSafeName = safeName.replace("\\", "/");
        if (isConfigFile(normalizedSafeName)) {
            insertConfigFile(new ProjectFile(normalizedSafeName, content));
        }
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
     * Removes a file from this ProjectFiles instance by path.
     *
     * @param path the path of the file to remove
     * @return true if the file was found and removed, false otherwise
     */
    public boolean removeFile(final String path) {
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

    @Override
    public void close() {
        if (this.tempProjectDir && this.projectDir != null && !this.projectDir.isEmpty()) {
            FileUtils.deleteQuietly(new File(this.projectDir));
            PENDING_TEMP_DIRS.remove(this.projectDir);
            this.tempProjectDir = false;
        }
    }

    private void persistDir() {
        long startTime = System.currentTimeMillis();
        Set<String> dirs = new HashSet<>();
        final String rootDir = System.getProperty("java.io.tmpdir")
                + File.separator + RandomStringUtils.randomAlphanumeric(16);
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
        PENDING_TEMP_DIRS.add(rootDir);   // shutdown-hook backstop if close() never runs
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
