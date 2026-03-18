package com.hadi.clarpse.compiler.python.model;

/**
 * Raw Python field/module-variable payload returned by the daemon.
 */
public class PythonFieldModel {

    public String name;
    public String rawType;
    public String targetUniqueName;
    public String externalLabel;
}
