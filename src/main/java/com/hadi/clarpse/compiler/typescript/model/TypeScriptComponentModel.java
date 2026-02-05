package com.hadi.clarpse.compiler.typescript.model;

import java.util.ArrayList;
import java.util.List;

public class TypeScriptComponentModel {

    public String kind;
    public String name;
    public String signature;
    public String returnType;
    public String type;
    public String jsDoc;
    public int cyclo;
    public List<String> modifiers = new ArrayList<>();
    public List<TypeScriptComponentModel> members = new ArrayList<>();
    public List<TypeScriptReferenceModel> references = new ArrayList<>();
}
