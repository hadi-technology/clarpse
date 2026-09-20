package com.hadi.clarpse.compiler;

import java.time.Instant;

/**
 * Receives the archive entries {@link ProjectFiles} reads and does not keep, so a caller that wants
 * a repository's non-source files gets them from the pass that reads the archive for parsing.
 *
 * <p>An entry is discarded when its extension maps to no {@link Lang} and it is not one of the
 * configuration files a compiler needs. An observer never sees a kept entry, never sees an entry
 * whose path the archive could use to escape its root, and decides nothing about what is parsed:
 * the {@link ProjectFiles} an observed extraction produces is the one it produces without an
 * observer.
 *
 * <p>Observed entries are extraction's entries. They count against the entry-count and
 * uncompressed-size limits like any other, so an observer cannot be used to read more of an archive
 * than extraction would read by itself.
 *
 * <p>{@link #observesPath(String)} is asked before an entry's bytes are read. An observer that
 * declines a path never has that entry's bytes materialised; extraction reads past them and counts
 * them against the limits. An observer that accepts one owns the array it is handed, including
 * whatever memory it retains by keeping it.
 *
 * <p>An exception thrown by either method propagates to the caller of the extraction, with the
 * archive closed and the partly built {@link ProjectFiles} discarded.
 */
@FunctionalInterface
public interface DiscardedEntryObserver {

    /**
     * Whether the entry at this path is worth reading. Extraction asks this before it reads the
     * entry's bytes, so a path or extension test here is what keeps a few documents out of a large
     * archive cheap. Every discarded entry is observed by default.
     *
     * @param path The entry's path relative to the archive root, normalised, with forward slashes.
     * @return Whether to read this entry's bytes and hand them to
     *         {@link #observe(String, byte[], Instant)}.
     */
    default boolean observesPath(String path) {
        return true;
    }

    /**
     * Hands over an entry extraction read and did not keep.
     *
     * @param path The entry's path relative to the archive root, normalised, with forward slashes.
     * @param content The entry's uncompressed bytes. The array belongs to the observer; extraction
     *                keeps no reference to it.
     * @param lastModified The entry's last-modified time, or null when the archive records none.
     */
    void observe(String path, byte[] content, Instant lastModified);
}
