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
     * outside the repository: {@code any}, alone or nested, or a symbol with no declaration. Such a
     * reference is not loaded, and carries no resolution.
     */
    public boolean lost;
    public TypeScriptTargetModel target;

    /** The member a call uses, on the type this reference names; {@code null} for a type reference. */
    public String member;

    /** How the member is used: {@code call}. */
    public String memberKind;

    /** The npm package or Node built-in module (always {@code node:name}) of an external owner. */
    public String externalUnit;

    /**
     * How the checker settled an external reference: {@code "exact"} when it resolved to one
     * declaration, {@code "unresolved"} when the name is only kept as written. {@code null} where
     * the daemon does not say.
     */
    public String resolution;

    /**
     * The arguments written on an applied decorator, in written order: empty for a decorator written
     * without any, and {@code null} for a reference that is not a decorator or whose arguments were
     * not read.
     */
    public java.util.List<TypeScriptArgumentModel> arguments;
}
