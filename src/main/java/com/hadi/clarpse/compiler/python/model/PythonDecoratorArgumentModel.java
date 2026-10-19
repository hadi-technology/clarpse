package com.hadi.clarpse.compiler.python.model;

/**
 * Raw payload of one argument written on a decorator: its keyword, or null when it was positional,
 * and its value.
 */
public class PythonDecoratorArgumentModel {

    public String name;
    public PythonDecoratorValueModel value;
}
