package com.hadi.clarpse.compiler.typescript.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Raw TypeScript daemon model for one source file.
 */
public class TypeScriptFileModel {

    public String filePath;
    public List<TypeScriptComponentModel> declarations = new ArrayList<>();
}
