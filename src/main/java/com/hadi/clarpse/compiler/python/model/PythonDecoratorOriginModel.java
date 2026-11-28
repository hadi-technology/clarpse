package com.hadi.clarpse.compiler.python.model;

/**
 * Raw payload of where a decorator comes from: the dotted name an import gives it, and whether the
 * name could not be resolved to a declaration.
 */
public class PythonDecoratorOriginModel {

    public String name;
    public boolean unresolved;
}
