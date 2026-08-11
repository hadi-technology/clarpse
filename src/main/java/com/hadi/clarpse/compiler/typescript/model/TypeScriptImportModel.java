package com.hadi.clarpse.compiler.typescript.model;

/**
 * One name brought into a file by an import declaration.
 *
 * <p>{@code filePath} is set only when the TypeScript compiler resolved the specifier to a file
 * inside the repository; an unresolved or external specifier leaves it null and carries only
 * {@code module}. Resolution is the checker's, not string manipulation on the specifier, so an
 * import TypeScript itself cannot resolve is reported as external rather than guessed into a path.
 */
public class TypeScriptImportModel {

    public String module;
    public String filePath;
    public String symbolName;
}
