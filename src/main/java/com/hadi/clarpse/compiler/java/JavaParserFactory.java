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
        return setupTypeSolver(persistDir, java.util.List.of());
    }

    /**
     * Builds a type solver rooted at every source root in the project, not only at the project
     * directory.
     *
     * <p>{@link JavaParserTypeSolver} resolves {@code a.b.C} by looking for {@code <root>/a/b/C.java}.
     * Rooting it at the project directory only works for a flat layout: in a Maven or Gradle project
     * the package {@code a} lives at {@code <root>/src/main/java/a}, so every lookup missed. The
     * effect is invisible for an explicitly imported type, whose name the import map already
     * carries, and total for anything that needs the solver — reproduced in three files, where a
     * class implementing an interface reached through {@code import a.*;} had no edge under
     * {@code src/main/java} and the correct edge under a flat layout, same sources either way.
     *
     * @param sourceRoots directories under which package paths begin, derived from the files
     *     themselves rather than guessed from convention, so unconventional layouts work too
     */
    public static CombinedTypeSolver setupTypeSolver(String persistDir,
                                                     java.util.Collection<String> sourceRoots) {
        final CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        for (final String sourceRoot : sourceRoots) {
            final java.io.File root = new java.io.File(sourceRoot);
            if (root.isDirectory()) {
                typeSolver.add(new JavaParserTypeSolver(root));
            }
        }
        // The project directory stays last: it is correct for a flat layout and harmless otherwise,
        // and dropping it would regress projects that have no source root at all.
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
