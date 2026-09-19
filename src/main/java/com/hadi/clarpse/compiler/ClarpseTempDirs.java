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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creates, tracks and deletes every temporary directory Clarpse makes.
 *
 * <p>Each is created under {@code java.io.tmpdir} as
 * {@code clarpse-<kind>-<pid>-<start>-<random>}, where {@code pid} and {@code start} identify the
 * process that owns it: its process id and its start time in epoch milliseconds. It stays registered
 * as open in this JVM until {@link #delete(Path)} removes it, and a JVM shutdown hook deletes
 * whatever is still open.
 *
 * <p>{@link #deleteStale(Duration)} deletes what a process that could not run its hook, killed or
 * out of memory, left behind: a directory whose owner is no longer running. Since a process id is
 * reused, and in a container the JVM is typically the same id after every restart, the owner counts
 * as running only when a live process has both its id and its start time.
 */
final class ClarpseTempDirs {

    /** The start of the name of every temporary directory Clarpse creates. */
    static final String PREFIX = "clarpse-";

    private static final Logger LOGGER = LogManager.getLogger(ClarpseTempDirs.class);

    private static final Set<Path> OPEN = ConcurrentHashMap.newKeySet();

    /** The owner fields of a name: the process id and start time before the random suffix. */
    private static final Pattern OWNER = Pattern.compile("^" + PREFIX + ".+-(\\d+)-(-?\\d+)-[^-]+$");

    /** This process's start time in epoch milliseconds, or -1 when the platform does not report it. */
    private static final long START = startMillis(ProcessHandle.current());

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
     * @param kind What the directory holds; the name is {@value #PREFIX} followed by it, this
     *             process's id and start time, and a random suffix.
     * @return The directory, absolute and normalised.
     */
    static Path create(final String kind) throws IOException {
        final Path dir = Files.createTempDirectory(ownedPrefix(kind)).toAbsolutePath().normalize();
        OPEN.add(dir);
        return dir;
    }

    /**
     * The name a directory of the given kind owned by this process starts with.
     *
     * @param kind What the directory holds.
     * @return {@code clarpse-<kind>-<pid>-<start>-}.
     */
    static String ownedPrefix(final String kind) {
        return PREFIX + kind + "-" + ProcessHandle.current().pid() + "-" + START + "-";
    }

    private static long startMillis(final ProcessHandle process) {
        return process.info().startInstant().map(Instant::toEpochMilli).orElse(-1L);
    }

    /**
     * Whether the process that owns a directory, by its name, is still running: a live process has
     * its id, and either the same start time or a start time the platform does not report.
     *
     * @param name A directory's name.
     * @return The answer, or empty when the name carries no owner.
     */
    static Optional<Boolean> ownerRunning(final String name) {
        final Matcher matcher = OWNER.matcher(name);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        final long pid;
        final long start;
        try {
            pid = Long.parseLong(matcher.group(1));
            start = Long.parseLong(matcher.group(2));
        } catch (final NumberFormatException e) {
            return Optional.empty();
        }
        final Optional<ProcessHandle> process = ProcessHandle.of(pid);
        if (process.isEmpty() || !process.get().isAlive()) {
            return Optional.of(false);
        }
        final long liveStart = startMillis(process.get());
        return Optional.of(start < 0 || liveStart < 0 || liveStart == start);
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
     * that are not open in this JVM, that were last modified longer ago than {@code olderThan}, and
     * whose owning process, named in the directory's name, is no longer running. A directory whose
     * name carries no owner is judged by its age alone.
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
                if (!Files.isDirectory(dir) || isOpen(dir)
                        || ownerRunning(dir.getFileName().toString()).orElse(false)) {
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
