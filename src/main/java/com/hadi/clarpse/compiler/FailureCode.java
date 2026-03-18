package com.hadi.clarpse.compiler;

/**
 * Language-agnostic failure codes returned in {@link CompileFailure#errorCode()}.
 */
public final class FailureCode {

    public static final int NODE_RUNTIME_NOT_FOUND = 1000;
    public static final int LANGUAGE_RUNTIME_NOT_FOUND = 1001;
    public static final int CONFIG_NOT_FOUND = 1002;
    public static final int CONFIG_INVALID = 1003;
    public static final int PROGRAM_INIT_FAILED = 1004;

    public static final int FILE_OUT_OF_SCOPE = 2001;
    public static final int FILE_NOT_FOUND = 2002;
    public static final int PARSE_FAILED = 2003;
    public static final int DAEMON_ERROR = 2004;
    public static final int FILE_EXCLUDED = 2005;

    private FailureCode() {
    }
}
