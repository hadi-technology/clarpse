package com.hadi.clarpse.compiler.typescript.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Raw payload of a decorator argument's value.
 *
 * <p>{@code kind} is one of {@code string}, {@code number}, {@code boolean}, {@code enum},
 * {@code class}, {@code array} or {@code other}. {@code text} holds a literal's value or a name as
 * written, {@code target} the repository declaration a class resolved to, and {@code elements} an
 * array's elements.
 */
public class TypeScriptValueModel {

    public String kind;
    public String text;
    public TypeScriptTargetModel target;
    public List<TypeScriptValueModel> elements = new ArrayList<>();
}
