package com.hadi.clarpse.compiler;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class ProjectFile {

    private static final Logger LOGGER = LogManager.getLogger(ProjectFile.class);
    private String content;
    private String path;

    public ProjectFile(final String path, final String fileContent) {
        this.content = fileContent == null ? "" : fileContent;
        this.path = normalizePath(path);
        LOGGER.debug("Created new file with path " + this.path + ".");
    }

    public void path(final String path) {
        this.path = normalizePath(path);
    }

    public void content(final String content) {
        this.content = content;
    }

    public ProjectFile(final java.io.File file) throws IOException {
        try (Scanner scanner = new Scanner(file, StandardCharsets.UTF_8)) {
            content = scanner.useDelimiter("\\A").next();
        }
        path = normalizePath(file.getAbsolutePath());
    }

    public String dir() {
        Path currPath = Paths.get(this.path());
        Path currPathParent = currPath.getParent();
        if (currPathParent != null) {
            return currPathParent.toString();
        } else {
            return "/";
        }
    }

    public String shortName() {
        String shortName = path;
        if (shortName.contains("/")) {
            shortName = shortName.substring(shortName.lastIndexOf("/") + 1,
                                            shortName.lastIndexOf('.'));
        }
        return shortName;
    }

    public String name() {
        String name = path;
        if (name.contains("/")) {
            name = name.substring(name.lastIndexOf("/") + 1);
        }
        return name;
    }
    public final String content() {
        return content;
    }

    public final InputStream stream() {
        return new ByteArrayInputStream(content().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public final boolean equals(final Object obj) {
        if (obj == null || (getClass() != obj.getClass())) {
            return false;
        }
        final ProjectFile file = (ProjectFile) obj;
        return content().equals(file.content())
            && path().equals(file.path());
    }

    public String path() {
        return this.path;
    }

    @Override
    public int hashCode() {
        return content().hashCode() + path().hashCode();
    }

    public ProjectFile copy() {
        return new ProjectFile(path(), content());
    }

    @Override
    public String toString() {
        return this.path;
    }

    public String extension() {
        return FilenameUtils.getExtension(this.path);
    }

    private static String normalizePath(final String rawPath) {
        if (rawPath == null || rawPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Project files must include a non-empty path.");
        }
        String normalizedInput = rawPath.trim().replace('\\', '/');
        final boolean isAbsolute = CompilerSupport.isAbsolutePath(normalizedInput);
        String windowsDrivePrefix = "";
        if (normalizedInput.length() > 2
                && Character.isLetter(normalizedInput.charAt(0))
                && normalizedInput.charAt(1) == ':') {
            windowsDrivePrefix = normalizedInput.substring(0, 2);
            normalizedInput = normalizedInput.substring(2);
        }
        while (normalizedInput.startsWith("/")) {
            normalizedInput = normalizedInput.substring(1);
        }
        if (normalizedInput.isEmpty()) {
            throw new IllegalArgumentException("Project file path cannot be a root path.");
        }
        final Path normalizedPath = Paths.get(normalizedInput).normalize();
        for (Path part : normalizedPath) {
            if ("..".equals(part.toString())) {
                throw new IllegalArgumentException("Project file paths cannot include parent traversal.");
            }
        }
        final String result = normalizedPath.toString().replace('\\', '/');
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Project file path is invalid.");
        }
        if (isAbsolute) {
            if (!windowsDrivePrefix.isEmpty()) {
                return windowsDrivePrefix + "/" + result;
            }
            return "/" + result;
        }
        return "/" + result;
    }
}
