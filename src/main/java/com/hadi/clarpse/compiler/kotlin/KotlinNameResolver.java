package com.hadi.clarpse.compiler.kotlin;

import com.hadi.clarpse.compiler.kotlin.KotlinDeclarationIndex.AliasDeclaration;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinFileModel;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinImport;
import com.hadi.clarpse.reference.ResolutionKind;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                return resolvedHead;
            }
            if (expression) {
                return null;
            }
            return new Resolution(name, ResolutionKind.UNRESOLVED);
        }
        if (resolvedHead != null && resolvedHead.kind() != ResolutionKind.UNRESOLVED) {
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
        for (final KotlinImport kotlinImport : file.imports) {
            if (kotlinImport.star()) {
                final Resolution starred = repositoryType(kotlinImport.target() + "." + name, expression, depth);
                if (starred != null) {
                    return starred;
                }
            }
        }
        final String defaultImport = DEFAULT_IMPORTS.get(name);
        if (defaultImport != null) {
            return new Resolution(defaultImport, ResolutionKind.EXACT);
        }
        return null;
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
        if (target == null || target.kind() == ResolutionKind.UNRESOLVED) {
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
     * @return The file class's fully qualified name, or null.
     */
    String fileClassOfCall(final String name, final Scope scope, final boolean shadowed) {
        final KotlinFileModel file = scope.file();
        for (final KotlinImport kotlinImport : file.imports) {
            if (!kotlinImport.star() && name.equals(kotlinImport.visibleName())) {
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
