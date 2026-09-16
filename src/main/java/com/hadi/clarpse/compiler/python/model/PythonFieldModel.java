package com.hadi.clarpse.compiler.python.model;

/**
 * Raw Python field/module-variable payload returned by the daemon.
 */
public class PythonFieldModel {

    public String name;
    /** True for a class-level assignment in an enumeration, which names a member of it. */
    public boolean enumConstant;
    public String rawType;
    public int implementationHash;
    public String targetUniqueName;
    public String externalLabel;

    /** The remaining arms of a union annotation. See {@link PythonTypeRefModel#alternates}. */
    public java.util.List<PythonTypeRefModel> alternates = new java.util.ArrayList<>();
}
