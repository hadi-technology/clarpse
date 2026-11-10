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
    private JavaImportResolver importResolver;

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

    /**
     * Resolves an import directive to the repository files it names.
     *
     * @return The resolver, or null when the files imported are not recorded.
     */
    public JavaImportResolver importResolver() {
        return importResolver;
    }

    /**
     * This context, recording the repository files each parsed file's imports resolve to.
     *
     * @param resolver The resolver over the project's files; null to record none.
     * @return This context.
     */
    public ParserContext withImportResolver(final JavaImportResolver resolver) {
        this.importResolver = resolver;
        return this;
    }
}
