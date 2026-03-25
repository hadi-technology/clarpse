package com.hadi.clarpse.compiler.java;

import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;

/**
 * Holds the result of parsing a single Java file.
 */
public class ParseOutcome {
    private final int index;
    private final OOPSourceCodeModel model;
    private final CompileFailure failure;

    public ParseOutcome(final int index, final OOPSourceCodeModel model, final CompileFailure failure) {
        this.index = index;
        this.model = model;
        this.failure = failure;
    }

    public int index() {
        return index;
    }

    public OOPSourceCodeModel model() {
        return model;
    }

    public CompileFailure failure() {
        return failure;
    }
}
