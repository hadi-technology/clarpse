package com.hadi.clarpse.compiler.java;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

/**
 * Factory for creating JavaParser components.
 */
public final class JavaParserFactory {

    private JavaParserFactory() {
        // Utility class
    }

    public static CombinedTypeSolver setupTypeSolver(String persistDir) {
        final CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(persistDir));
        return typeSolver;
    }

    public static ParserConfiguration setupParserConfig(CombinedTypeSolver typeSolver) {
        final ParserConfiguration parserConfiguration = new ParserConfiguration();
        parserConfiguration.setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
        parserConfiguration.setSymbolResolver(new JavaSymbolSolver(typeSolver));
        parserConfiguration.setIgnoreAnnotationsWhenAttributingComments(true);
        return parserConfiguration;
    }
}
