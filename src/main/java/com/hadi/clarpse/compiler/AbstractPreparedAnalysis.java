package com.hadi.clarpse.compiler;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The shared part of every language's {@link PreparedAnalysis}: the discovered level-one set, the
 * choice of the level-one files to model from it, the caller's paths and the budget, and the
 * closed state.
 *
 * <p>A language supplies the discovery, done before construction, {@link #complete} to model a
 * chosen level one, and {@link #release} to free what it holds.
 */
public abstract class AbstractPreparedAnalysis implements PreparedAnalysis {

    private final AnalysisOptions options;
    private final List<ProjectFile> analysed;
    private final List<ProjectFile> languageFiles;
    private final Set<String> discovered;
    private boolean closed;

    /**
     * Creates the shared state.
     *
     * @param options       The compile's options.
     * @param analysed      The analysed files.
     * @param languageFiles Every file of the language.
     * @param discovered    The level-one paths discovered from the analysed files.
     */
    protected AbstractPreparedAnalysis(final AnalysisOptions options, final List<ProjectFile> analysed,
                                       final List<ProjectFile> languageFiles, final Collection<String> discovered) {
        this.options = options;
        this.analysed = List.copyOf(analysed);
        this.languageFiles = List.copyOf(languageFiles);
        final Set<String> levelOne = new TreeSet<>(discovered);
        for (final ProjectFile file : analysed) {
            levelOne.remove(file.path());
        }
        this.discovered = Set.copyOf(levelOne);
    }

    @Override
    public final Set<String> levelOneFiles() {
        requireOpen();
        return new TreeSet<>(discovered);
    }

    @Override
    public final CompileResult compile(final Collection<String> additionalLevelOnePaths) throws CompileException {
        requireOpen();
        final Set<String> required = new LinkedHashSet<>(options.levelOnePaths());
        required.addAll(AnalysisOptions.nonEmpty(additionalLevelOnePaths));
        return complete(LevelOneSelection.select(discovered, options.withLevelOnePaths(required), analysed,
                languageFiles));
    }

    /**
     * Models the analysed files and the selected level-one files.
     *
     * @param selection The level-one files to model and those the budget held back.
     * @return The compile result, with its level-one report.
     */
    protected abstract CompileResult complete(LevelOneSelection selection) throws CompileException;

    /** Frees what this analysis holds. Called once, by the first {@link #close()}. */
    protected abstract void release();

    @Override
    public final void close() {
        if (!closed) {
            closed = true;
            release();
        }
    }

    protected final AnalysisOptions options() {
        return options;
    }

    protected final List<ProjectFile> analysed() {
        return analysed;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("This prepared analysis is closed.");
        }
    }
}
