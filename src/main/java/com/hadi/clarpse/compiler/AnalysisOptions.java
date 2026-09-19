package com.hadi.clarpse.compiler;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Immutable settings for one compile: the analysis depth, the most level-one files a one-level
 * compile may model, and level-one files the caller requires in addition to those discovered.
 *
 * <p>{@link #full()} reproduces the behaviour of a compile given no options at all.
 */
public final class AnalysisOptions {

    /** The default for {@link #levelOneBudget()}. */
    public static final int DEFAULT_LEVEL_ONE_BUDGET = 1000;

    private static final AnalysisOptions FULL =
            new AnalysisOptions(AnalysisDepth.FULL, DEFAULT_LEVEL_ONE_BUDGET, Set.of());

    private final AnalysisDepth depth;
    private final int levelOneBudget;
    private final Set<String> levelOnePaths;

    private AnalysisOptions(final AnalysisDepth depth, final int levelOneBudget,
                            final Set<String> levelOnePaths) {
        this.depth = depth;
        this.levelOneBudget = levelOneBudget;
        this.levelOnePaths = levelOnePaths;
    }

    /**
     * Options for an ordinary compile of the analysed files.
     *
     * @return The full-depth options.
     */
    public static AnalysisOptions full() {
        return FULL;
    }

    /**
     * Options for a one-level compile with the default budget and no required level-one files.
     *
     * @return One-level options.
     */
    public static AnalysisOptions oneLevel() {
        return new AnalysisOptions(AnalysisDepth.ONE_LEVEL, DEFAULT_LEVEL_ONE_BUDGET, Set.of());
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
     * These options with level-one files the compile must model in addition to those it
     * discovers, in the path form of {@link ProjectFile#path()}.
     *
     * <p>A caller comparing two revisions passes the union of both revisions' level-one sets
     * (from {@link ClarpseProject#levelOneFiles()}) to the compile of each, so a type loaded in one
     * revision is loaded in the other as well. Paths that name no file of the language, or that
     * name an analysed file, are ignored.
     *
     * @param paths Level-one file paths; may be {@code null}.
     * @return New options.
     */
    public AnalysisOptions withLevelOnePaths(final Collection<String> paths) {
        final Set<String> copy = new LinkedHashSet<>();
        if (paths != null) {
            for (final String path : paths) {
                if (path != null && !path.isEmpty()) {
                    copy.add(path);
                }
            }
        }
        return new AnalysisOptions(depth, levelOneBudget, Set.copyOf(copy));
    }

    public AnalysisDepth depth() {
        return depth;
    }

    /**
     * The most level-one files a one-level compile models. Files beyond it are reported by
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
     * Whether a compile with these options, over the given analysed paths, is a one-level compile.
     * A one-level compile needs analysed files to start from; with none named, every file is
     * analysed and the compile is an ordinary one.
     *
     * @param analyzedFilePaths The analysed paths of the compile, {@code null} for all files.
     * @return {@code true} for a one-level compile.
     */
    public boolean isOneLevel(final Collection<String> analyzedFilePaths) {
        return depth == AnalysisDepth.ONE_LEVEL && analyzedFilePaths != null;
    }
}
