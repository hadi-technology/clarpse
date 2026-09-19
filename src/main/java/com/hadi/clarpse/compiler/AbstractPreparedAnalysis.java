package com.hadi.clarpse.compiler;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The shared part of every language's {@link PreparedAnalysis}: the discovered level-one set, the
 * choice of the level-one files to model from it, the caller's paths and the budget, the closed
 * state, and the temporary directory the analysis made its {@link ProjectFiles} write.
 *
 * <p>A language supplies the discovery, done before construction, {@link #complete} to model a
 * chosen level one, and {@link #release} to free what it holds, and builds the analysis through
 * {@link #prepareCleanly}. When preparing made the project files write themselves to a temporary
 * directory, that directory belongs to the analysis: it is deleted when preparing fails, and
 * otherwise when the analysis is closed. A directory the project files had written before is left
 * to their owner.
 */
public abstract class AbstractPreparedAnalysis implements PreparedAnalysis {

    private final AnalysisOptions options;
    private final List<ProjectFile> analysed;
    private final List<ProjectFile> languageFiles;
    private final Set<String> discovered;
    private ProjectFiles ownedTempDir;
    private boolean closed;

    /** Builds a language's prepared analysis. */
    @FunctionalInterface
    public interface Preparation {

        /**
         * Resolves the analysed files and discovers level one.
         *
         * @return The prepared analysis.
         */
        AbstractPreparedAnalysis prepare() throws CompileException;
    }

    /**
     * Runs a preparation, and takes ownership of the temporary directory it made the project files
     * write: the directory is deleted at once if the preparation fails, and when the analysis is
     * closed if it succeeds.
     *
     * @param projectFiles The project files the preparation reads.
     * @param preparation  The preparation.
     * @return The prepared analysis.
     */
    public static PreparedAnalysis prepareCleanly(final ProjectFiles projectFiles, final Preparation preparation)
            throws CompileException {
        final boolean hadTempDir = projectFiles.hasTempDir();
        boolean prepared = false;
        try {
            final AbstractPreparedAnalysis analysis = preparation.prepare();
            if (!hadTempDir && projectFiles.hasTempDir()) {
                analysis.ownedTempDir = projectFiles;
            }
            prepared = true;
            return analysis;
        } finally {
            if (!prepared && !hadTempDir) {
                projectFiles.deleteTempDir();
            }
        }
    }

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

    /**
     * Releases what the analysis holds, and deletes the temporary directory it made its project
     * files write, even when releasing fails.
     */
    @Override
    public final void close() {
        if (!closed) {
            closed = true;
            try {
                release();
            } finally {
                if (ownedTempDir != null) {
                    ownedTempDir.deleteTempDir();
                    ownedTempDir = null;
                }
            }
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
