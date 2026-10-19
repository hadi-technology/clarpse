package com.hadi.clarpse.compiler.typescript.model;

/**
 * Raw payload of one argument written on a decorator: its name, which is null when it was
 * positional, and its value.
 */
public class TypeScriptArgumentModel {

    public String name;
    public TypeScriptValueModel value;
}
