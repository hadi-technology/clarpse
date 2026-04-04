package com.hadi.clarpse.compiler;

import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a source code project that is analyzed to produce an object-oriented representation
 * of the code.
 */
public class ClarpseProject {

    private static final Logger LOGGER = LogManager.getLogger(ClarpseProject.class);
    private final ProjectFiles projectFiles;
    private final Lang lang;
    private final Collection<String> analyzedFilePaths;
    private CompileResult compileResult;

    public ClarpseProject(ProjectFiles pfs, Lang lang) {
        this(pfs, lang, null);
    }

    public ClarpseProject(final ProjectFiles pfs,
                          final Lang lang,
                          final Collection<String> pathsToAnalyze) {
        validateInput(lang);
        this.projectFiles = pfs;
        this.lang = lang;
        if (pathsToAnalyze == null) {
            this.analyzedFilePaths = null;
        } else {
            this.analyzedFilePaths = List.copyOf(pathsToAnalyze);
        }
    }

    private void validateInput(Lang lang) {
        if (!supportedLang(lang)) {
            throw new IllegalArgumentException("The specified source language is not supported!");
        }
    }

    public CompileResult result() throws CompileException {
        if (this.compileResult == null) {
            int totalLangFileCount = this.projectFiles.files(this.lang).size();
            int analyzedLangFileCount = analyzedFileCount();
            LOGGER.info("Parsing " + analyzedLangFileCount + " " + this.lang.value()
                    + " source files (from " + totalLangFileCount + " available files)..");
            long startTime = System.nanoTime();
            final ClarpseCompiler parsingTool = CompilerFactory.getParsingTool(this.lang);
            CompileResult compileRes = parsingTool.compile(this.projectFiles, this.analyzedFilePaths);
            long duration = (System.nanoTime() - startTime) / 1000000;
            LOGGER.info("Parsed " + compileRes.model().size() + " components from "
                    + analyzedLangFileCount + " " + this.lang.value() + " files in " + duration + " ms.");
            this.compileResult = compileRes;
        }
        LOGGER.info("Returning generated compile result ..");
        return this.compileResult;
    }

    private int analyzedFileCount() {
        if (this.analyzedFilePaths == null || this.analyzedFilePaths.isEmpty()) {
            return this.projectFiles.files(this.lang).size();
        }
        return ClarpseCompiler.analyzedFiles(this.projectFiles, this.lang, this.analyzedFilePaths).size();
    }

    private boolean supportedLang(final Lang language) throws IllegalArgumentException {
        boolean isValidLang = false;
        for (Lang tmpLang : Lang.supportedLanguages()) {
            if (language == tmpLang) {
                isValidLang = true;
                break;
            }
        }
        return isValidLang;
    }

    /**
     * Incrementally updates a previously parsed model with file-level changes.
     *
     * <p>This is the core incremental API. Given a base model and a set of
     * changed/deleted files, it produces an updated model without re-parsing
     * unchanged files.</p>
     *
     * <p>Algorithm:
     *   <ol>
     *     <li>Deep-copy the base model</li>
     *     <li>Remove all components from changed + deleted files</li>
     *     <li>Parse only the changed files using the appropriate compiler</li>
     *     <li>Merge newly parsed components into the updated model</li>
     *     <li>Re-classify references for affected components against the
     *         FULL updated model (not a sub-model — this is critical for
     *         correctness, since references to unchanged components must
     *         still resolve as internal)</li>
     *   </ol>
     *
     * @param baseModel     The previously parsed model (not modified)
     * @param changedFiles  File path → new content, for added or modified files
     * @param deletedFiles  File paths that were deleted
     * @param lang          Programming language of the files
     * @return Updated model with only the changed files re-parsed
     * @throws CompileException if parsing fails
     */
    public static CompileResult updateModel(
        OOPSourceCodeModel baseModel,
        Map<String, String> changedFiles,
        Set<String> deletedFiles,
        Lang lang
    ) throws CompileException {

        if (baseModel == null) {
            throw new IllegalArgumentException("baseModel cannot be null");
        }
        if (changedFiles == null) changedFiles = Map.of();
        if (deletedFiles == null) deletedFiles = Set.of();
        if (lang == null) {
            lang = detectLanguage(changedFiles.keySet());
        }

        // Short-circuit: nothing changed
        if (changedFiles.isEmpty() && deletedFiles.isEmpty()) {
            return new CompileResult(baseModel.copy(), Set.of());
        }

        LOGGER.info("Incremental update: {} changed, {} deleted files",
            changedFiles.size(), deletedFiles.size());

        final OOPSourceCodeModel updatedModel = baseModel.copy();
        final Set<CompileFailure> failures = new HashSet<>();

        // ── Phase 1: Remove components from changed + deleted files ──
        Set<String> allAffectedFiles = new HashSet<>(deletedFiles);
        allAffectedFiles.addAll(changedFiles.keySet());

        Set<String> removedComponentNames = new HashSet<>();
        for (String file : allAffectedFiles) {
            removedComponentNames.addAll(
                updatedModel.removeComponentsForFile(file));
        }

        // ── Phase 2: Parse only changed files ──
        if (!changedFiles.isEmpty()) {
            ProjectFiles changedPFs = new ProjectFiles();
            changedFiles.forEach((path, content) ->
                changedPFs.insertFile(new ProjectFile(path, content)));

            ClarpseCompiler compiler = CompilerFactory.getParsingTool(lang);
            // Pass null for analyzedFilePaths → parse all files in the ProjectFiles
            CompileResult result = compiler.compile(changedPFs, null);
            updatedModel.merge(result.model());
            failures.addAll(result.failures());
        }

        // ── Phase 3: Re-classify references for affected components ──
        // Collect names of all components in changed files (old + new)
        Set<String> componentsToReclassify = new HashSet<>(removedComponentNames);
        for (String file : changedFiles.keySet()) {
            componentsToReclassify.addAll(
                updatedModel.getComponentNamesForFile(file));
        }
        // Also reclassify components that reference any changed component,
        // since the targets they point to may have changed or disappeared.
        Set<String> referrers = findReferrersTo(
            updatedModel, baseModel, removedComponentNames);
        componentsToReclassify.addAll(referrers);

        classifyReferencesForSubset(updatedModel, componentsToReclassify);

        return new CompileResult(updatedModel, failures);
    }

    /**
     * Finds components that have references pointing to any of the given
     * target component names. Searches the previous model (since those
     * components may have been removed from the current one), but only
     * returns names that still exist in the current model.
     */
    private static Set<String> findReferrersTo(
        OOPSourceCodeModel currentModel,
        OOPSourceCodeModel previousModel,
        Set<String> targetComponentNames
    ) {
        return previousModel.components()
            .filter(cmp -> cmp.references().stream()
                .anyMatch(ref -> targetComponentNames.contains(
                    ref.invokedComponent())))
            .map(Component::uniqueName)
            .filter(currentModel::containsComponent)
            .collect(Collectors.toSet());
    }

    /**
     * Classifies references for a subset of components.
     *
     * <p>IMPORTANT: Resolution is performed against the FULL model, not a
     * sub-model. If you classify against a sub-model, references to
     * components outside the sub-model are incorrectly marked as external.</p>
     */
    private static void classifyReferencesForSubset(
        OOPSourceCodeModel fullModel,
        Set<String> componentNames
    ) {
        fullModel.components()
            .filter(cmp -> componentNames.contains(cmp.uniqueName()))
            .forEach(component -> {
                final Set<com.hadi.clarpse.reference.ComponentReference> internal = new java.util.LinkedHashSet<>();
                final Set<com.hadi.clarpse.reference.ComponentReference> external = new java.util.LinkedHashSet<>();
                component.references().forEach(ref -> {
                    boolean isInternal = fullModel.containsComponent(
                        ref.invokedComponent());
                    ref.setExternal(!isInternal);
                    if (isInternal) {
                        internal.add(ref);
                    } else {
                        external.add(ref);
                    }
                });
                component.setReferenceClassification(internal, external);
            });
    }

    /**
     * Detects the programming language from file extensions.
     */
    private static Lang detectLanguage(Set<String> filePaths) {
        for (String path : filePaths) {
            int dotIndex = path.lastIndexOf('.');
            if (dotIndex > 0) {
                Lang lang = Lang.langFromExtn(path.substring(dotIndex + 1));
                if (lang != null) return lang;
            }
        }
        throw new IllegalArgumentException(
            "Unable to detect language from file extensions: " + filePaths);
    }
}
