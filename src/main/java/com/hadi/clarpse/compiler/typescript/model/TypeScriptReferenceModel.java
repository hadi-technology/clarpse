package com.hadi.clarpse.compiler.typescript.model;

/**
 * Raw TypeScript reference entry emitted by the daemon.
 */
public class TypeScriptReferenceModel {

    public String kind;
    public boolean external;
    public String displayName;
    public TypeScriptTargetModel target;
}
