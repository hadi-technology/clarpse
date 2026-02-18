package com.hadi.clarpse.compiler.typescript;

import com.hadi.clarpse.compiler.FailureCode;

public class TypeScriptDaemonException extends Exception {

    public static final int CODE_NODE_NOT_FOUND = FailureCode.NODE_RUNTIME_NOT_FOUND;
    public static final int CODE_TYPESCRIPT_NOT_FOUND = FailureCode.LANGUAGE_RUNTIME_NOT_FOUND;
    public static final int CODE_NO_TSCONFIG = FailureCode.CONFIG_NOT_FOUND;
    public static final int CODE_CONFIG_PARSE_FAILED = FailureCode.CONFIG_INVALID;
    public static final int CODE_PROGRAM_CREATE_FAILED = FailureCode.PROGRAM_INIT_FAILED;
    public static final int CODE_FILE_NOT_IN_PROGRAM = FailureCode.FILE_OUT_OF_SCOPE;
    public static final int CODE_FILE_NOT_FOUND = FailureCode.FILE_NOT_FOUND;
    public static final int CODE_DAEMON_ERROR = FailureCode.DAEMON_ERROR;

    private final int code;

    public TypeScriptDaemonException(final String message) {
        super(message);
        this.code = 0;
    }

    public TypeScriptDaemonException(final String message, final int code) {
        super(message);
        this.code = code;
    }

    public TypeScriptDaemonException(final String message, final int code, final Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
