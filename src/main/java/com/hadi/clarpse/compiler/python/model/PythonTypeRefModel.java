package com.hadi.clarpse.compiler.python.model;

/**
 * Raw Python type-reference payload returned by the daemon.
 */
public class PythonTypeRefModel {

    public String raw;
    public String targetUniqueName;
    public String externalLabel;

    /** Whether {@link #externalLabel} is only the name as written, not one an import or the checker settled. */
    public boolean unresolved;

    /** The member a call uses on the type this names; {@code null} for an ordinary type reference. */
    public String member;

    /** How the member is used: {@code call}. */
    public String memberKind;

    /** The module of an external owner of the member. */
    public String externalUnit;

    /** Whether this is a call of a class, which constructs it; the reference to the class stays beside it. */
    public boolean construction;

    /** Whether the written name is a type variable, which names no type and so records no reference. */
    public boolean typeVariable;

    /**
     * The remaining arms of a union annotation, one reference each.
     *
     * <p>{@code Foo | Bar} names two types, not one, and a single reference can only carry the
     * first. Splitting is the Python front end's to do: the shared name normaliser sees one string
     * and would have to return a list to express this, for one language's syntax.
     */
    public java.util.List<PythonTypeRefModel> alternates = new java.util.ArrayList<>();
}
