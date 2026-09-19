package com.hadi.clarpse.compiler;

import java.util.List;

/**
 * What a one-level compile loaded beyond its analysed files.
 *
 * <p>Every path is in the form of {@link ProjectFile#path()}, and every list is sorted. A compile
 * that was not one-level reports {@link #none()}.
 */
public final class LevelOneReport {

    private static final LevelOneReport NONE = new LevelOneReport(List.of(), List.of(), List.of(), 0);

    private final List<String> levelOneFiles;
    private final List<String> heldByBudget;
    private final List<String> loadedBeyondLevelOne;
    private final int notLoadedReferences;

    /**
     * Creates a report.
     *
     * @param levelOneFiles        level-one files whose components are in the model
     * @param heldByBudget         level-one files the budget kept out of the model
     * @param loadedBeyondLevelOne files the resolver read, without modelling them, to resolve the
     *                             analysed files' references
     * @param notLoadedReferences  references left in the not-loaded state across the model
     */
    public LevelOneReport(final List<String> levelOneFiles, final List<String> heldByBudget,
                          final List<String> loadedBeyondLevelOne, final int notLoadedReferences) {
        this.levelOneFiles = sorted(levelOneFiles);
        this.heldByBudget = sorted(heldByBudget);
        this.loadedBeyondLevelOne = sorted(loadedBeyondLevelOne);
        this.notLoadedReferences = notLoadedReferences;
    }

    /**
     * The report of a compile that was not one-level.
     *
     * @return An empty report.
     */
    public static LevelOneReport none() {
        return NONE;
    }

    private static List<String> sorted(final List<String> paths) {
        if (paths == null) {
            return List.of();
        }
        return paths.stream().sorted().toList();
    }

    public List<String> levelOneFiles() {
        return levelOneFiles;
    }

    public List<String> heldByBudget() {
        return heldByBudget;
    }

    public List<String> loadedBeyondLevelOne() {
        return loadedBeyondLevelOne;
    }

    public int notLoadedReferences() {
        return notLoadedReferences;
    }

    /**
     * Whether the level-one budget kept any file out of the model.
     *
     * @return {@code true} when {@link #heldByBudget()} is not empty.
     */
    public boolean budgetHit() {
        return !heldByBudget.isEmpty();
    }
}
