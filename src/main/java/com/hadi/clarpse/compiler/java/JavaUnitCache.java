package com.hadi.clarpse.compiler.java;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseStart;
import com.github.javaparser.Providers;
import com.github.javaparser.ast.CompilationUnit;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The compilation units the type solvers of one one-level compile read to resolve names, shared by
 * every parser thread and by both of its phases, so a file is parsed for name resolution at most
 * once per compile.
 *
 * <p>Units are parsed without a symbol resolver and are never modified once cached: they are read
 * concurrently by the solvers of several threads, so nothing may attach to them. A file the listener
 * walks is not taken from this cache; the walking thread parses it privately with its own resolver.
 * A file is parsed here only while the {@link JavaLoadTracker} admits it, so the cap bounds what the
 * cache holds. {@link #clear()} drops every unit at the end of the compile.
 */
public final class JavaUnitCache {

    private final Map<String, String> contentByPath;
    private final JavaLoadTracker tracker;
    private final Map<String, Optional<CompilationUnit>> units = new ConcurrentHashMap<>();

    /**
     * Creates an empty cache.
     *
     * @param contentByPath The source text of every repository Java file, by path.
     * @param tracker       Admits and records the files solvers load.
     */
    public JavaUnitCache(final Map<String, String> contentByPath, final JavaLoadTracker tracker) {
        this.contentByPath = contentByPath;
        this.tracker = tracker;
    }

    /**
     * The unit of a file a solver reads to resolve a name, parsing it on first use when the tracker
     * admits it.
     *
     * @param path The file's path.
     * @return The unit, or empty when the file is unknown, unparseable or past the cap.
     */
    public Optional<CompilationUnit> resolved(final String path) {
        final Optional<CompilationUnit> cached = units.get(path);
        if (cached != null) {
            return cached;
        }
        if (!contentByPath.containsKey(path) || !tracker.admit(path)) {
            return Optional.empty();
        }
        return units.computeIfAbsent(path, this::parse);
    }

    private Optional<CompilationUnit> parse(final String path) {
        final String content = contentByPath.get(path);
        if (content == null) {
            return Optional.empty();
        }
        final JavaParser parser = new JavaParser(JavaParserFactory.unitParserConfig());
        return parser.parse(ParseStart.COMPILATION_UNIT, Providers.provider(content)).getResult();
    }

    /** Drops every unit. */
    public void clear() {
        units.clear();
    }
}
