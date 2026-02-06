package com.hadi.clarpse.compiler;

import java.util.Objects;

/**
 * Represents a file that failed to compile along with a message.
 */
public final class CompileFailure {

    private final ProjectFile file;
    private final String message;
    private final Integer errorCode;

    public CompileFailure(final ProjectFile file, final String message) {
        this(file, message, null);
    }

    public CompileFailure(final ProjectFile file, final String message, final Integer errorCode) {
        this.file = Objects.requireNonNull(file, "file");
        String safeMessage = message;
        if (safeMessage == null) {
            safeMessage = "";
        }
        this.message = safeMessage;
        this.errorCode = errorCode;
    }

    public ProjectFile file() {
        return file;
    }

    public String message() {
        return message;
    }

    public Integer errorCode() {
        return errorCode;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final CompileFailure other = (CompileFailure) obj;
        return file.equals(other.file) && Objects.equals(message, other.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(file, message);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(file.path());
        if (message != null && !message.isEmpty()) {
            sb.append(" (").append(message);
            if (errorCode != null) {
                sb.append(", code=").append(errorCode);
            }
            sb.append(")");
        }
        return sb.toString();
    }
}
