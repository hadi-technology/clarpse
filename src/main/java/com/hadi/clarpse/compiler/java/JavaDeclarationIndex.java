package com.hadi.clarpse.compiler.java;

import com.hadi.clarpse.compiler.ProjectFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps every top-level type name the repository's Java files declare to the files declaring it,
 * read from source text without parsing.
 *
 * <p>A file contributes its package plus its file name, since a public type must be declared in a
 * file of its own name, and its package plus every type declared at the start of a line, which
 * covers package-private types declared in a file of another name. Nested types are not indexed:
 * a name is looked up by trying ever shorter prefixes of it, so {@code a.B.C} finds the file
 * declaring {@code a.B}.
 *
 * <p>A name declared by several files maps to all of them, sorted by path, so a lookup is
 * deterministic.
 */
public final class JavaDeclarationIndex {

    private static final Pattern PACKAGE_DECLARATION =
            Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");

    private static final Pattern TOP_LEVEL_DECLARATION = Pattern.compile(
            "(?m)^(?:(?:public|protected|private|abstract|final|static|sealed|non-sealed|strictfp)\\s+)*"
                    + "(?:class|interface|enum|record|@interface)\\s+([A-Za-z_$][\\w$]*)");

    private final Map<String, List<String>> filesByName;

    private JavaDeclarationIndex(final Map<String, List<String>> filesByName) {
        this.filesByName = filesByName;
    }

    /**
     * Indexes the given files.
     *
     * @param files The repository's Java files.
     * @return The index.
     */
    public static JavaDeclarationIndex of(final Collection<ProjectFile> files) {
        final Map<String, List<String>> index = new HashMap<>();
        for (final ProjectFile file : files) {
            final String path = file.path();
            final String content = file.content();
            if (path == null || content == null) {
                continue;
            }
            String packagePrefix = "";
            final Matcher packageMatcher = PACKAGE_DECLARATION.matcher(content);
            if (packageMatcher.find()) {
                packagePrefix = packageMatcher.group(1) + ".";
            }
            final Set<String> names = new LinkedHashSet<>();
            names.add(fileBaseName(path));
            final Matcher declarations = TOP_LEVEL_DECLARATION.matcher(content);
            while (declarations.find()) {
                names.add(declarations.group(1));
            }
            for (final String name : names) {
                index.computeIfAbsent(packagePrefix + name, key -> new ArrayList<>()).add(path);
            }
        }
        index.values().forEach(Collections::sort);
        return new JavaDeclarationIndex(index);
    }

    private static String fileBaseName(final String path) {
        String name = path;
        final int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.endsWith(".java")) {
            name = name.substring(0, name.length() - ".java".length());
        }
        return name;
    }

    /**
     * The indexed top-level name a qualified name falls under: the longest prefix of it that the
     * index holds.
     *
     * @param qualifiedName A fully qualified type name, possibly of a nested type.
     * @return The indexed prefix, or {@code null} when the repository declares no such type.
     */
    public String topLevelName(final String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isEmpty()) {
            return null;
        }
        String candidate = qualifiedName;
        while (true) {
            if (filesByName.containsKey(candidate)) {
                return candidate;
            }
            final int dot = candidate.lastIndexOf('.');
            if (dot <= 0) {
                return null;
            }
            candidate = candidate.substring(0, dot);
        }
    }

    /**
     * The files declaring the type a qualified name falls under.
     *
     * @param qualifiedName A fully qualified type name, possibly of a nested type.
     * @return The declaring files' paths, sorted; empty when the repository declares no such type.
     */
    public List<String> filesDeclaring(final String qualifiedName) {
        final String topLevel = topLevelName(qualifiedName);
        if (topLevel == null) {
            return List.of();
        }
        return Collections.unmodifiableList(filesByName.get(topLevel));
    }

    /**
     * Whether the repository declares the type a qualified name falls under.
     *
     * @param qualifiedName A fully qualified type name.
     * @return {@code true} when some file declares it.
     */
    public boolean declares(final String qualifiedName) {
        return topLevelName(qualifiedName) != null;
    }

    /**
     * The files declaring an exact top-level name, without prefix lookup.
     *
     * @param topLevelName A fully qualified top-level type name.
     * @return The declaring files' paths, sorted; empty when none.
     */
    List<String> filesDeclaringExactly(final String topLevelName) {
        return filesByName.getOrDefault(topLevelName, List.of());
    }
}
