package com.hadi.clarpse.compiler.python.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Raw payload of a decorator argument's value.
 *
 * <p>{@code kind} is one of {@code string}, {@code number}, {@code boolean}, {@code class},
 * {@code qualified}, {@code array} or {@code other}. {@code text} holds a literal's value or a name
 * as written; {@code target} is the unique name of the repository class a name resolved to, or of a
 * dotted name's qualifier; {@code elements} are an array's elements.
 */
public class PythonDecoratorValueModel {

    public String kind;
    public String text;
    public String target;
    public List<PythonDecoratorValueModel> elements = new ArrayList<>();
}
