package com.hadi.clarpse.compiler.kotlin;

import com.hadi.clarpse.compiler.java.JavaDeclarationIndex;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinFileModel;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinMemberModel;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinTypeAlias;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinTypeModel;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.TypeKind;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Every type the repository declares on the JVM, by fully qualified name: the Kotlin types and file
 * classes of every Kotlin file, and the Java types of every Java file. It is what a name in a
 * Kotlin file is resolved against, so a Kotlin file resolves a Java type of its package, or of a
 * package it star-imports, exactly as it resolves a Kotlin one.
 *
 * <p>It also maps each top-level Kotlin function and property to the file class that holds it on
 * the JVM, and each {@code typealias} to what it aliases, and answers which files declare a name.
 */
final class KotlinDeclarationIndex {

    /** A Kotlin type declaration, where it is, and what kind it is. */
    record KotlinDeclaration(String uniqueName, String path, TypeKind kind) {
    }

    /** A {@code typealias} and the file it is declared in, whose scope its target is written in. */
    record AliasDeclaration(KotlinTypeAlias alias, KotlinFileModel file) {
    }

    private final Map<String, List<KotlinDeclaration>> kotlinTypes = new HashMap<>();
    private final Map<String, String> fileClassByCallable = new HashMap<>();
    private final Map<String, AliasDeclaration> aliases = new HashMap<>();
    private final JavaDeclarationIndex javaIndex;

    /**
     * Indexes the given files.
     *
     * @param kotlinFiles The models of every Kotlin file, bodies or not.
     * @param javaIndex   The repository's Java declarations, or null when it has no Java files.
     */
    KotlinDeclarationIndex(final Collection<KotlinFileModel> kotlinFiles, final JavaDeclarationIndex javaIndex) {
        this.javaIndex = javaIndex;
        // In path order, so that when several files declare one name -- an expect function and its
        // actuals -- the same file wins whatever order the files were read in.
        final List<KotlinFileModel> ordered = new ArrayList<>(kotlinFiles);
        ordered.sort(Comparator.comparing(KotlinFileModel::path));
        for (final KotlinFileModel file : ordered) {
            for (final KotlinTypeModel type : file.types) {
                addType(type, qualify(file.packageName, type.name), file.path());
            }
            final String fileClass = fileClassName(file);
            if (fileClass != null) {
                addDeclaration(new KotlinDeclaration(fileClass, file.path(), TypeKind.CLASS));
                for (final KotlinMemberModel member : file.topLevelMembers) {
                    if (member.name != null && !member.name.isEmpty()) {
                        fileClassByCallable.putIfAbsent(qualify(file.packageName, member.name), fileClass);
                    }
                }
            }
            for (final KotlinTypeAlias alias : file.typeAliases) {
                aliases.putIfAbsent(qualify(file.packageName, alias.name()), new AliasDeclaration(alias, file));
            }
        }
        kotlinTypes.values().forEach(list -> list.sort((left, right) -> left.path().compareTo(right.path())));
    }

    private void addType(final KotlinTypeModel type, final String uniqueName, final String path) {
        if (type.name == null || type.name.isEmpty()) {
            return;
        }
        addDeclaration(new KotlinDeclaration(uniqueName, path, type.kind));
        for (final KotlinTypeModel nested : type.nestedTypes) {
            addType(nested, uniqueName + "." + nested.name, path);
        }
    }

    private void addDeclaration(final KotlinDeclaration declaration) {
        kotlinTypes.computeIfAbsent(declaration.uniqueName(), key -> new ArrayList<>()).add(declaration);
    }

    /**
     * The fully qualified name of the class a file's top-level functions and properties compile
     * into: the {@code @file:JvmName} if one is given, otherwise the file name with its first
     * letter capitalised and {@code Kt} appended. A file declaring no top-level function or
     * property has none.
     *
     * @param file The file.
     * @return The name, or null.
     */
    static String fileClassName(final KotlinFileModel file) {
        if (file.topLevelMembers.isEmpty()) {
            return null;
        }
        String simpleName = file.jvmName;
        if (simpleName == null || simpleName.isEmpty()) {
            simpleName = defaultFileClassSimpleName(file.moduleName);
        }
        return qualify(file.packageName, simpleName);
    }

    /** The JVM's file class name for a file name without its extension: {@code fooBar} gives {@code FooBarKt}. */
    static String defaultFileClassSimpleName(final String fileBaseName) {
        final StringBuilder name = new StringBuilder();
        for (int i = 0; i < fileBaseName.length(); i += 1) {
            final char ch = fileBaseName.charAt(i);
            if (Character.isJavaIdentifierPart(ch)) {
                name.append(ch);
            } else {
                name.append('_');
            }
        }
        if (name.length() == 0) {
            return "Kt";
        }
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            name.insert(0, '_');
        }
        name.setCharAt(0, Character.toUpperCase(name.charAt(0)));
        return name + "Kt";
    }

    static String qualify(final String packageName, final String name) {
        if (packageName == null || packageName.isEmpty()) {
            return name;
        }
        return packageName + "." + name;
    }

    /**
     * Whether the repository declares a type of exactly this name. A Java nested type is known only
     * through its top-level type, so a name below a Java type counts only when every part past the
     * top-level type reads as a type name rather than as a constant.
     *
     * @param uniqueName A fully qualified name.
     * @return Whether it names a repository type.
     */
    boolean isType(final String uniqueName) {
        if (uniqueName == null || uniqueName.isEmpty()) {
            return false;
        }
        if (kotlinTypes.containsKey(uniqueName)) {
            return true;
        }
        if (javaIndex == null) {
            return false;
        }
        final String topLevel = javaIndex.topLevelName(uniqueName);
        if (topLevel == null) {
            return false;
        }
        if (topLevel.length() == uniqueName.length()) {
            return true;
        }
        for (final String part : uniqueName.substring(topLevel.length() + 1).split("\\.")) {
            if (!readsAsTypeName(part)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a name is spelled like a type: a capital first letter and some lower case after it. */
    static boolean readsAsTypeName(final String name) {
        if (name == null || name.isEmpty() || !Character.isUpperCase(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); i += 1) {
            if (Character.isLowerCase(name.charAt(i))) {
                return true;
            }
        }
        return name.length() == 1;
    }

    /**
     * The kind of a Kotlin type the repository declares.
     *
     * @param uniqueName A fully qualified name.
     * @return The kind, or null when no Kotlin file declares the name.
     */
    TypeKind kotlinKind(final String uniqueName) {
        final List<KotlinDeclaration> declarations = kotlinTypes.get(uniqueName);
        if (declarations == null || declarations.isEmpty()) {
            return null;
        }
        return declarations.get(0).kind();
    }

    /**
     * The file class holding a top-level function or property.
     *
     * @param callable The function's or property's fully qualified name.
     * @return The file class's fully qualified name, or null when no Kotlin file declares it.
     */
    String fileClassOf(final String callable) {
        return fileClassByCallable.get(callable);
    }

    /**
     * The {@code typealias} of this name.
     *
     * @param uniqueName A fully qualified name.
     * @return The alias and its file, or null.
     */
    AliasDeclaration alias(final String uniqueName) {
        return aliases.get(uniqueName);
    }

    /**
     * The Kotlin files declaring the type a qualified name falls under, or the file class it names.
     *
     * @param uniqueName A fully qualified name, possibly of a nested type.
     * @return Their paths, sorted; empty when no Kotlin file declares it.
     */
    List<String> kotlinFilesDeclaring(final String uniqueName) {
        String candidate = uniqueName;
        while (candidate != null && !candidate.isEmpty()) {
            final List<KotlinDeclaration> declarations = kotlinTypes.get(candidate);
            if (declarations != null) {
                final TreeSet<String> paths = new TreeSet<>();
                declarations.forEach(declaration -> paths.add(declaration.path()));
                return List.copyOf(paths);
            }
            final int dot = candidate.lastIndexOf('.');
            if (dot <= 0) {
                break;
            }
            candidate = candidate.substring(0, dot);
        }
        return Collections.emptyList();
    }

    /**
     * Whether the repository declares, in a Kotlin or a Java file, the type a reference names.
     *
     * @param uniqueName A fully qualified name.
     * @return Whether some file declares it.
     */
    boolean declares(final String uniqueName) {
        return !kotlinFilesDeclaring(uniqueName).isEmpty()
                || (javaIndex != null && javaIndex.declares(uniqueName));
    }
}
