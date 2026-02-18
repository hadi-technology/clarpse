package com.hadi.clarpse.compiler.python.model;

import java.util.ArrayList;
import java.util.List;

public class PythonClassModel {

    public String className;
    public String uniqueName;
    public List<PythonTypeRefModel> bases = new ArrayList<>();
    public List<PythonMethodModel> methods = new ArrayList<>();
    public List<PythonFieldModel> fields = new ArrayList<>();
}
