package com.hadi.clarpse.compiler;

/**
 * Structured reasons for skipping a file during compilation.
 */
public enum SkipReason {
    NODE_NOT_FOUND,
    TYPESCRIPT_NOT_FOUND,
    NO_TSCONFIG,
    CONFIG_PARSE_FAILED,
    PROGRAM_CREATE_FAILED,
    FILE_NOT_IN_PROGRAM,
    FILE_NOT_FOUND,
    RESOLVER_START_FAILED
}
