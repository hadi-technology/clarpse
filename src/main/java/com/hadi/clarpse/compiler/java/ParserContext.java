package com.hadi.clarpse.compiler.java;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;

/**
 * Thread-local context holding parser and type solver for Java parsing.
 */
public class ParserContext {
    private final CombinedTypeSolver typeSolver;
    private final JavaParser parser;
    private final JavaUnitCache units;
    private final JavaSymbolSolver symbolSolver;

    public ParserContext(final String persistDir) {
        this(persistDir, java.util.List.of());
    }

    /**
     * A context resolving through the given solver and taking the units it walks from a shared
     * cache, which parses each file once for every thread of a compile.
     *
     * @param typeSolver The solver to resolve types with.
     * @param units      The compile's shared units.
     */
    public ParserContext(final CombinedTypeSolver typeSolver, final JavaUnitCache units) {
        this.typeSolver = typeSolver;
        this.parser = new JavaParser(JavaParserFactory.setupParserConfig(this.typeSolver));
        this.units = units;
        this.symbolSolver = new JavaSymbolSolver(typeSolver);
    }

    public ParserContext(final String persistDir, final java.util.Collection<String> sourceRoots) {
        this.typeSolver = JavaParserFactory.setupTypeSolver(persistDir, sourceRoots);
        this.parser = new JavaParser(JavaParserFactory.setupParserConfig(this.typeSolver));
        this.units = null;
        this.symbolSolver = null;
    }

    /**
     * The shared units this context walks, or {@code null} when it parses each file itself.
     *
     * @return The cache, or {@code null}.
     */
    public JavaUnitCache units() {
        return units;
    }

    /**
     * Makes a shared unit resolve through this context's solver before this thread walks it.
     *
     * @param unit A unit from {@link #units()}.
     */
    public void attach(final CompilationUnit unit) {
        if (symbolSolver != null) {
            unit.setData(Node.SYMBOL_RESOLVER_KEY, symbolSolver);
        }
    }

    public CombinedTypeSolver typeSolver() {
        return typeSolver;
    }

    public JavaParser parser() {
        return parser;
    }
}
