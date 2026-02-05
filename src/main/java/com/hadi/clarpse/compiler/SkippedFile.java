package com.hadi.clarpse.compiler;

import java.util.Objects;

/**
 * Represents a file that was skipped along with its reason.
 */
public final class SkippedFile {

    private final ProjectFile file;
    private final SkipReason reason;

    public SkippedFile(final ProjectFile file, final SkipReason reason) {
        this.file = Objects.requireNonNull(file, "file");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public ProjectFile file() {
        return file;
    }

    public SkipReason reason() {
        return reason;
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
        return file.path() + " (" + reason + ")";
    }
}
