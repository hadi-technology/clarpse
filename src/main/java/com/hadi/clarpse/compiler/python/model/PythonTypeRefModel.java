package com.hadi.clarpse.compiler.python.model;

/**
 * Raw Python type-reference payload returned by the daemon.
 */
public class PythonTypeRefModel {

    public String raw;
    public String targetUniqueName;
    public String externalLabel;

    /**
     * The remaining arms of a union annotation, one reference each.
     *
     * <p>{@code Foo | Bar} names two types, not one, and a single reference can only carry the
     * first. Splitting is the Python front end's to do: the shared name normaliser sees one string
     * and would have to return a list to express this, for one language's syntax.
     */
    public java.util.List<PythonTypeRefModel> alternates = new java.util.ArrayList<>();
}
