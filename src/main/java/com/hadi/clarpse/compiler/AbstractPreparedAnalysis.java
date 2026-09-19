package com.hadi.clarpse.compiler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
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
 * chosen level one, {@link #extend} to resolve added analysed files, and {@link #release} to free
 * what it holds, and builds the analysis through {@link #prepareCleanly}. When preparing or
 * extending made the project files write themselves to a temporary directory, that directory
 * belongs to the analysis: it is deleted when preparing fails, and otherwise when the analysis is
 * closed. A directory the project files had written before is left to their owner.
 */
public abstract class AbstractPreparedAnalysis implements PreparedAnalysis {

    private final AnalysisOptions options;
    private final List<ProjectFile> languageFiles;
    private List<ProjectFile> analysed;
    private Set<String> discovered;
    private ProjectFiles projectFiles;
    private ProjectFiles ownedTempDir;
    private boolean closed;

    /**
     * What resolving added analysed files yields: every analysed file of the analysis, in the order
     * a fresh preparation would list them, and the level-one paths discovered from all of them.
     *
     * @param analysed   The analysed files, the added ones included.
     * @param discovered The level-one paths discovered from every analysed file.
     */
    protected record Extension(List<ProjectFile> analysed, Collection<String> discovered) {

        /**
         * Copies both collections, so the extension holds what the language returned at the time.
         *
         * @param analysed   The analysed files, the added ones included.
         * @param discovered The level-one paths discovered from every analysed file.
         */
        public Extension {
            analysed = List.copyOf(analysed);
            discovered = List.copyOf(discovered);
        }
    }

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
            analysis.projectFiles = projectFiles;
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
        this.languageFiles = List.copyOf(languageFiles);
        this.analysed = List.copyOf(analysed);
        this.discovered = levelOneOutside(discovered, analysed);
    }

    private static Set<String> levelOneOutside(final Collection<String> discovered,
                                               final List<ProjectFile> analysed) {
        final Set<String> levelOne = new TreeSet<>(discovered);
        for (final ProjectFile file : analysed) {
            levelOne.remove(file.path());
        }
        return Set.copyOf(levelOne);
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

    @Override
    public final void extendFocus(final Collection<String> additionalFocusPaths) throws CompileException {
        requireOpen();
        final Set<String> known = new HashSet<>();
        for (final ProjectFile file : analysed) {
            known.add(ClarpseCompiler.normalizeForComparison(file.path()));
        }
        final Set<String> wanted = new HashSet<>(known);
        for (final String path : AnalysisOptions.nonEmpty(additionalFocusPaths)) {
            wanted.add(ClarpseCompiler.normalizeForComparison(path));
        }
        final List<ProjectFile> added = new ArrayList<>();
        final List<ProjectFile> focus = new ArrayList<>();
        for (final ProjectFile file : languageFiles) {
            final String normalized = ClarpseCompiler.normalizeForComparison(file.path());
            if (wanted.contains(normalized)) {
                focus.add(file);
                if (!known.contains(normalized)) {
                    added.add(file);
                }
            }
        }
        if (added.isEmpty()) {
            return;
        }
        // An interrupted thread cannot start a resolver reliably, and a resolver that cannot start
        // would be recorded as a failure of the added files; stop before touching anything.
        if (Thread.currentThread().isInterrupted()) {
            throw new CompileException("Interrupted before extending the analysis.", new InterruptedException());
        }
        final boolean hadTempDir = projectFiles != null && projectFiles.hasTempDir();
        try {
            final Extension extension = extend(added, focus);
            this.analysed = List.copyOf(extension.analysed());
            this.discovered = levelOneOutside(extension.discovered(), this.analysed);
        } finally {
            if (!hadTempDir && ownedTempDir == null && projectFiles != null && projectFiles.hasTempDir()) {
                ownedTempDir = projectFiles;
            }
        }
    }

    /**
     * Resolves added analysed files, reusing what the analysis already holds, and rediscovers level
     * one over every analysed file. It commits its own state only once nothing can fail any more, so
     * an exception leaves the analysis as it was.
     *
     * @param added The analysed files being added, in language-file order.
     * @param focus Every analysed file afterwards, the added ones included, in language-file order.
     * @return The analysed files and the level-one paths discovered from them.
     */
    protected Extension extend(final List<ProjectFile> added, final List<ProjectFile> focus)
            throws CompileException {
        throw new UnsupportedOperationException("Extending is not supported by " + getClass().getSimpleName());
    }

    /**
     * The project files this analysis was prepared from, for a language whose resolution reads them
     * again when the analysis is extended.
     *
     * @return The project files, or {@code null} for an analysis not built through
     *         {@link #prepareCleanly}.
     */
    protected final ProjectFiles projectFiles() {
        return projectFiles;
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
