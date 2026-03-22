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
    private CompileResult compileResult;

    public ClarpseProject(ProjectFiles pfs, Lang lang) {
        this(pfs, lang, null);
    }

    public ClarpseProject(final ProjectFiles pfs,
                          final Lang lang,
                          final Collection<String> analyzedFilePaths) {
        validateInput(lang);
        this.projectFiles = pfs;
        this.lang = lang;
        if (analyzedFilePaths == null) {
            this.analyzedFilePaths = null;
        } else {
            this.analyzedFilePaths = List.copyOf(analyzedFilePaths);
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
}
