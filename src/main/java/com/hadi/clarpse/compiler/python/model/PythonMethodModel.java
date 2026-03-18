package com.hadi.clarpse.compiler.python.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Raw Python method/function payload returned by the daemon.
 */
public class PythonMethodModel {

    public String name;
    public String signature;
    public String uniqueName;
    public int implementationHash;
    public String comment;
    public int cyclo;
    public boolean classMethod;
    public boolean staticMethod;
    public List<PythonParamModel> params = new ArrayList<>();

    @JsonProperty("return")
    public PythonTypeRefModel returnType;
}
