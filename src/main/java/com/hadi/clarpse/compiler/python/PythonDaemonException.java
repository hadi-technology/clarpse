package com.hadi.clarpse.compiler.python;

public class PythonDaemonException extends Exception {

    public static final int CODE_NODE_NOT_FOUND = 1001;
    public static final int CODE_RESOLVER_START_FAILED = 1002;
    public static final int CODE_REPO_NOT_FOUND = 2001;
    public static final int CODE_FILE_NOT_FOUND = 2002;
    public static final int CODE_PARSE_FAILED = 2003;
    public static final int CODE_DAEMON_ERROR = 2004;
    public static final int CODE_FILE_EXCLUDED = 2005;

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
