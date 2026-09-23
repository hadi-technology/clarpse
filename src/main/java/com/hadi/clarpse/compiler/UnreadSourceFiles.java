package com.hadi.clarpse.compiler;

import org.apache.commons.io.FilenameUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The files a {@link ProjectFiles} was given whose extension names a programming language Clarpse
 * has no parser for, so that a caller can tell a project that holds no Kotlin from a project whose
 * Kotlin this library dropped.
 *
 * <p>Only paths are kept. These files are never parsed, are absent from
 * {@link ProjectFiles#files()} and {@link ProjectFiles#files(Lang)}, do not count towards
 * {@link ProjectFiles#size()}, and are not written to {@link ProjectFiles#projectDir()}. Recording
 * a path reads no content: an archive entry's bytes are materialised only if a
 * {@link DiscardedEntryObserver} asks for them, and a directory's file is not opened at all.
 *
 * <p>Paths read the way {@link ProjectFile#path()} reads for the files that were kept: normalised,
 * forward slashes, a leading slash.
 *
 * <p>The record can be partial, and says so through {@link #complete()}. A query against a partial
 * record answers {@link Answer#UNKNOWN} rather than {@code NO}, because a path that is not in a
 * partial record may still be in the project.
 */
public final class UnreadSourceFiles {

    /**
     * How a query about a file is answered. {@link #UNKNOWN} is the answer whenever the record is
     * too partial for the absence of a path to mean the absence of the file.
     */
    public enum Answer {
        YES, NO, UNKNOWN
    }

    /**
     * The extensions this record covers, and the language each one names. The set is the
     * general-purpose programming languages a repository is written in but this library does not
     * read, rather than every extension it does not parse: sweeping in images, lock files, data and
     * vendored blobs would make a query about a source file meaningless. An extension is mapped to
     * the language it most often names; a few (notably {@code .h} and {@code .m}) are shared by
     * several languages, so the language a path reports is the likeliest one and not a finding.
     */
    private static final Map<String, String> EXTN_TO_LANGUAGE = unreadLanguagesByExtn();

    private final Set<String> paths;
    private final boolean complete;

    UnreadSourceFiles(final Collection<String> paths, final boolean complete) {
        this.paths = Collections.unmodifiableSet(new LinkedHashSet<>(paths));
        this.complete = complete;
    }

    private static Map<String, String> unreadLanguagesByExtn() {
        final Map<String, String> extns = new LinkedHashMap<>();
        putAll(extns, "kotlin", "kt", "kts");
        putAll(extns, "scala", "scala", "sc");
        putAll(extns, "go", "go");
        putAll(extns, "rust", "rs");
        putAll(extns, "swift", "swift");
        putAll(extns, "ruby", "rb");
        putAll(extns, "php", "php");
        putAll(extns, "c", "c", "h");
        putAll(extns, "cpp", "cpp", "cc", "cxx", "hpp", "hh", "hxx");
        putAll(extns, "objective-c", "m", "mm");
        putAll(extns, "javascript", "js", "jsx", "mjs", "cjs");
        putAll(extns, "groovy", "groovy");
        putAll(extns, "clojure", "clj", "cljs", "cljc");
        putAll(extns, "fsharp", "fs", "fsi", "fsx");
        putAll(extns, "visualbasic", "vb");
        return Collections.unmodifiableMap(extns);
    }

    private static void putAll(final Map<String, String> extns, final String language,
                               final String... fileExtns) {
        for (final String extn : fileExtns) {
            extns.put(extn, language);
        }
    }

    /**
     * Whether this path names a source file in a language this library does not read. A path whose
     * extension {@link Lang} covers is not one of these, so a file this library parses is never
     * reported as unread.
     *
     * @param path Any path or file name; neither normalisation nor existence is required.
     * @return Whether a file at this path would belong to this record.
     */
    static boolean isUnreadSourceFile(final String path) {
        return languageOf(path) != null;
    }

    /**
     * The language a path's extension names, among the languages this library does not read.
     *
     * @param path Any path or file name.
     * @return The language name, or null when the extension is one this library reads or one this
     *         record does not cover.
     */
    static String languageOf(final String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        final String extn = FilenameUtils.getExtension(path).toLowerCase(Locale.ROOT);
        if (extn.isEmpty() || Lang.langFromExtn(extn) != null) {
            return null;
        }
        return EXTN_TO_LANGUAGE.get(extn);
    }

    /**
     * The paths of the files in a language this library does not read, in the order they were seen.
     *
     * @return The recorded paths, which is every such path when {@link #complete()} holds.
     */
    public Set<String> paths() {
        return this.paths;
    }

    /**
     * Whether every file in a language this library does not read is in {@link #paths()}. It is
     * false once a path could not be recorded: the cap on how many paths one project may hold was
     * reached, an archive entry's path could not be made safe to keep, or
     * {@link ProjectFiles#shiftSubDirsLeft()} left a path with no subdirectory to shift into.
     *
     * @return Whether the absence of a path from this record means the absence of the file.
     */
    public boolean complete() {
        return this.complete;
    }

    /**
     * The languages the recorded paths are written in.
     *
     * @return The distinct language names behind {@link #paths()}, which is empty when the project
     *         holds no file in a language this library does not read.
     */
    public Set<String> languages() {
        final Set<String> languages = new LinkedHashSet<>();
        for (final String path : this.paths) {
            final String language = languageOf(path);
            if (language != null) {
                languages.add(language);
            }
        }
        return Collections.unmodifiableSet(languages);
    }

    /**
     * Whether the project holds a file at this path in a language this library does not read.
     *
     * @param path The path to look for, in any form {@link ProjectFile} accepts.
     * @return YES when the path is recorded, NO when it is not and the record is complete or the
     *         path names a language this library reads, UNKNOWN otherwise.
     */
    public Answer holdsPath(final String path) {
        if (!isUnreadSourceFile(path)) {
            return Answer.NO;
        }
        final String normalized = normalizedOrNull(path);
        if (normalized == null) {
            return Answer.NO;
        }
        return answer(this.paths.contains(normalized));
    }

    /**
     * Whether the project holds a file with this name, in any directory, in a language this library
     * does not read.
     *
     * @param fileName The file name to look for, extension included.
     * @return YES when a recorded path ends in this name, NO when none does and the record is
     *         complete or the name is in a language this library reads, UNKNOWN otherwise.
     */
    public Answer holdsFileNamed(final String fileName) {
        if (!isUnreadSourceFile(fileName)) {
            return Answer.NO;
        }
        final String name = fileName.trim().replace('\\', '/');
        boolean found = false;
        for (final String path : this.paths) {
            if (path.equals(name) || path.endsWith("/" + name)) {
                found = true;
                break;
            }
        }
        return answer(found);
    }

    @Override
    public String toString() {
        return "UnreadSourceFiles[paths=" + this.paths.size() + ", complete=" + this.complete + "]";
    }

    private Answer answer(final boolean found) {
        if (found) {
            return Answer.YES;
        }
        if (this.complete) {
            return Answer.NO;
        }
        return Answer.UNKNOWN;
    }

    private static String normalizedOrNull(final String path) {
        try {
            return ProjectFile.normalizePath(path);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }
}
