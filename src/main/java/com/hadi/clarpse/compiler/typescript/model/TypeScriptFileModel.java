package com.hadi.clarpse.compiler.typescript.model;

import java.util.ArrayList;
import java.util.List;

public class TypeScriptFileModel {

    public String filePath;
    public List<TypeScriptComponentModel> declarations = new ArrayList<>();
}
