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
        content = fileContent;
        if (path.startsWith(".") || !path.startsWith("/")) {
            throw new IllegalArgumentException(("Project files must use an absolute path!"));
        } else {
            this.path = path;
        }
        LOGGER.debug("Created new file with path " + this.path + ".");
    }

    public void path(final String path) {
        this.path = path;
    }

    public void content(final String content) {
        this.content = content;
    }

    public ProjectFile(final java.io.File file) throws IOException {
        try (Scanner scanner = new Scanner(file, StandardCharsets.UTF_8)) {
            content = scanner.useDelimiter("\\A").next();
        }
        path = file.getName();
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
}
