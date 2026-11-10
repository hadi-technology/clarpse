package com.hadi.clarpse.compiler.python.model;

/**
 * Raw Python field/module-variable payload returned by the daemon.
 */
public class PythonFieldModel {

    public String name;
    /** True for a class-level assignment in an enumeration, which names a member of it. */
    public boolean enumConstant;

    /** Whether the field is a module-level alias of a type or a union of types. */
    public boolean typeAlias;
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

    /** The module-level functions and values the field's initialiser names. */
    public java.util.List<PythonTypeRefModel> valueReferences = new java.util.ArrayList<>();
}
