package com.hadi.clarpse.compiler.java;

import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;

import java.util.Set;

/**
 * Holds the results of parsing Java files.
 */
public class ParseResults {
    private final OOPSourceCodeModel model;
    private final Set<CompileFailure> failures;

    public ParseResults(final OOPSourceCodeModel model, final Set<CompileFailure> failures) {
        this.model = model;
        this.failures = failures;
    }

    public OOPSourceCodeModel model() {
        return model;
    }

    public Set<CompileFailure> failures() {
        return failures;
    }
}
