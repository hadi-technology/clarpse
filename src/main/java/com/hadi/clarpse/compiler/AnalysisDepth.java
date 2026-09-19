package com.hadi.clarpse.compiler;

/**
 * How far past the analysed files a compile models the codebase.
 */
public enum AnalysisDepth {

    /**
     * The analysed files only. References that leave them resolve against whatever files the
     * language's resolver can see, and the model holds components for the analysed files alone.
     */
    FULL,

    /**
     * The analysed files, plus the in-repository files that declare something the analysed files
     * reference. See {@code docs/one-level-analysis.md} for what the model then promises.
     */
    ONE_LEVEL
}
