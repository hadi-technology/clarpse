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
 * <p>Entries extraction skips because of a size limit are neither kept nor observed; they are
 * reported through {@link #skipped(String, SkipReason)}, so a caller can tell an extraction that
 * read the whole archive from one that stopped short. Skipped entries are reported whatever their
 * extension, and an observer that does not override the method is told nothing.
 *
 * <p>An exception thrown by any of these methods propagates to the caller of the extraction, with the
 * archive closed and the partly built {@link ProjectFiles} discarded.
 */
@FunctionalInterface
public interface DiscardedEntryObserver {

    /** Why extraction skipped an entry. */
    enum SkipReason {
        /** Reading the entry took the archive past its total uncompressed size limit. */
        TOTAL_SIZE_LIMIT,
        /** The entry alone is larger than the per-entry uncompressed size limit. */
        ENTRY_TOO_LARGE
    }

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

    /**
     * Reports an entry extraction skipped because of a size limit. Once the total limit is reached
     * every later entry is reported with {@link SkipReason#TOTAL_SIZE_LIMIT}. The entry's bytes are
     * not handed over. The default does nothing.
     *
     * @param path The entry's path relative to the archive root, normalised, with forward slashes.
     * @param reason Why the entry was skipped.
     */
    default void skipped(String path, SkipReason reason) {
    }
}
