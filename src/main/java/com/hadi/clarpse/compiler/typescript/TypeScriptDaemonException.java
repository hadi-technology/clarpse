package com.hadi.clarpse.compiler.typescript;

public class TypeScriptDaemonException extends Exception {

    public static final int CODE_TYPESCRIPT_NOT_FOUND = 1001;
    public static final int CODE_NO_TSCONFIG = 1002;
    public static final int CODE_CONFIG_PARSE_FAILED = 1003;
    public static final int CODE_PROGRAM_CREATE_FAILED = 1004;
    public static final int CODE_FILE_NOT_IN_PROGRAM = 2001;
    public static final int CODE_FILE_NOT_FOUND = 2002;

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
