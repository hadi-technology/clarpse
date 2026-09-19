package com.hadi.clarpse.compiler;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Immutable settings for one compile: how many levels of referenced files to model past the
 * analysed files, the most files the last of those levels may hold, and files of that level the
 * caller requires in addition to those discovered.
 *
 * <p>The depth counts levels of references. Depth 0, the default and {@link #full()}, models the
 * analysed files alone and reproduces the behaviour of a compile given no options. Depth 1 also
 * models the repository files the analysed files reference, and marks their components boundary.
 * Only depths 0 and 1 are offered; see {@code docs/one-level-analysis.md} for why.
 */
public final class AnalysisOptions {

    /** The default for {@link #levelOneBudget()}. */
    public static final int DEFAULT_LEVEL_ONE_BUDGET = 1000;

    /** The deepest level of referenced files a compile may model. */
    public static final int MAX_DEPTH = 1;

    private static final AnalysisOptions FULL = new AnalysisOptions(0, DEFAULT_LEVEL_ONE_BUDGET, Set.of());

    private final int depth;
    private final int levelOneBudget;
    private final Set<String> levelOnePaths;

    private AnalysisOptions(final int depth, final int levelOneBudget, final Set<String> levelOnePaths) {
        this.depth = depth;
        this.levelOneBudget = levelOneBudget;
        this.levelOnePaths = levelOnePaths;
    }

    /**
     * Options for an ordinary compile of the analysed files: depth 0.
     *
     * @return The default options.
     */
    public static AnalysisOptions full() {
        return FULL;
    }

    /**
     * Options for a one-level compile with the default budget: {@code full().withDepth(1)}.
     *
     * @return One-level options.
     */
    public static AnalysisOptions oneLevel() {
        return FULL.withDepth(1);
    }

    /**
     * These options with a different depth.
     *
     * @param levels How many levels of referenced files to model past the analysed files. Only 1 is
     *               accepted: depth 0 is {@link #full()}, and deeper levels are not offered.
     * @return New options.
     * @throws IllegalArgumentException For any depth other than 1.
     */
    public AnalysisOptions withDepth(final int levels) {
        if (levels != MAX_DEPTH) {
            throw new IllegalArgumentException("Only depth " + MAX_DEPTH + " is supported, got " + levels
                    + "; use AnalysisOptions.full() for an ordinary compile. See docs/one-level-analysis.md.");
        }
        return new AnalysisOptions(levels, levelOneBudget, levelOnePaths);
    }

    /**
     * These options with a different level-one budget.
     *
     * @param budget The most level-one files to model; must not be negative.
     * @return New options.
     */
    public AnalysisOptions withLevelOneBudget(final int budget) {
        if (budget < 0) {
            throw new IllegalArgumentException("The level-one budget must not be negative.");
        }
        return new AnalysisOptions(depth, budget, levelOnePaths);
    }

    /**
     * These options with level-one files the compile must model in addition to those it discovers,
     * in the path form of {@link ProjectFile#path()}.
     *
     * <p>A caller comparing two revisions passes the union of both revisions' level-one sets, from
     * {@link PreparedAnalysis#levelOneFiles()}, to the compile of each, so a type loaded in one
     * revision is loaded in the other as well. Paths that name no file of the language, or that
     * name an analysed file, are ignored.
     *
     * @param paths Level-one file paths; may be {@code null}.
     * @return New options.
     */
    public AnalysisOptions withLevelOnePaths(final Collection<String> paths) {
        return new AnalysisOptions(depth, levelOneBudget, Set.copyOf(nonEmpty(paths)));
    }

    static Set<String> nonEmpty(final Collection<String> paths) {
        final Set<String> copy = new LinkedHashSet<>();
        if (paths != null) {
            for (final String path : paths) {
                if (path != null && !path.isEmpty()) {
                    copy.add(path);
                }
            }
        }
        return copy;
    }

    /**
     * How many levels of referenced files past the analysed files a compile models.
     *
     * @return 0 for an ordinary compile, 1 for a one-level compile.
     */
    public int depth() {
        return depth;
    }

    /**
     * The most level-one files a one-level compile models. Level one is the boundary level: its
     * components are marked boundary. Files beyond the budget are reported by
     * {@link LevelOneReport#heldByBudget()} and references into them are left not loaded.
     *
     * @return The budget.
     */
    public int levelOneBudget() {
        return levelOneBudget;
    }

    /**
     * Level-one files required by the caller.
     *
     * @return An unmodifiable set of paths.
     */
    public Set<String> levelOnePaths() {
        return levelOnePaths;
    }

    /**
     * Whether a compile with these options, over the given analysed paths, models a boundary level.
     * It needs analysed files to start from; with none named, every file is analysed and the compile
     * is an ordinary one.
     *
     * @param analyzedFilePaths The analysed paths of the compile, {@code null} for all files.
     * @return {@code true} for a one-level compile.
     */
    public boolean modelsBoundary(final Collection<String> analyzedFilePaths) {
        return depth > 0 && analyzedFilePaths != null;
    }
}
