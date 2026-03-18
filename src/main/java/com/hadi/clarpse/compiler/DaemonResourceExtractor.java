package com.hadi.clarpse.compiler;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts daemon scripts and bundled runtime archives to temp directories.
 */
public final class DaemonResourceExtractor {

    private DaemonResourceExtractor() {
    }

    public static Extraction extract(final Class<?> ownerClass,
                                     final String tempDirPrefix,
                                     final String scriptResource,
                                     final String bundleZipResource) throws IOException {
        if (ownerClass == null) {
            throw new IllegalArgumentException("ownerClass cannot be null.");
        }
        final Path tempDir = Files.createTempDirectory(tempDirPrefix);
        final Path scriptPath = tempDir.resolve("daemon.js");
        copyResource(ownerClass, scriptResource, scriptPath);
        unzipResource(ownerClass, bundleZipResource, tempDir);
        scriptPath.toFile().deleteOnExit();
        tempDir.toFile().deleteOnExit();
        return new Extraction(tempDir, scriptPath);
    }

    private static void copyResource(final Class<?> ownerClass,
                                     final String resourcePath,
                                     final Path targetPath) throws IOException {
        try (InputStream inputStream = ownerClass.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Missing resource: " + resourcePath);
            }
            Files.copy(inputStream, targetPath);
        }
    }

    private static void unzipResource(final Class<?> ownerClass,
                                      final String resourcePath,
                                      final Path targetDir) throws IOException {
        try (InputStream inputStream = ownerClass.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Missing resource: " + resourcePath);
            }
            try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
                ZipEntry entry;
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    final Path outPath = targetDir.resolve(entry.getName()).normalize();
                    if (!outPath.startsWith(targetDir)) {
                        throw new IOException("Unsafe zip entry: " + entry.getName());
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(outPath);
                        zipInputStream.closeEntry();
                        continue;
                    }
                    if (outPath.getParent() != null) {
                        Files.createDirectories(outPath.getParent());
                    }
                    try (OutputStream outputStream =
                                 new BufferedOutputStream(Files.newOutputStream(outPath))) {
                        final byte[] buffer = new byte[8192];
                        int read;
                        while ((read = zipInputStream.read(buffer)) > 0) {
                            outputStream.write(buffer, 0, read);
                        }
                    }
                    zipInputStream.closeEntry();
                }
            }
        }
    }

    public static final class Extraction {
        private final Path tempDir;
        private final Path scriptPath;

        private Extraction(final Path tempDir, final Path scriptPath) {
            this.tempDir = tempDir;
            this.scriptPath = scriptPath;
        }

        public Path tempDir() {
            return tempDir;
        }

        public Path scriptPath() {
            return scriptPath;
        }
    }
}
