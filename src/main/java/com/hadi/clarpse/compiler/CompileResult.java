package com.hadi.clarpse.compiler;

import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class CompileResult {

    /**
     * List of files that could not be parsed.
     */
    private Set<CompileFailure> failures = new HashSet<>();
    private final OOPSourceCodeModel model;

    public CompileResult(OOPSourceCodeModel model) {
        this.model = model;
    }

    public CompileResult(OOPSourceCodeModel model, Set<CompileFailure> failures) {
        this(model);
        if (failures != null) {
            this.failures = failures;
        }
    }

    public OOPSourceCodeModel model() {
        return this.model;
    }

    public Collection<CompileFailure> failures() {
        return Set.copyOf(failures);
    }
}
