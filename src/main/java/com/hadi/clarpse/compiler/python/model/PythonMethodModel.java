package com.hadi.clarpse.compiler.python.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class PythonMethodModel {

    public String name;
    public String signature;
    public String uniqueName;
    public List<PythonParamModel> params = new ArrayList<>();

    @JsonProperty("return")
    public PythonTypeRefModel returnType;
}
