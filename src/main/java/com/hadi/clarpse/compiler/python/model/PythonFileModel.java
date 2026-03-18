package com.hadi.clarpse.compiler.python.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Raw Python file payload returned by the daemon.
 */
public class PythonFileModel {

    public String filePath;
    public String packageName;
    public String moduleName;
    public List<PythonClassModel> classes = new ArrayList<>();
    public List<PythonMethodModel> functions = new ArrayList<>();
    public List<PythonFieldModel> moduleFields = new ArrayList<>();
    public List<String> warnings = new ArrayList<>();
}
