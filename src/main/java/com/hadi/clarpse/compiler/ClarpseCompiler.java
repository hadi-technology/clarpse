package com.hadi.clarpse.compiler;

/**
 * Compiles source code into an object-oriented representation of the original code.
 */
public interface ClarpseCompiler {

    /**
     * Compiles source code.
     *
     * @param projectFiles Files to compile.
     * @return See {@link CompileResult}
     */
    CompileResult compile(ProjectFiles projectFiles) throws CompileException;
}
