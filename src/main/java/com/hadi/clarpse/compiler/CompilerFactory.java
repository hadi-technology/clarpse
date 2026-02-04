package com.hadi.clarpse.compiler;

/**
 * Factory to retrieve appropriate parsing tool for our projects.
 */
public class CompilerFactory {

    public static ClarpseCompiler getParsingTool(final Lang language) throws CompileException {
        switch (language) {

            case JAVA:
            return new ClarpseJavaCompiler();
        default:
            throw new CompileException("Could not find parsing tool for: " + language.value());
        }

    }
}
