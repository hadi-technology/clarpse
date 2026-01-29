package com.hadi.clarpse.compiler;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Represents source files to be parsed.
 */
public class ProjectFiles {

    private static final Logger LOGGER = LogManager.getLogger(ProjectFiles.class);
    private final Map<Lang, List<ProjectFile>> langToFilesMap = new HashMap<>();
    private int size = 0;
    private String projectDir;
    private boolean tempProjectDir = false;

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
        this.langToFilesMap.forEach((lang, files) -> this.langToFilesMap.put(lang, files.stream().map(file -> {
            if (StringUtils.countMatches(file.path(), File.separator) > 1) {
                return new ProjectFile(file.path().substring(
                        StringUtils.ordinalIndexOf(file.path(), File.separator, 2)
                ), file.content());
            } else {
                throw new IllegalArgumentException("Cannot shift file: " + file.path() + ".");
            }
        }).collect(Collectors.toList())));
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
            if (nextFile.isFile() && Lang.langFromExtn(FilenameUtils.getExtension(nextFile.getName())) != null) {
                this.insertFile(new ProjectFile(
                        nextFile.getPath(),
                        FileUtils.readFileToString(nextFile, StandardCharsets.US_ASCII))
                );
            }
        }
        LOGGER.info("Read " + this.langToFilesMap.size() + " files.");
    }

    private void extractProjectFilesFromStream(final InputStream is)
            throws Exception {
        LOGGER.info("Extracting source files from input stream..");
        int filesCounter = 0;
        try (ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory() && (Lang.langFromExtn(
                        FilenameUtils.getExtension(entry.getName())) != null)) {
                    ProjectFile newFile = new ProjectFile(
                            File.separator + entry.getName().replace(" ", "_"),
                            new String(IOUtils.toByteArray(zis), StandardCharsets.UTF_8));
                    LOGGER.debug("Extracted project file " + newFile + ".");
                    this.insertFile(newFile);
                    filesCounter += 1;
                }
                zis.closeEntry();
                entry = zis.getNextEntry();
            }
        } catch (final Exception e) {
            throw new Exception("Error while  reading  files from zip!", e);
        }
        LOGGER.info("Extracted " + filesCounter + " files.");
    }

    private boolean anyMatchExtensions(String s, String[] extn) {
        return Arrays.stream(extn).anyMatch(s::endsWith);
    }

    public final void insertFile(final ProjectFile file) {
        Lang fileLang = Lang.langFromExtn(file.extension());
        if (fileLang != null) {
            this.insertFile(file, fileLang);
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

    public final Collection<ProjectFile> files(Lang language) {
        return this.langToFilesMap.getOrDefault(language, new ArrayList<>());
    }

    public final Collection<ProjectFile> files() {
        Set<ProjectFile> allFiles = new HashSet<>();
        this.langToFilesMap.forEach((lang, files) -> allFiles.addAll(files));
        return allFiles;
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

    private void persistDir() {
        long startTime = System.currentTimeMillis();
        Set<String> dirs = new HashSet<>();
        final String rootDir = System.getProperty("java.io.tmpdir")
                + File.separator + RandomStringUtils.randomAlphanumeric(16);
        LOGGER.info("Persisting files to " + rootDir);
        dirs.add(rootDir);
        this.langToFilesMap.forEach((lang, projectFiles) -> projectFiles.forEach(projectFile -> {
            final String filePath = rootDir + File.separator + projectFile.path();
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
        long elapsedTime = System.currentTimeMillis() - startTime;
        LOGGER.info(this.size() + " files were persisted in " + elapsedTime + " ms");
        this.projectDir = rootDir;
        this.tempProjectDir = true;
    }

    public void filter(Collection<String> filterFilePaths) {
        this.langToFilesMap.forEach((lang, files) -> new ArrayList<>(files).forEach(file -> {
            if (!filterFilePaths.contains(file.path())) {
                this.langToFilesMap.get(lang).remove(file);
                size -= 1;
            }
        }));
    }

    public Set<ProjectFile> matchingFilesByName(String matchName) {
        Set<ProjectFile> result = new HashSet<>();
        this.langToFilesMap.forEach((lang, files) -> result.addAll(files.stream().filter(
                file -> file.name().equals(matchName)).collect(Collectors.toList())));
        return result;
    }
}
