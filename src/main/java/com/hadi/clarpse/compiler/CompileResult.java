package com.hadi.clarpse.compiler;

import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class CompileResult {

    /**
     * List of files that could not be parsed.
     */
    private Set<ProjectFile> failures = new HashSet<>();
    private Set<SkippedFile> skipped = new HashSet<>();
    private final OOPSourceCodeModel model;

    public CompileResult(OOPSourceCodeModel model) {
        this.model = model;
    }

    public CompileResult(OOPSourceCodeModel model, Set<ProjectFile> failures) {
        this(model);
        if (failures != null) {
            this.failures = failures;
        }
    }

    public CompileResult(OOPSourceCodeModel model, Set<ProjectFile> failures, Set<SkippedFile> skipped) {
        this(model, failures);
        if (skipped != null) {
            this.skipped = skipped;
        }
    }

    public OOPSourceCodeModel model() {
        return this.model;
    }

    public Collection<ProjectFile> failures() {
        return Set.copyOf(failures);
    }

    public Collection<SkippedFile> skipped() {
        return Set.copyOf(skipped);
    }
}
