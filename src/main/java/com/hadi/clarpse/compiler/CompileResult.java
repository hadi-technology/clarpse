package com.hadi.clarpse.compiler;

import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Compilation outcome containing the assembled model and non-fatal failures.
 */
public class CompileResult {

    /**
     * List of files that could not be parsed.
     */
    private Set<CompileFailure> failures = new HashSet<>();
    private final OOPSourceCodeModel model;
    private LevelOneReport levelOne = LevelOneReport.none();

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

    /**
     * What a one-level compile loaded beyond its analysed files.
     *
     * @return The report; {@link LevelOneReport#none()} for a compile that was not one-level.
     */
    public LevelOneReport levelOne() {
        return this.levelOne;
    }

    /**
     * This result with the given level-one report.
     *
     * @param report The report; {@code null} is read as {@link LevelOneReport#none()}.
     * @return This result.
     */
    public CompileResult withLevelOne(final LevelOneReport report) {
        if (report == null) {
            this.levelOne = LevelOneReport.none();
        } else {
            this.levelOne = report;
        }
        return this;
    }
}
