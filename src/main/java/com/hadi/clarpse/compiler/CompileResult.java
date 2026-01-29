package com.hadi.clarpse.compiler;

import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;

import java.util.Collection;
import java.util.Set;

public class CompileResult {

    /**
     * List of files that could not be parsed.
     */
    private Set<ProjectFile> failures;
    private final OOPSourceCodeModel model;

    public CompileResult(OOPSourceCodeModel model) {
        this.model = model;
    }

    public CompileResult(OOPSourceCodeModel model, Set<ProjectFile> failures) {
        this(model);
        this.failures = failures;
    }

    public OOPSourceCodeModel model() {
        return this.model;
    }

    public Collection<ProjectFile> failures() {
        return Set.copyOf(failures);
    }
}
