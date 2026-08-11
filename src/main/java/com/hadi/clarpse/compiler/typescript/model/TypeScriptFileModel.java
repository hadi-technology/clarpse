package com.hadi.clarpse.compiler.typescript.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Raw TypeScript daemon model for one source file.
 */
public class TypeScriptFileModel {

    public String filePath;
    public List<TypeScriptComponentModel> declarations = new ArrayList<>();

    /** Names this file imports. Empty until issue #156; the daemon did not collect them. */
    public List<TypeScriptImportModel> imports = new ArrayList<>();
}
