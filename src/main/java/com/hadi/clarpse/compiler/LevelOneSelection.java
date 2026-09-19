package com.hadi.clarpse.compiler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The level-one files a one-level compile models: those discovered from the analysed files plus
 * those the caller requires, less the analysed files, cut to the budget.
 *
 * <p>The candidates are sorted before the budget is applied, so two compiles given the same
 * candidates model the same files whatever order discovery found them in. That is what lets a
 * caller pass one union of level-one files to the compiles of two revisions and get the same
 * level-one set in both.
 */
public final class LevelOneSelection {

    private final List<ProjectFile> modelled;
    private final List<String> heldByBudget;

    private LevelOneSelection(final List<ProjectFile> modelled, final List<String> heldByBudget) {
        this.modelled = List.copyOf(modelled);
        this.heldByBudget = List.copyOf(heldByBudget);
    }

    /**
     * Selects the level-one files to model.
     *
     * @param discovered   level-one paths discovered from the analysed files
     * @param options      the compile's options, supplying the required paths and the budget
     * @param analysed     the compile's analysed files
     * @param languageFile every file of the compile's language
     * @return The selection.
     */
    public static LevelOneSelection select(final Collection<String> discovered,
                                           final AnalysisOptions options,
                                           final Collection<ProjectFile> analysed,
                                           final Collection<ProjectFile> languageFile) {
        final Map<String, ProjectFile> byPath = new HashMap<>();
        for (final ProjectFile file : languageFile) {
            byPath.put(ClarpseCompiler.normalizeForComparison(file.path()), file);
        }
        final Set<String> analysedPaths = new TreeSet<>();
        for (final ProjectFile file : analysed) {
            analysedPaths.add(ClarpseCompiler.normalizeForComparison(file.path()));
        }
        final Set<String> candidates = new TreeSet<>();
        addCandidates(candidates, discovered, byPath, analysedPaths);
        addCandidates(candidates, options.levelOnePaths(), byPath, analysedPaths);
        final List<ProjectFile> modelled = new ArrayList<>();
        final List<String> held = new ArrayList<>();
        for (final String candidate : candidates) {
            final ProjectFile file = byPath.get(candidate);
            if (modelled.size() < options.levelOneBudget()) {
                modelled.add(file);
            } else {
                held.add(file.path());
            }
        }
        return new LevelOneSelection(modelled, held);
    }

    private static void addCandidates(final Set<String> candidates, final Collection<String> paths,
                                      final Map<String, ProjectFile> byPath,
                                      final Set<String> analysedPaths) {
        if (paths == null) {
            return;
        }
        for (final String path : paths) {
            final String normalized = ClarpseCompiler.normalizeForComparison(path);
            if (normalized != null && byPath.containsKey(normalized)
                    && !analysedPaths.contains(normalized)) {
                candidates.add(normalized);
            }
        }
    }

    /**
     * The level-one files to model, sorted by path.
     *
     * @return The files.
     */
    public List<ProjectFile> modelled() {
        return modelled;
    }

    /**
     * The paths of the level-one files the budget kept out, sorted.
     *
     * @return The paths.
     */
    public List<String> heldByBudget() {
        return heldByBudget;
    }

    /**
     * The paths of the modelled level-one files.
     *
     * @return The paths, sorted.
     */
    public List<String> modelledPaths() {
        final List<String> paths = new ArrayList<>();
        for (final ProjectFile file : modelled) {
            paths.add(file.path());
        }
        return paths;
    }
}
