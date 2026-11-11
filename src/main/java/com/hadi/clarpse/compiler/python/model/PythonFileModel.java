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

    /** The import directives the file writes, in source order, each with the manner of the import. */
    public List<PythonImportDirectiveModel> importDirectives = new ArrayList<>();

    /**
     * The repository source files this file's imports resolve to, in source order. The daemon
     * reports on-disk paths; the compiler replaces them with project file paths and drops any that
     * are not a file of the project.
     */
    public List<String> importedFiles = new ArrayList<>();

    public List<String> warnings = new ArrayList<>();
}
