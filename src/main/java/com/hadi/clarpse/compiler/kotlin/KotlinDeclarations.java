package com.hadi.clarpse.compiler.kotlin;

import com.hadi.clarpse.compiler.CompileException;
import com.hadi.clarpse.compiler.ProjectFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * The types a project's Kotlin files declare on the JVM, by fully qualified name: their classes,
 * interfaces, objects and nested types, and the file classes holding their top-level functions and
 * properties. It lets another JVM language's compiler tell a name that a Kotlin file declares from
 * one nothing in the repository declares. Files are read without their function bodies, and a file
 * that cannot be parsed contributes nothing.
 *
 * <p>Files are grouped by the package their header declares and read the first time a question
 * could concern that package, so a project whose other files never name a Kotlin type reads none.
 * A declaration's name always begins with its file's package, so a name is answered from the files
 * of the root package and of every package its name begins with. A file whose header cannot be read
 * with certainty is read on the first question of any kind. Every answer is the one reading every
 * file up front would give. Instances are safe for use by concurrent threads.
 */
public final class KotlinDeclarations {

    private static final String UNREAD_HEADERS = "\u0000unread";

    private final Map<String, List<ProjectFile>> filesByPackage;
    private final Map<String, Group> groups = new ConcurrentHashMap<>();

    private KotlinDeclarations(final Map<String, List<ProjectFile>> filesByPackage) {
        this.filesByPackage = filesByPackage;
    }

    /**
     * The declarations of the given Kotlin files, each package's read when first needed.
     *
     * @param kotlinFiles The project's Kotlin files.
     * @return Their declarations.
     */
    public static KotlinDeclarations of(final Collection<ProjectFile> kotlinFiles) {
        final Map<String, List<ProjectFile>> byPackage = new HashMap<>();
        for (final ProjectFile file : kotlinFiles) {
            String packageName = KotlinPackageHeader.of(file.content());
            if (packageName == null) {
                packageName = UNREAD_HEADERS;
            }
            byPackage.computeIfAbsent(packageName, key -> new ArrayList<>()).add(file);
        }
        return new KotlinDeclarations(byPackage);
    }

    /**
     * Whether a Kotlin file declares a type of exactly this name.
     *
     * @param uniqueName A fully qualified name.
     * @return Whether it names a Kotlin type or file class.
     * @throws IllegalStateException When reading the Kotlin files is interrupted or fails unexpectedly.
     */
    public boolean declaresType(final String uniqueName) {
        return anyCandidate(uniqueName, index -> index.isType(uniqueName));
    }

    /**
     * Whether a Kotlin file declares the type a reference names, or the type it is nested in.
     *
     * @param uniqueName A fully qualified name, possibly of a nested type.
     * @return Whether some Kotlin file declares it.
     * @throws IllegalStateException When reading the Kotlin files is interrupted or fails unexpectedly.
     */
    public boolean declares(final String uniqueName) {
        return anyCandidate(uniqueName, index -> !index.kotlinFilesDeclaring(uniqueName).isEmpty());
    }

    /**
     * Why reading some package's files failed, if one did. A question that met the failure threw
     * {@link IllegalStateException}; a caller that cannot see where its questions went asks this
     * once it is done, so the failure is not mistaken for no Kotlin file declaring the name.
     *
     * @return The first failure, or null when every package read so far was read.
     */
    public CompileException failure() {
        for (final Group group : groups.values()) {
            final CompileException failure = group.failure();
            if (failure != null) {
                return failure;
            }
        }
        return null;
    }

    /** The packages whose files have been read, the files of unread headers counted under null. */
    Set<String> packagesRead() {
        final Set<String> read = new HashSet<>();
        groups.forEach((packageName, group) -> {
            if (group.isRead()) {
                read.add(UNREAD_HEADERS.equals(packageName) ? null : packageName);
            }
        });
        return read;
    }

    /**
     * Whether the question holds of the files of any package a declaration of this name could be
     * in: the root package, each package the name begins with, and the files of unread headers.
     */
    private boolean anyCandidate(final String uniqueName, final Predicate<KotlinDeclarationIndex> question) {
        if (uniqueName == null || uniqueName.isEmpty()) {
            return false;
        }
        if (answers(UNREAD_HEADERS, question) || answers("", question)) {
            return true;
        }
        int dot = uniqueName.indexOf('.');
        while (dot > 0) {
            if (answers(uniqueName.substring(0, dot), question)) {
                return true;
            }
            dot = uniqueName.indexOf('.', dot + 1);
        }
        return false;
    }

    private boolean answers(final String packageName, final Predicate<KotlinDeclarationIndex> question) {
        final List<ProjectFile> files = filesByPackage.get(packageName);
        if (files == null) {
            return false;
        }
        return question.test(groups.computeIfAbsent(packageName, key -> new Group(files)).index());
    }

    /** One package's files, read once, by whichever thread asks first. */
    private static final class Group {

        private final List<ProjectFile> files;
        private KotlinDeclarationIndex index;
        private CompileException failure;

        Group(final List<ProjectFile> files) {
            this.files = files;
        }

        synchronized KotlinDeclarationIndex index() {
            if (index == null) {
                try {
                    index = new KotlinDeclarationIndex(ClarpseKotlinCompiler.models(
                            ClarpseKotlinCompiler.parseFiles(files, false), new HashSet<>()), null);
                } catch (final CompileException e) {
                    failure = e;
                    throw new IllegalStateException("Could not read the declarations of Kotlin files.", e);
                }
            }
            return index;
        }

        synchronized boolean isRead() {
            return index != null || failure != null;
        }

        synchronized CompileException failure() {
            return failure;
        }
    }
}
