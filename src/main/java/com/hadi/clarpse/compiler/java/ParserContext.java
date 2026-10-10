package com.hadi.clarpse.compiler.java;

import com.github.javaparser.JavaParser;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;

import java.util.function.Predicate;

/**
 * Thread-local context holding parser and type solver for Java parsing.
 */
public class ParserContext {
    private final CombinedTypeSolver typeSolver;
    private final JavaParser parser;
    private Predicate<String> otherLanguageTypes = name -> false;

    public ParserContext(final String persistDir) {
        this(persistDir, java.util.List.of());
    }

    /**
     * A context resolving through the given solver. It parses every file it walks itself, with a
     * parser whose symbol resolver is this context's own, so no unit it walks is shared with
     * another thread.
     *
     * @param typeSolver The solver to resolve types with.
     */
    public ParserContext(final CombinedTypeSolver typeSolver) {
        this.typeSolver = typeSolver;
        this.parser = new JavaParser(JavaParserFactory.setupParserConfig(this.typeSolver));
    }

    public ParserContext(final String persistDir, final java.util.Collection<String> sourceRoots) {
        this.typeSolver = JavaParserFactory.setupTypeSolver(persistDir, sourceRoots);
        this.parser = new JavaParser(JavaParserFactory.setupParserConfig(this.typeSolver));
    }

    public CombinedTypeSolver typeSolver() {
        return typeSolver;
    }

    public JavaParser parser() {
        return parser;
    }

    /**
     * The types the project's files in other JVM languages declare, which the type solver cannot
     * see.
     *
     * @return Whether a fully qualified name is such a type; never null.
     */
    public Predicate<String> otherLanguageTypes() {
        return otherLanguageTypes;
    }

    /**
     * This context, resolving on-demand imports against the given other-language types too.
     *
     * @param types Whether a fully qualified name is a type another JVM language declares; null for none.
     * @return This context.
     */
    public ParserContext withOtherLanguageTypes(final Predicate<String> types) {
        if (types == null) {
            this.otherLanguageTypes = name -> false;
        } else {
            this.otherLanguageTypes = types;
        }
        return this;
    }
}
