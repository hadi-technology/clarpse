package com.hadi.clarpse.compiler;

import java.util.Objects;

/**
 * Represents a file that was skipped along with its reason.
 */
public final class SkippedFile {

    private final ProjectFile file;
    private final SkipReason reason;
    private final String detail;
    private final Integer errorCode;

    public SkippedFile(final ProjectFile file, final SkipReason reason) {
        this(file, reason, null, null);
    }

    public SkippedFile(final ProjectFile file, final SkipReason reason, final String detail, final Integer errorCode) {
        this.file = Objects.requireNonNull(file, "file");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.detail = detail;
        this.errorCode = errorCode;
    }

    public ProjectFile file() {
        return file;
    }

    public SkipReason reason() {
        return reason;
    }

    public String detail() {
        return detail;
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
        final SkippedFile other = (SkippedFile) obj;
        return file.equals(other.file) && reason == other.reason;
    }

    @Override
    public int hashCode() {
        return Objects.hash(file, reason);
    }

    @Override
    public String toString() {
        if (detail == null && errorCode == null) {
            return file.path() + " (" + reason + ")";
        }
        StringBuilder sb = new StringBuilder(file.path())
                .append(" (")
                .append(reason);
        if (errorCode != null) {
            sb.append(", code=").append(errorCode);
        }
        if (detail != null) {
            sb.append(", detail=").append(detail);
        }
        sb.append(")");
        return sb.toString();
    }
}
