package com.hadi.clarpse.compiler.csharp;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The unique names of C# types, which tell apart types that share a name and differ only in arity.
 *
 * <p>A declared type is identified by its metadata name: its namespace, then each enclosing type
 * and itself, a generic type spelled with its arity in CLR metadata form ({@code Converter`1}). Its
 * unique name is that metadata name with the arity dropped from every type whose name no type of
 * another arity shares in the same scope. A generic type therefore keeps its erased name, and only
 * a name overloaded by arity carries it: {@code Converter} and {@code Converter<T>} in namespace
 * {@code Acme} are {@code Acme.Converter} and {@code Acme.Converter`1}, while a lone
 * {@code Repo<T>} is {@code Acme.Repo}.
 *
 * <p>Unique names depend on every type declared in a scope, so they are computed over all the
 * declarations of a compile at once.
 */
final class CSharpTypeNames {

    /** The arity of a reference whose type-argument list could not be read. */
    static final int UNKNOWN_ARITY = -1;

    private static final char ARITY_MARK = '`';

    private CSharpTypeNames() {
    }

    /**
     * The number of type parameters a declared identifier names: {@code Foo<T, U>} has two, and
     * {@code Foo} none.
     *
     * @param identifier The identifier as declared, its type-parameter list included.
     * @return The arity.
     */
    static int declaredArity(final String identifier) {
        if (identifier == null) {
            return 0;
        }
        final int open = identifier.indexOf('<');
        if (open < 0) {
            return 0;
        }
        final int arity = typeArgumentCount(identifier, open);
        return Math.max(arity, 0);
    }

    /**
     * The number of type arguments in the list opening at {@code open}, counted at its top level:
     * {@code <string, List<int>>} has two, and the unbound {@code <,>} two.
     *
     * <p>Text that is not a type-argument list -- an operator, a literal, a statement, or a list that
     * never closes -- yields {@link #UNKNOWN_ARITY}, so a comparison written {@code A < B} is not
     * read as a type with one argument.
     *
     * @param text The text.
     * @param open The index of the {@code <} opening the list.
     * @return The count, or {@link #UNKNOWN_ARITY}.
     */
    static int typeArgumentCount(final String text, final int open) {
        int angles = 0;
        int nesting = 0;
        int commas = 0;
        for (int i = open; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '<') {
                angles++;
            } else if (c == '>') {
                angles--;
                if (angles == 0) {
                    return commas + 1;
                }
            } else if (c == '(' || c == '[') {
                nesting++;
            } else if (c == ')' || c == ']') {
                nesting--;
                if (nesting < 0) {
                    return UNKNOWN_ARITY;
                }
            } else if (c == ',') {
                if (angles == 1 && nesting == 0) {
                    commas++;
                }
            } else if (!isTypeArgumentChar(c)) {
                return UNKNOWN_ARITY;
            }
        }
        return UNKNOWN_ARITY;
    }

    private static boolean isTypeArgumentChar(final char c) {
        return Character.isLetterOrDigit(c) || Character.isWhitespace(c)
                || c == '_' || c == '.' || c == '?' || c == ':' || c == '*' || c == '@';
    }

    /**
     * A type's segment of a metadata name: its name, followed by its arity when it is generic.
     *
     * @param name  The type's erased name.
     * @param arity Its number of type parameters.
     * @return The segment.
     */
    static String metadataSegment(final String name, final int arity) {
        if (arity <= 0) {
            return name;
        }
        return name + ARITY_MARK + arity;
    }

    /**
     * A metadata name: the given scope's name, then the segment.
     *
     * @param scope   The namespace or enclosing type's metadata name; empty for none.
     * @param segment The type's segment.
     * @return The metadata name.
     */
    static String qualify(final String scope, final String segment) {
        if (scope == null || scope.isEmpty()) {
            return segment;
        }
        return scope + "." + segment;
    }

    /**
     * The name a reference written in source can reach a type by: its unique name without the arity
     * on its last segment, which is the scope's unique name followed by the type's erased name.
     *
     * @param uniqueName A type's unique name.
     * @return The name, equal to the unique name for a type whose name is not overloaded by arity.
     */
    static String lookupName(final String uniqueName) {
        return stripArity(uniqueName);
    }

    /**
     * The unique name of each of the given declared types.
     *
     * @param metadataNames The metadata names of every type declared in the compile.
     * @return Each metadata name's unique name.
     */
    static Map<String, String> uniqueNames(final Collection<String> metadataNames) {
        final Set<String> declared = new HashSet<>(metadataNames);
        final Map<String, Set<Integer>> aritiesByName = new HashMap<>();
        for (final String metadataName : declared) {
            final int dot = metadataName.lastIndexOf('.');
            final String scope;
            if (dot < 0) {
                scope = "";
            } else {
                scope = metadataName.substring(0, dot);
            }
            final String segment = metadataName.substring(dot + 1);
            aritiesByName.computeIfAbsent(qualify(scope, stripArity(segment)), key -> new TreeSet<>())
                    .add(arity(segment));
        }
        final Map<String, String> uniqueNames = new LinkedHashMap<>();
        for (final String metadataName : metadataNames) {
            uniqueNames.put(metadataName, uniqueName(metadataName, declared, aritiesByName));
        }
        return uniqueNames;
    }

    private static String uniqueName(final String metadataName, final Set<String> declared,
                                     final Map<String, Set<Integer>> aritiesByName) {
        final String[] segments = metadataName.split("\\.");
        final StringBuilder metadataScope = new StringBuilder();
        final StringBuilder unique = new StringBuilder();
        for (final String segment : segments) {
            final String scope = metadataScope.toString();
            final String erased = stripArity(segment);
            final String declaredHere = qualify(scope, segment);
            final Set<Integer> arities = aritiesByName.get(qualify(scope, erased));
            final boolean overloaded = declared.contains(declaredHere) && arities != null && arities.size() > 1;
            if (unique.length() > 0) {
                unique.append('.');
                metadataScope.append('.');
            }
            metadataScope.append(segment);
            if (overloaded) {
                unique.append(segment);
            } else {
                unique.append(erased);
            }
        }
        return unique.toString();
    }

    private static String stripArity(final String name) {
        final int mark = name.lastIndexOf(ARITY_MARK);
        if (mark < 0 || mark < name.lastIndexOf('.')) {
            return name;
        }
        return name.substring(0, mark);
    }

    private static int arity(final String segment) {
        final int mark = segment.lastIndexOf(ARITY_MARK);
        if (mark < 0) {
            return 0;
        }
        try {
            return Integer.parseInt(segment.substring(mark + 1));
        } catch (final NumberFormatException e) {
            return 0;
        }
    }
}
