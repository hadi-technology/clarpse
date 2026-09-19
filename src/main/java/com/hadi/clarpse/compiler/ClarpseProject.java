package com.hadi.clarpse.compiler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.List;

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
     * Starts a one-level compile: resolves the analysed files and discovers their level-one files,
     * holding what completing the compile reuses. A caller comparing two revisions prepares each,
     * takes the union of their {@link PreparedAnalysis#levelOneFiles()}, and completes each with it
     * through {@link PreparedAnalysis#compile(Collection)}. The caller must close it.
     *
     * @return The prepared analysis.
     * @throws IllegalStateException When this project's options model no boundary level.
     */
    public PreparedAnalysis prepare() throws CompileException {
        if (!this.options.modelsBoundary(this.analyzedFilePaths)) {
            throw new IllegalStateException("Only a one-level compile of named files can be prepared.");
        }
        return CompilerFactory.getParsingTool(this.lang)
                .prepare(this.projectFiles, this.analyzedFilePaths, this.options);
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
