package com.hadi.clarpse.compiler.python.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Raw Python class payload, including members and nested classes.
 */
public class PythonClassModel {

    public String className;
    public String uniqueName;
    public String comment;
    public int implementationHash;
    public List<PythonTypeRefModel> bases = new ArrayList<>();
    public List<PythonMethodModel> methods = new ArrayList<>();
    public List<PythonFieldModel> fields = new ArrayList<>();
    public List<PythonClassModel> classes = new ArrayList<>();
}
