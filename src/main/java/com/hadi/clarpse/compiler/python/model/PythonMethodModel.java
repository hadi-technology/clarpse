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
    /** Names of the decorators ({@code @app.route}) applied to this method or function. */
    public List<String> decorators = new ArrayList<>();
    public List<PythonParamModel> params = new ArrayList<>();

    /**
     * The local variables this method or function binds in its own body, one per distinct name.
     * Shares the field payload shape: a name, the declared type where there is one, and the
     * reference that type resolves to.
     */
    public List<PythonFieldModel> locals = new ArrayList<>();

    @JsonProperty("return")
    public PythonTypeRefModel returnType;

    @JsonProperty("bodyReferences")
    public List<PythonTypeRefModel> bodyReferences = new ArrayList<>();
}
