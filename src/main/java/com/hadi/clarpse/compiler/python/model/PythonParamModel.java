package com.hadi.clarpse.compiler.python.model;

/**
 * Raw Python parameter payload returned by the daemon.
 */
public class PythonParamModel {

    public String name;
    public String rawType;
    public int implementationHash;
    public String targetUniqueName;
    public String externalLabel;

    /** Whether {@link #externalLabel} is only the name as written, not one an import or the checker settled. */
    public boolean unresolved;

    /** Whether the annotation names a type variable, which records no reference. */
    public boolean typeVariable;

    /** The remaining arms of a union annotation. See {@link PythonTypeRefModel#alternates}. */
    public java.util.List<PythonTypeRefModel> alternates = new java.util.ArrayList<>();
}
