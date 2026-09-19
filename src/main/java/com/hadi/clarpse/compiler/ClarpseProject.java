package com.hadi.clarpse.compiler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Represents a source code project that is analyzed to produce an object-oriented representation
 * of the code.
 */
public class ClarpseProject {

    private static final Logger LOGGER = LogManager.getLogger(ClarpseProject.class);
    private final ProjectFiles projectFiles;
    private final Lang lang;
    private final Collection<String> analyzedFilePaths;
    private final AnalysisOptions options;
    private CompileResult compileResult;

    public ClarpseProject(ProjectFiles pfs, Lang lang) {
        this(pfs, lang, null);
    }

    public ClarpseProject(final ProjectFiles pfs,
                          final Lang lang,
                          final Collection<String> pathsToAnalyze) {
        this(pfs, lang, pathsToAnalyze, AnalysisOptions.full());
    }

    /**
     * A project whose compile uses the given options.
     *
     * @param pfs            The project's files.
     * @param lang           The language to compile.
     * @param pathsToAnalyze The files to analyse, {@code null} for all files of the language.
     * @param options        Analysis options; {@code null} is read as {@link AnalysisOptions#full()}.
     */
    public ClarpseProject(final ProjectFiles pfs,
                          final Lang lang,
                          final Collection<String> pathsToAnalyze,
                          final AnalysisOptions options) {
        validateInput(lang);
        if (options == null) {
            this.options = AnalysisOptions.full();
        } else {
            this.options = options;
        }
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
            CompileResult compileRes = parsingTool.compile(this.projectFiles, this.analyzedFilePaths,
                    this.options);
            long duration = (System.nanoTime() - startTime) / 1000000;
            LOGGER.info("Parsed " + compileRes.model().size() + " components from "
                    + analyzedLangFileCount + " " + this.lang.value() + " files in " + duration + " ms.");
            this.compileResult = compileRes;
        }
        LOGGER.info("Returning generated compile result ..");
        return this.compileResult;
    }

    /**
     * The level-one files of this project's analysed files, discovered without modelling them.
     *
     * <p>A caller comparing two revisions computes this for each, and passes the union to both
     * compiles through {@link AnalysisOptions#withLevelOnePaths(Collection)}.
     *
     * @return The discovered level-one paths, sorted, excluding the analysed files.
     * @throws IllegalStateException When this project is not a one-level compile.
     */
    public Set<String> levelOneFiles() throws CompileException {
        if (!this.options.isOneLevel(this.analyzedFilePaths)) {
            throw new IllegalStateException("Level-one files exist only for a one-level compile of named files.");
        }
        return CompilerFactory.getParsingTool(this.lang)
                .levelOneFiles(this.projectFiles, this.analyzedFilePaths, this.options);
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
}
