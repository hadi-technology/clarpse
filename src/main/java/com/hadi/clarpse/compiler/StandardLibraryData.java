package com.hadi.clarpse.compiler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Loads the data lists the standard-library rules read, shipped as resources under {@code /stdlib}.
 * A line starting with {@code #} is a comment that names the list's source and version.
 */
public final class StandardLibraryData {

    private StandardLibraryData() {
    }

    /**
     * The entries of a list.
     *
     * @param path The resource path, such as {@code /stdlib/java-packages.txt}.
     * @return The non-comment, non-blank lines, trimmed.
     * @throws IllegalStateException when the resource is missing or unreadable, since the list ships in the jar.
     */
    public static Set<String> lines(final String path) {
        final Set<String> lines = new HashSet<>();
        try (InputStream in = StandardLibraryData.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + path);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    if (!line.isBlank() && !line.startsWith("#")) {
                        lines.add(line.trim());
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read resource " + path, e);
        }
        return lines;
    }
}
