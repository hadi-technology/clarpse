package com.hadi.clarpse.compiler.python;

import com.hadi.clarpse.compiler.FailureCode;

/**
 * Exception type for Python daemon initialization and parse failures.
 */
public class PythonDaemonException extends Exception {

    public static final int CODE_NODE_NOT_FOUND = FailureCode.NODE_RUNTIME_NOT_FOUND;
    public static final int CODE_RESOLVER_START_FAILED = FailureCode.PROGRAM_INIT_FAILED;
    public static final int CODE_REPO_NOT_FOUND = FailureCode.PROGRAM_INIT_FAILED;
    public static final int CODE_FILE_NOT_FOUND = FailureCode.FILE_NOT_FOUND;
    public static final int CODE_PARSE_FAILED = FailureCode.PARSE_FAILED;
    public static final int CODE_DAEMON_ERROR = FailureCode.DAEMON_ERROR;
    public static final int CODE_FILE_EXCLUDED = FailureCode.FILE_EXCLUDED;

    private final int code;

    public PythonDaemonException(final String message) {
        this(message, 0, null);
    }

    public PythonDaemonException(final String message, final int code) {
        this(message, code, null);
    }

    public PythonDaemonException(final String message, final int code, final Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
