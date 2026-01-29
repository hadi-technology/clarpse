package com.hadi.clarpse.compiler;

/**
 * A compile exception encountered during the parsing of source code.
 */
public class CompileException  extends Exception {
    public CompileException(String errorMessage) {
        super(errorMessage);
    }

    public CompileException(String errorMessage, Exception e) {
        super(errorMessage, e);
    }
}
