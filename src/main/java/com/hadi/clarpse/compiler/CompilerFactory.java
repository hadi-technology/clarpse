package com.hadi.clarpse.compiler;

import com.hadi.clarpse.compiler.typescript.ClarpseTypeScriptCompiler;
import com.hadi.clarpse.compiler.python.ClarpsePythonCompiler;
import com.hadi.clarpse.compiler.csharp.ClarpseCSharpCompiler;

/**
 * Factory to retrieve appropriate parsing tool for our projects.
 */
public class CompilerFactory {

    public static ClarpseCompiler getParsingTool(final Lang language) throws CompileException {
        switch (language) {

            case JAVA:
            return new ClarpseJavaCompiler();
            case CSHARP:
            return new ClarpseCSharpCompiler();
            case TYPESCRIPT:
            return new ClarpseTypeScriptCompiler();
            case PYTHON:
            return new ClarpsePythonCompiler();
        default:
            throw new CompileException("Could not find parsing tool for: " + language.value());
        }

    }
}
