package com.hadi.clarpse.compiler.java;

import java.util.List;
import java.util.function.Function;

/**
 * Resolves a Java import directive to the repository files it names exactly.
 *
 * <p>Every form names a type: a single-type import names the type, a static import names the type
 * whose member it imports (its last segment is dropped), and an on-demand import names the type
 * whose members it brings in. The files are those declaring the top-level type the name falls
 * under, so a nested type leads to the file of the type containing it. An on-demand import of a
 * package names no type and gives nothing, which is sound because a package and a type cannot
 * share a fully qualified name.
 *
 * <p>The project's Java files are consulted first. Only when none declares the type, the files of
 * the project's other JVM language are, so a Java file importing a Kotlin class, or the file class
 * a Kotlin file's top-level functions compile into, gets that Kotlin file. A name that neither
 * declares gives nothing.
 */
public final class JavaImportResolver {

    private final JavaDeclarationIndex index;
    private final Function<String, List<String>> otherLanguageFiles;

    /**
     * A resolver.
     *
     * @param index              The declarations of every Java file of the project.
     * @param otherLanguageFiles The files declaring the type a fully qualified name falls under in
     *                           the project's other JVM language; null for none.
     */
    public JavaImportResolver(final JavaDeclarationIndex index,
                              final Function<String, List<String>> otherLanguageFiles) {
        this.index = index;
        this.otherLanguageFiles = otherLanguageFiles;
    }

    /**
     * The repository files an import directive names exactly.
     *
     * @param name     The imported name; for an on-demand import the package or type, without
     *                 {@code .*}.
     * @param isStatic Whether the import is {@code import static}.
     * @param onDemand Whether the import is an on-demand ({@code .*}) import.
     * @return The files' paths, sorted; never null.
     */
    public List<String> filesImportedBy(final String name, final boolean isStatic, final boolean onDemand) {
        if (name == null || name.isEmpty()) {
            return List.of();
        }
        String typeName = name;
        if (isStatic && !onDemand) {
            final int dot = name.lastIndexOf('.');
            if (dot <= 0) {
                return List.of();
            }
            typeName = name.substring(0, dot);
        }
        final List<String> own = index.filesDeclaring(typeName);
        if (!own.isEmpty() || otherLanguageFiles == null) {
            return own;
        }
        return otherLanguageFiles.apply(typeName);
    }
}
