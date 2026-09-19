package com.hadi.clarpse.compiler.java;

import com.github.javaparser.JavaParser;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;

/**
 * Thread-local context holding parser and type solver for Java parsing.
 */
public class ParserContext {
    private final CombinedTypeSolver typeSolver;
    private final JavaParser parser;

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
}
