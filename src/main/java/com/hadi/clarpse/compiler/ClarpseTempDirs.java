package com.hadi.clarpse.compiler;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates, tracks and deletes every temporary directory Clarpse makes.
 *
 * <p>Each is created under {@code java.io.tmpdir} with a name starting {@value #PREFIX}, and stays
 * registered as open in this JVM until {@link #delete(Path)} removes it. A JVM shutdown hook deletes
 * whatever is still open, and {@link #deleteStale(Duration)} deletes what a process that could not
 * run its hook, killed or out of memory, left behind.
 */
final class ClarpseTempDirs {

    /** The start of the name of every temporary directory Clarpse creates. */
    static final String PREFIX = "clarpse-";

    private static final Logger LOGGER = LogManager.getLogger(ClarpseTempDirs.class);

    private static final Set<Path> OPEN = ConcurrentHashMap.newKeySet();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (final Path dir : OPEN) {
                FileUtils.deleteQuietly(dir.toFile());
            }
        }, "clarpse-tempdir-cleanup"));
    }

    private ClarpseTempDirs() {
    }

    /**
     * Creates and registers a temporary directory.
     *
     * @param kind What the directory holds; the name is {@value #PREFIX} followed by it, a dash and
     *             a random suffix.
     * @return The directory, absolute and normalised.
     */
    static Path create(final String kind) throws IOException {
        final Path dir = Files.createTempDirectory(PREFIX + kind + "-").toAbsolutePath().normalize();
        OPEN.add(dir);
        return dir;
    }

    /**
     * Deletes a directory this class created and deregisters it. Unknown paths are ignored.
     *
     * @param dir The directory.
     */
    static void delete(final Path dir) {
        if (dir == null) {
            return;
        }
        final Path normalized = dir.toAbsolutePath().normalize();
        if (OPEN.remove(normalized)) {
            FileUtils.deleteQuietly(normalized.toFile());
        }
    }

    /**
     * Whether a directory is registered as open in this JVM.
     *
     * @param dir The directory.
     * @return {@code true} while it has not been deleted.
     */
    static boolean isOpen(final Path dir) {
        return dir != null && OPEN.contains(dir.toAbsolutePath().normalize());
    }

    /**
     * Deletes the directories under {@code java.io.tmpdir} whose names start with {@value #PREFIX},
     * that were last modified longer ago than {@code olderThan}, and that are not open in this JVM.
     *
     * @param olderThan The age past which a directory is stale; must not be negative.
     * @return The directories deleted.
     */
    static List<Path> deleteStale(final Duration olderThan) {
        if (olderThan == null || olderThan.isNegative()) {
            throw new IllegalArgumentException("The age must not be negative.");
        }
        final Instant cutoff = Instant.now().minus(olderThan);
        final Path tmp = Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        final List<Path> deleted = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(tmp, PREFIX + "*")) {
            for (final Path entry : entries) {
                final Path dir = entry.toAbsolutePath().normalize();
                if (!Files.isDirectory(dir) || isOpen(dir)) {
                    continue;
                }
                if (Files.getLastModifiedTime(dir).toInstant().isBefore(cutoff)) {
                    FileUtils.deleteQuietly(dir.toFile());
                    if (!Files.exists(dir)) {
                        deleted.add(dir);
                    }
                }
            }
        } catch (final IOException e) {
            LOGGER.warn("Could not sweep stale temporary directories under {}.", tmp, e);
        }
        if (!deleted.isEmpty()) {
            LOGGER.info("Deleted {} stale temporary director(ies) older than {}: {}", deleted.size(), olderThan,
                    deleted);
        }
        return deleted;
    }
}
