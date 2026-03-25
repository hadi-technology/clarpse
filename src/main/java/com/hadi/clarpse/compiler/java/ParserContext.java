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
        this.typeSolver = JavaParserFactory.setupTypeSolver(persistDir);
        this.parser = new JavaParser(JavaParserFactory.setupParserConfig(this.typeSolver));
    }

    public CombinedTypeSolver typeSolver() {
        return typeSolver;
    }

    public JavaParser parser() {
        return parser;
    }
}
