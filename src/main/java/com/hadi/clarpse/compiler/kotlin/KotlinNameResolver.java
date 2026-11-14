package com.hadi.clarpse.compiler.kotlin;

import com.hadi.clarpse.compiler.kotlin.KotlinDeclarationIndex.AliasDeclaration;
import com.hadi.clarpse.compiler.kotlin.KotlinDeclarationIndex.TopLevelFunction;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinFileModel;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinImport;
import com.hadi.clarpse.reference.ResolutionKind;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a name written in a Kotlin file to the fully qualified name of the type it denotes, in
 * the order Kotlin looks a name up: an enclosing type or a type nested in one, an explicit import
 * or import alias, a type of the file's own package, a star import, and last the packages every
 * Kotlin file imports by default ({@code kotlin.*}, {@code kotlin.collections.*} and the rest).
 *
 * <p>Repository types are those of the {@link KotlinDeclarationIndex}, so a Java type resolves
 * like a Kotlin one. A default import is consulted only after everything in scope, so a type of
 * the file's package or a star-imported one shadows the {@code kotlin} type of the same name, and a
 * repository type of the same name declared anywhere else does not.
 *
 * <p>Nothing is guessed. A name in a type position that nothing in scope declares is kept as
 * written and marked {@link ResolutionKind#UNRESOLVED}. A name in an expression -- a call, a
 * receiver, a class literal -- is only taken for a type when it resolves to one, because there it
 * is as likely to name a function, a property or a variable.
 */
final class KotlinNameResolver {

    /** A resolved name, and the evidence that settled it. */
    record Resolution(String name, ResolutionKind kind) {
    }

    /** Where a name is written: the file, the enclosing types, and the type parameters in scope. */
    record Scope(KotlinFileModel file, List<String> enclosingTypes, Set<String> typeParameters) {
    }

    private static final int MAX_ALIAS_DEPTH = 8;

    /** Whether the platform declares a public top-level type of a name, by name. */
    private static final Map<String, Boolean> PLATFORM_TYPES = new ConcurrentHashMap<>();

    /** The first segments of the packages libraries and platforms publish under. */
    private static final Set<String> PACKAGE_ROOTS = Set.of("com", "org", "net", "io", "edu", "gov", "java",
            "javax", "jakarta", "android", "androidx", "kotlin", "kotlinx", "scala", "sun", "jdk", "de", "fr",
            "uk", "nl", "ch", "se", "cn", "jp", "ru", "br", "in", "co", "dev", "app");

    /** The types Kotlin's default imports bring into every file, by simple name. */
    private static final Map<String, String> DEFAULT_IMPORTS = defaultImports();

    private final KotlinDeclarationIndex index;

    KotlinNameResolver(final KotlinDeclarationIndex index) {
        this.index = index;
    }

    KotlinDeclarationIndex index() {
        return index;
    }

    /**
     * Resolves a written type name.
     *
     * @param written    The name, qualifier included and type arguments excluded.
     * @param scope      Where it is written.
     * @param expression Whether it is written in an expression rather than a type position.
     * @return The resolution, or null when the name is not taken for a type.
     */
    Resolution resolve(final String written, final Scope scope, final boolean expression) {
        return resolve(written, scope, expression, 0);
    }

    private Resolution resolve(final String written, final Scope scope, final boolean expression, final int depth) {
        if (written == null || written.isBlank() || depth > MAX_ALIAS_DEPTH) {
            return null;
        }
        final String name = written.trim();
        final int dot = name.indexOf('.');
        final String head;
        final String rest;
        if (dot < 0) {
            head = name;
            rest = "";
        } else {
            head = name.substring(0, dot);
            rest = name.substring(dot + 1);
        }
        if (scope.typeParameters().contains(head)) {
            return null;
        }
        final Resolution resolvedHead = resolveSimple(head, scope, expression || !rest.isEmpty(), depth);
        if (rest.isEmpty()) {
            if (resolvedHead != null) {
                if (expression && resolvedHead.kind() != ResolutionKind.EXACT) {
                    return null;
                }
                return resolvedHead;
            }
            if (expression) {
                return null;
            }
            return new Resolution(name, ResolutionKind.UNRESOLVED);
        }
        if (resolvedHead != null && resolvedHead.kind() == ResolutionKind.EXACT) {
            final String candidate = resolvedHead.name() + "." + rest;
            if (index.isType(candidate)) {
                return new Resolution(candidate, ResolutionKind.EXACT);
            }
            if (!expression || (!index.isType(resolvedHead.name()) && allReadAsTypeNames(rest))) {
                return new Resolution(candidate, ResolutionKind.EXACT);
            }
            return null;
        }
        // The qualifier is no type in scope, so the name is written in full: a package and a type.
        if (index.isType(name)) {
            return new Resolution(name, ResolutionKind.EXACT);
        }
        final AliasDeclaration alias = index.alias(name);
        if (alias != null) {
            return expandAlias(alias, expression, depth);
        }
        if (!expression || readsAsQualifiedTypeName(name)) {
            return new Resolution(name, ResolutionKind.EXACT);
        }
        return null;
    }

    /** Resolves a simple name, or returns null when nothing in scope names a type by it. */
    private Resolution resolveSimple(final String name, final Scope scope, final boolean expression,
                                     final int depth) {
        final KotlinFileModel file = scope.file();
        for (final String enclosing : scope.enclosingTypes()) {
            if (enclosing.endsWith("." + name) || enclosing.equals(name)) {
                return new Resolution(enclosing, ResolutionKind.EXACT);
            }
            final Resolution nested = repositoryType(enclosing + "." + name, expression, depth);
            if (nested != null) {
                return nested;
            }
        }
        for (final KotlinImport kotlinImport : file.imports) {
            if (!kotlinImport.star() && name.equals(kotlinImport.visibleName())) {
                final Resolution imported = repositoryType(kotlinImport.target(), expression, depth);
                if (imported != null) {
                    return imported;
                }
                if (!expression || (KotlinDeclarationIndex.readsAsTypeName(name)
                        && !index.isType(parentOf(kotlinImport.target())))) {
                    return new Resolution(kotlinImport.target(), ResolutionKind.EXACT);
                }
                return null;
            }
        }
        final Resolution samePackage = repositoryType(KotlinDeclarationIndex.qualify(file.packageName, name),
                expression, depth);
        if (samePackage != null) {
            return samePackage;
        }
        // Star imports are one level of lookup: a name that several of them supply is ambiguous
        // and binds to none of them.
        final Set<String> starred = new java.util.LinkedHashSet<>();
        Resolution starredResolution = null;
        for (final KotlinImport kotlinImport : file.imports) {
            if (kotlinImport.star()) {
                final Resolution found = repositoryType(kotlinImport.target() + "." + name, expression, depth);
                if (found != null && starred.add(found.name())) {
                    starredResolution = found;
                }
            }
        }
        final String defaultImport = DEFAULT_IMPORTS.get(name);
        if (defaultImport == null) {
            starred.addAll(platformStarImports(name, file));
        }
        if (starred.size() > 1) {
            return new Resolution(name, ResolutionKind.AMBIGUOUS);
        }
        if (starredResolution != null) {
            return starredResolution;
        }
        if (defaultImport != null) {
            return new Resolution(defaultImport, ResolutionKind.EXACT);
        }
        if (starred.size() == 1) {
            return new Resolution(starred.iterator().next(), ResolutionKind.EXACT);
        }
        return null;
    }

    /**
     * The JDK type a name denotes through the star imports of {@code java} and {@code javax}
     * packages, read from the running platform as the Java side's type solver reads it. Only a name
     * that exactly one such package declares as a public type is answered; a package outside the JDK
     * cannot be asked, so it adds nothing. Kotlin's default imports are looked up first, so a name
     * they bring in is never read here.
     */
    private Set<String> platformStarImports(final String name, final KotlinFileModel file) {
        final Set<String> found = new java.util.LinkedHashSet<>();
        if (name.isEmpty() || !Character.isUpperCase(name.charAt(0))) {
            return found;
        }
        for (final KotlinImport kotlinImport : file.imports) {
            if (!kotlinImport.star() || !(kotlinImport.target().startsWith("java.")
                    || kotlinImport.target().startsWith("javax."))) {
                continue;
            }
            final String candidate = kotlinImport.target() + "." + name;
            if (isPlatformType(candidate)) {
                found.add(candidate);
            }
        }
        return found;
    }

    static boolean isPlatformType(final String qualifiedName) {
        return PLATFORM_TYPES.computeIfAbsent(qualifiedName, name -> {
            try {
                final Class<?> type = Class.forName(name, false, ClassLoader.getPlatformClassLoader());
                return Modifier.isPublic(type.getModifiers()) && type.getEnclosingClass() == null;
            } catch (final ClassNotFoundException | LinkageError | SecurityException unreadable) {
                return false;
            }
        });
    }

    /** Where in the order of lookup a simple name is found. */
    enum Origin {
        /** An enclosing type, or a type nested in one. */
        ENCLOSING,
        /** An import of that exact name. */
        IMPORT,
        /** The file's own package. */
        PACKAGE,
        /** A star import of a package the repository declares the type in. */
        STAR,
        /** A package every Kotlin file imports. */
        DEFAULT,
        /** Nothing in scope declares a type of that name. */
        NONE
    }

    /**
     * Where a simple name is found, in the order {@link #resolve} looks.
     *
     * @param name  The name.
     * @param scope Where it is written.
     * @return The level of the lookup that settles it.
     */
    Origin originOf(final String name, final Scope scope) {
        final KotlinFileModel file = scope.file();
        for (final String enclosing : scope.enclosingTypes()) {
            if (enclosing.endsWith("." + name) || enclosing.equals(name) || declares(enclosing + "." + name)) {
                return Origin.ENCLOSING;
            }
        }
        for (final KotlinImport kotlinImport : file.imports) {
            if (!kotlinImport.star() && name.equals(kotlinImport.visibleName())) {
                return Origin.IMPORT;
            }
        }
        if (declares(KotlinDeclarationIndex.qualify(file.packageName, name))) {
            return Origin.PACKAGE;
        }
        for (final KotlinImport kotlinImport : file.imports) {
            if (kotlinImport.star() && declares(kotlinImport.target() + "." + name)) {
                return Origin.STAR;
            }
        }
        if (DEFAULT_IMPORTS.containsKey(name)) {
            return Origin.DEFAULT;
        }
        if (!platformStarImports(name, file).isEmpty()) {
            return Origin.STAR;
        }
        return Origin.NONE;
    }

    private boolean declares(final String uniqueName) {
        return index.isType(uniqueName) || index.alias(uniqueName) != null;
    }

    /** Whether a simple name is a type, or a type alias, something in scope declares. */
    boolean namesTypeInScope(final String name, final Scope scope) {
        return scope.typeParameters().contains(name) || resolveSimple(name, scope, true, 0) != null;
    }

    /**
     * Whether a simple name is a top-level property or function the file can see without a
     * qualifier: one imported by name that is no type, or one of its own package or of a
     * star-imported package.
     */
    boolean topLevelValueMayBe(final String name, final Scope scope) {
        final KotlinFileModel file = scope.file();
        for (final KotlinImport kotlinImport : file.imports) {
            if (!kotlinImport.star() && name.equals(kotlinImport.visibleName())
                    && !index.isType(kotlinImport.target())) {
                return true;
            }
        }
        if (index.fileClassOf(KotlinDeclarationIndex.qualify(file.packageName, name)) != null) {
            return true;
        }
        for (final KotlinImport kotlinImport : file.imports) {
            if (kotlinImport.star() && index.fileClassOf(kotlinImport.target() + "." + name) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * The top-level functions a bare name can call from this file, by the order Kotlin looks a
     * function up: the ones an import by that name brings in, which decide alone when there is one;
     * otherwise the ones of the file's own package and of every star-imported package.
     *
     * @param name  The name as the file writes it.
     * @param scope Where it is written.
     * @return The declarations, extensions included; empty when no repository function is visible.
     */
    List<TopLevelFunction> visibleFunctions(final String name, final Scope scope) {
        final KotlinFileModel file = scope.file();
        final List<TopLevelFunction> imported = new ArrayList<>();
        boolean importedByName = false;
        for (final KotlinImport kotlinImport : file.imports) {
            if (!kotlinImport.star() && name.equals(kotlinImport.visibleName())) {
                importedByName = true;
                imported.addAll(index.functionsNamed(kotlinImport.target()));
            }
        }
        if (importedByName) {
            return imported;
        }
        final List<TopLevelFunction> visible = new ArrayList<>(
                index.functionsNamed(KotlinDeclarationIndex.qualify(file.packageName, name)));
        for (final KotlinImport kotlinImport : file.imports) {
            if (kotlinImport.star()) {
                visible.addAll(index.functionsNamed(kotlinImport.target() + "." + name));
            }
        }
        return visible;
    }

    /** Whether a name starts the package of the file, a package it imports from, or a published root. */
    static boolean isKnownPackageRoot(final String root, final KotlinFileModel file) {
        if (PACKAGE_ROOTS.contains(root) || file.packageName.equals(root)
                || file.packageName.startsWith(root + ".")) {
            return true;
        }
        for (final KotlinImport kotlinImport : file.imports) {
            if (kotlinImport.target().startsWith(root + ".")) {
                return true;
            }
        }
        return false;
    }

    /** The repository type, or type alias, of exactly this name, or null. */
    private Resolution repositoryType(final String uniqueName, final boolean expression, final int depth) {
        if (index.isType(uniqueName)) {
            return new Resolution(uniqueName, ResolutionKind.EXACT);
        }
        final AliasDeclaration alias = index.alias(uniqueName);
        if (alias != null) {
            return expandAlias(alias, expression, depth);
        }
        return null;
    }

    /** What an alias stands for, resolved in the scope of the file declaring it. */
    private Resolution expandAlias(final AliasDeclaration alias, final boolean expression, final int depth) {
        if (alias.alias().target().isEmpty()) {
            return null;
        }
        final Resolution target = resolve(alias.alias().target().get(0),
                new Scope(alias.file(), List.of(), Set.of()), expression, depth + 1);
        if (target == null || target.kind() != ResolutionKind.EXACT) {
            return target;
        }
        return new Resolution(target.name(), ResolutionKind.EXACT);
    }

    /**
     * The file class holding the top-level function a call names, when the call can only mean that
     * function: one imported by name, or one of the file's own package or of a star-imported package
     * that no declaration in closer scope shadows.
     *
     * @param name     The called name.
     * @param scope    Where the call is written.
     * @param shadowed Whether something in closer scope -- a parameter, a local, a member, an
     *                 implicit receiver -- may declare a function of that name.
     * @param importShadowed Whether something in closer scope may declare it ahead of an import
     *                 by name too: a name a parameter, local or member of the class or its readable
     *                 supertypes declares, or one a supertype the parse cannot read may pass down.
     * @return The file class's fully qualified name, or null.
     */
    String fileClassOfCall(final String name, final Scope scope, final boolean shadowed,
                           final boolean importShadowed) {
        final KotlinFileModel file = scope.file();
        for (final KotlinImport kotlinImport : file.imports) {
            if (!kotlinImport.star() && name.equals(kotlinImport.visibleName())) {
                if (importShadowed) {
                    return null;
                }
                return index.fileClassOf(kotlinImport.target());
            }
        }
        if (shadowed) {
            return null;
        }
        final String samePackage = index.fileClassOf(KotlinDeclarationIndex.qualify(file.packageName, name));
        if (samePackage != null) {
            return samePackage;
        }
        for (final KotlinImport kotlinImport : file.imports) {
            if (kotlinImport.star()) {
                final String starred = index.fileClassOf(kotlinImport.target() + "." + name);
                if (starred != null) {
                    return starred;
                }
            }
        }
        return null;
    }

    private static String parentOf(final String qualifiedName) {
        final int dot = qualifiedName.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return qualifiedName.substring(0, dot);
    }

    private static boolean allReadAsTypeNames(final String dotted) {
        for (final String part : dotted.split("\\.")) {
            if (!KotlinDeclarationIndex.readsAsTypeName(part)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a dotted name is spelled like a package followed by a type: {@code java.util.UUID}. */
    private static boolean readsAsQualifiedTypeName(final String dotted) {
        final String[] parts = dotted.split("\\.");
        if (parts.length < 2 || !KotlinDeclarationIndex.readsAsTypeName(parts[parts.length - 1])) {
            return false;
        }
        for (int i = 0; i < parts.length - 1; i += 1) {
            if (parts[i].isEmpty() || !Character.isLowerCase(parts[i].charAt(0))) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, String> defaultImports() {
        final Map<String, String> imports = new HashMap<>();
        put(imports, "kotlin", List.of("Any", "Nothing", "Unit", "Boolean", "Byte", "Short", "Int", "Long",
                "Float", "Double", "Char", "String", "CharSequence", "Number", "Array", "BooleanArray",
                "ByteArray", "ShortArray", "IntArray", "LongArray", "FloatArray", "DoubleArray", "CharArray",
                "UByte", "UShort", "UInt", "ULong", "Comparable", "Comparator", "Enum", "Annotation",
                "Throwable", "Exception", "Error", "RuntimeException", "IllegalArgumentException",
                "IllegalStateException", "IndexOutOfBoundsException", "UnsupportedOperationException",
                "NullPointerException", "ClassCastException", "ArithmeticException", "NumberFormatException",
                "NoSuchElementException", "ConcurrentModificationException", "AssertionError",
                "NoWhenBranchMatchedException", "UninitializedPropertyAccessException", "NotImplementedError",
                "KotlinNullPointerException", "TypeCastException",
                "Pair", "Triple", "Lazy", "Result", "Function", "Deprecated", "DeprecationLevel", "ReplaceWith",
                "Suppress", "PublishedApi", "DslMarker", "OptIn", "RequiresOptIn", "Throws", "KotlinVersion"));
        put(imports, "kotlin.annotation", List.of("Target", "Retention", "MustBeDocumented", "Repeatable",
                "AnnotationTarget", "AnnotationRetention"));
        put(imports, "kotlin.collections", List.of("Iterable", "MutableIterable", "Collection",
                "MutableCollection", "List", "MutableList", "Set", "MutableSet", "Map", "MutableMap", "Iterator",
                "MutableIterator", "ListIterator", "MutableListIterator", "ArrayList", "HashMap", "HashSet",
                "LinkedHashMap", "LinkedHashSet", "RandomAccess", "AbstractList", "AbstractMutableList",
                "AbstractCollection", "AbstractMutableCollection", "AbstractSet", "AbstractMutableSet",
                "AbstractMap", "AbstractMutableMap", "ArrayDeque", "Grouping", "IndexedValue"));
        put(imports, "kotlin.comparisons", List.of());
        put(imports, "kotlin.ranges", List.of("IntRange", "LongRange", "CharRange", "ClosedRange",
                "ClosedFloatingPointRange", "OpenEndRange", "IntProgression", "LongProgression",
                "CharProgression", "UIntRange", "ULongRange"));
        put(imports, "kotlin.sequences", List.of("Sequence"));
        put(imports, "kotlin.text", List.of("Regex", "RegexOption", "MatchResult", "MatchGroup",
                "MatchGroupCollection", "StringBuilder", "Appendable", "CharCategory", "Charsets", "Typography",
                "CharacterCodingException"));
        put(imports, "kotlin.io", List.of());
        put(imports, "kotlin.jvm", List.of("JvmStatic", "JvmField", "JvmName", "JvmOverloads", "JvmInline",
                "JvmRecord", "JvmSuppressWildcards", "JvmWildcard", "JvmMultifileClass", "JvmSynthetic",
                "Volatile", "Transient", "Synchronized", "Strictfp", "JvmDefault", "JvmDefaultWithCompatibility",
                "JvmDefaultWithoutCompatibility", "JvmSerializableLambda", "Throws"));
        put(imports, "java.lang", List.of("Object", "Thread", "Runnable", "System", "Math", "StrictMath",
                "Class", "ClassLoader", "Process", "ProcessBuilder", "Runtime", "ThreadLocal",
                "InheritableThreadLocal", "AutoCloseable", "Cloneable", "Void", "Integer", "Character",
                "InterruptedException", "SecurityException", "CloneNotSupportedException",
                "ReflectiveOperationException", "ClassNotFoundException", "NoSuchMethodException",
                "NoSuchFieldException", "IllegalAccessException", "InstantiationException", "StackTraceElement",
                "Record", "Override", "FunctionalInterface", "SafeVarargs", "Iterable", "Deprecated",
                "SuppressWarnings", "StackOverflowError", "LinkageError", "UnsatisfiedLinkError", "InternalError",
                "VirtualMachineError", "IllegalMonitorStateException", "NegativeArraySizeException",
                "ArrayIndexOutOfBoundsException", "StringIndexOutOfBoundsException", "ArrayStoreException",
                "ExceptionInInitializerError", "NoClassDefFoundError", "ThreadGroup", "Package", "Module",
                "Enum", "Comparable", "CharSequence", "Number", "Boolean", "Byte", "Short", "Long", "Float",
                "Double", "String", "StringBuilder", "Appendable", "Throwable", "Exception", "Error",
                "RuntimeException", "IllegalArgumentException", "IllegalStateException",
                "IndexOutOfBoundsException", "UnsupportedOperationException", "NullPointerException",
                "ClassCastException", "ArithmeticException", "NumberFormatException"));
        return Map.copyOf(imports);
    }

    /** Adds a package's names, keeping a name an earlier, higher-priority default package gave. */
    private static void put(final Map<String, String> imports, final String packageName,
                            final Collection<String> names) {
        for (final String name : names) {
            imports.putIfAbsent(name, packageName + "." + name);
        }
    }
}
