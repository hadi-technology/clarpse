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

    /**
     * Modules and symbols this file imports, already resolved by the daemon — relative forms
     * expanded and internal-ness checked against the module index. The daemon computed these to
     * drive type resolution and then omitted them from the payload, which is why
     * {@code imports()} was empty on every Python component. See issue #156.
     */
    public List<String> imports = new ArrayList<>();

    public List<String> warnings = new ArrayList<>();
}
