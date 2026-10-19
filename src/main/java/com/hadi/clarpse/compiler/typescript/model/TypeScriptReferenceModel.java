package com.hadi.clarpse.compiler.typescript.model;

/**
 * Raw TypeScript reference entry emitted by the daemon.
 */
public class TypeScriptReferenceModel {

    public String kind;
    public boolean external;
    public String displayName;

    /**
     * Set by a one-level analysis on an external reference whose type was lost, rather than found
     * outside the repository: {@code any}, alone or nested, or a symbol with no declaration.
     */
    public boolean unresolved;
    public TypeScriptTargetModel target;

    /**
     * The arguments written on an applied decorator, in written order: empty for a decorator written
     * without any, and {@code null} for a reference that is not a decorator or whose arguments were
     * not read.
     */
    public java.util.List<TypeScriptArgumentModel> arguments;
}
