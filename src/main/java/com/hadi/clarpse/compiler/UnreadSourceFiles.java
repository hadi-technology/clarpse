package com.hadi.clarpse.compiler;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The source files a project holds whose language this library does not parse. A caller may ask
 * whether one is present, and is told when the record is too partial for an absence to mean
 * anything.
 */
public final class UnreadSourceFiles {

    /** How a question about a file is answered. */
    public enum Answer {
        YES, NO, UNKNOWN
    }

    private final Set<String> paths;
    private final boolean complete;

    UnreadSourceFiles(final Collection<String> paths, final boolean complete) {
        this.paths = Collections.unmodifiableSet(new LinkedHashSet<>(paths));
        this.complete = complete;
    }

    /**
     * The recorded paths, in the order they were seen.
     *
     * @return The paths, normalised the way this project's other file paths are.
     */
    public Set<String> paths() {
        return this.paths;
    }

    /**
     * Whether a path missing from this record means the file is missing from the project.
     *
     * @return False once a path could not be recorded, which makes every absence unknown.
     */
    public boolean complete() {
        return this.complete;
    }

    /**
     * The languages the recorded paths are written in.
     *
     * @return The distinct language names, empty when nothing was recorded.
     */
    public Set<String> languages() {
        final Set<String> languages = new LinkedHashSet<>();
        for (final String path : this.paths) {
            final UnreadLang lang = UnreadLang.langFromPath(path);
            if (lang != null) {
                languages.add(lang.value());
            }
        }
        return Collections.unmodifiableSet(languages);
    }

    /**
     * Whether the project holds a file at this path in a language this library does not parse.
     *
     * @param path The path to look for.
     * @return YES, NO, or UNKNOWN when this record is too partial to answer.
     */
    public Answer holdsPath(final String path) {
        if (UnreadLang.langFromPath(path) == null) {
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
     * does not parse.
     *
     * @param fileName The file name to look for, extension included.
     * @return YES, NO, or UNKNOWN when this record is too partial to answer.
     */
    public Answer holdsFileNamed(final String fileName) {
        if (UnreadLang.langFromPath(fileName) == null) {
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
