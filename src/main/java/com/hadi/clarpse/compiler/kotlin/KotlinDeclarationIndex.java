package com.hadi.clarpse.compiler.kotlin;

import com.hadi.clarpse.compiler.java.JavaDeclarationIndex;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinFileModel;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinImport;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinMemberModel;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinTypeAlias;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinTypeModel;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.TypeKind;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Every type the repository declares on the JVM, by fully qualified name: the Kotlin types and file
 * classes of every Kotlin file, and the Java types of every Java file. It is what a name in a
 * Kotlin file is resolved against, so a Kotlin file resolves a Java type of its package, or of a
 * package it star-imports, exactly as it resolves a Kotlin one.
 *
 * <p>It also maps each top-level Kotlin function and property to the file class that holds it on
 * the JVM, and each {@code typealias} to what it aliases, and answers which files declare a name
 * and which Kotlin files an import directive resolves to.
 */
final class KotlinDeclarationIndex {

    /**
     * A Kotlin type declaration, where it is, and what kind it is.
     *
     * @param uniqueName The fully qualified name.
     * @param path       The declaring file's path.
     * @param kind       The kind of declaration.
     * @param type       The declaration, or null for the file class of top-level members.
     * @param file       The declaring file, or null for a file class.
     * @param enclosing  The unique names of the type and the types it is nested in, innermost first.
     */
    record KotlinDeclaration(String uniqueName, String path, TypeKind kind, KotlinTypeModel type,
                             KotlinFileModel file, List<String> enclosing) {
    }

    /** A top-level function or property, the file class that holds it, and its modifiers. */
    private record CallableDeclaration(String fileClass, List<String> modifiers) {
    }

    /** A {@code typealias} and the file it is declared in, whose scope its target is written in. */
    record AliasDeclaration(KotlinTypeAlias alias, KotlinFileModel file) {
    }

    /** A top-level function, the file class that holds it, and the file that declares it. */
    record TopLevelFunction(String fileClass, KotlinMemberModel function, KotlinFileModel file) {
    }

    private final Map<String, List<TopLevelFunction>> topLevelFunctions = new HashMap<>();
    private final Set<String> packages = new HashSet<>();
    private final Map<String, List<KotlinDeclaration>> kotlinTypes = new HashMap<>();
    private final Map<String, String> fileClassByCallable = new HashMap<>();
    private final Map<String, List<CallableDeclaration>> declarationsByCallable = new HashMap<>();
    private final Map<String, AliasDeclaration> aliases = new HashMap<>();
    private final Map<String, Set<String>> filesByCallable = new HashMap<>();
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
            for (final KotlinMemberModel member : file.topLevelMembers) {
                if (member.name != null && !member.name.isEmpty()) {
                    filesByCallable.computeIfAbsent(qualify(file.packageName, member.name),
                            key -> new LinkedHashSet<>()).add(file.path());
                }
            }
            packages.add(file.packageName);
            for (final KotlinTypeModel type : file.types) {
                addType(type, qualify(file.packageName, type.name), file, List.of());
            }
            final String fileClass = fileClassName(file);
            if (fileClass != null) {
                addDeclaration(new KotlinDeclaration(fileClass, file.path(), TypeKind.CLASS, null, null, List.of()));
                for (final KotlinMemberModel member : file.topLevelMembers) {
                    if (member.name != null && !member.name.isEmpty()) {
                        declarationsByCallable.computeIfAbsent(qualify(file.packageName, member.name),
                                key -> new ArrayList<>()).add(new CallableDeclaration(fileClass, member.modifiers));
                        if (member.kind == KotlinModel.MemberKind.FUNCTION) {
                            topLevelFunctions.computeIfAbsent(qualify(file.packageName, member.name),
                                    key -> new ArrayList<>()).add(new TopLevelFunction(fileClass, member, file));
                        }
                    }
                }
            }
            for (final KotlinTypeAlias alias : file.typeAliases) {
                aliases.putIfAbsent(qualify(file.packageName, alias.name()), new AliasDeclaration(alias, file));
            }
        }
        kotlinTypes.values().forEach(list -> list.sort((left, right) -> left.path().compareTo(right.path())));
        declarationsByCallable.forEach((callable, declarations) -> {
            final String fileClass = declaringFileClass(declarations);
            if (fileClass != null) {
                fileClassByCallable.put(callable, fileClass);
            }
        });
    }

    /**
     * The file class a call to a name binds to, given every top-level declaration of the name in path
     * order. A declaration alone is the one meant. Of several, the {@code expect} declaration is
     * what common code resolves to; when all of them are {@code actual} and no {@code expect} is in
     * the project, none is the one meant and the call binds to none. Declarations that are neither
     * (overloads in several files) keep the first in path order.
     */
    private static String declaringFileClass(final List<CallableDeclaration> declarations) {
        for (final CallableDeclaration declaration : declarations) {
            if (declaration.modifiers().contains("expect")) {
                return declaration.fileClass();
            }
        }
        final boolean allActual = declarations.stream().allMatch(
                declaration -> declaration.modifiers().contains("actual"));
        if (allActual && declarations.stream().map(CallableDeclaration::fileClass).distinct().count() > 1) {
            return null;
        }
        return declarations.get(0).fileClass();
    }

    private void addType(final KotlinTypeModel type, final String uniqueName, final KotlinFileModel file,
                         final List<String> outer) {
        if (type.name == null || type.name.isEmpty()) {
            return;
        }
        final List<String> enclosing = new ArrayList<>();
        enclosing.add(uniqueName);
        enclosing.addAll(outer);
        addDeclaration(new KotlinDeclaration(uniqueName, file.path(), type.kind, type, file,
                List.copyOf(enclosing)));
        for (final KotlinTypeModel nested : type.nestedTypes) {
            addType(nested, uniqueName + "." + nested.name, file, enclosing);
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
     * Whether a Kotlin file of the repository declares this package.
     *
     * @param packageName A package name.
     * @return Whether some Kotlin file's package is exactly that.
     */
    boolean declaresPackage(final String packageName) {
        return packages.contains(packageName);
    }

    /**
     * The one Kotlin declaration of a type.
     *
     * @param uniqueName A fully qualified name.
     * @return The declaration, or null when no Kotlin file declares the name as a type, or several do.
     */
    KotlinDeclaration declaration(final String uniqueName) {
        final List<KotlinDeclaration> declarations = kotlinTypes.get(uniqueName);
        if (declarations == null || declarations.size() != 1 || declarations.get(0).type() == null) {
            return null;
        }
        return declarations.get(0);
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
     * The top-level functions of a name, extensions included.
     *
     * @param callable The functions' fully qualified name.
     * @return Every declaration of it, in path order; empty when no Kotlin file declares one.
     */
    List<TopLevelFunction> functionsNamed(final String callable) {
        return topLevelFunctions.getOrDefault(callable, List.of());
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
     * The Kotlin files an import directive resolves to.
     *
     * <p>A name import gives the files declaring a type, a {@code typealias} or a top-level
     * function or property of that name: every file that does, since overloads and a type and a
     * factory function can share a name. A name that matches none is read as a member of a type
     * ({@code Obj.member}, {@code Color.RED}, a nested type) and gives the files declaring the
     * nearest enclosing type. A star import of a package names no file and gives nothing; one
     * whose name is a type gives the files declaring it. An alias changes the name the file sees, never the
     * target. A name no Kotlin file declares is looked up among the
     * project's Java files, in the same way; a library or unresolved name gives nothing.
     *
     * @param directive The import directive.
     * @return The files' paths, each directive's files sorted by path; never null.
     */
    Set<String> filesImportedBy(final KotlinImport directive) {
        final Set<String> found = new LinkedHashSet<>();
        final String target = directive.target();
        if (target == null || target.isEmpty()) {
            return found;
        }
        addDeclaringFiles(target, found);
        if (directive.star()) {
            if (found.isEmpty() && javaIndex != null) {
                found.addAll(javaIndex.filesDeclaring(target));
            }
            return found;
        }
        found.addAll(filesByCallable.getOrDefault(target, Set.of()));
        final AliasDeclaration alias = aliases.get(target);
        if (alias != null) {
            found.add(alias.file().path());
        }
        if (found.isEmpty()) {
            final int dot = target.lastIndexOf('.');
            if (dot > 0) {
                found.addAll(kotlinFilesDeclaring(target.substring(0, dot)));
            }
        }
        if (found.isEmpty() && javaIndex != null) {
            found.addAll(javaIndex.filesDeclaring(target));
        }
        return found;
    }

    private void addDeclaringFiles(final String uniqueName, final Set<String> into) {
        final List<KotlinDeclaration> declarations = kotlinTypes.get(uniqueName);
        if (declarations != null) {
            declarations.forEach(declaration -> into.add(declaration.path()));
        }
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
