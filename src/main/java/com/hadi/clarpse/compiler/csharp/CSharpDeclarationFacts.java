package com.hadi.clarpse.compiler.csharp;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Facts a C# declaration states in its own text: the modifiers of a member, with the meaning a
 * {@code ref} return gives them, and the classes the {@code using static} directives in force name.
 */
final class CSharpDeclarationFacts {

    private CSharpDeclarationFacts() {
    }

    /**
     * The modifiers of a member declaration. A {@code ref} before the type says the member returns or
     * holds a reference and is a modifier of the member; the {@code readonly} that may follow it
     * belongs to the type ({@code ref readonly int}), so it is not.
     *
     * @param text The member's declaration text.
     * @return The modifiers, in source order.
     */
    static List<String> memberModifiers(final String text) {
        final List<String> modifiers = CSharpFileParser.parseModifiers(text);
        if (text == null || text.isBlank()) {
            return modifiers;
        }
        final String header = CSharpFileParser.normalizeWhitespace(headerOf(text));
        final List<String> parts = List.of(header.split(" "));
        final int ref = parts.indexOf("ref");
        if (ref < 0) {
            return modifiers;
        }
        if (ref + 1 < parts.size() && "readonly".equals(parts.get(ref + 1))
                && !parts.subList(0, ref).contains("readonly")) {
            // `readonly` directly after `ref` belongs to the returned type, unless a `readonly`
            // was also written before the `ref`.
            modifiers.remove("readonly");
        }
        modifiers.add("ref");
        return modifiers;
    }

    /** The text of a member declaration before its parameters, body, expression body or initializer. */
    private static String headerOf(final String text) {
        int end = text.length();
        for (final String marker : new String[] {"{", "(", ";", "=>", "="}) {
            final int index = text.indexOf(marker);
            if (index >= 0) {
                end = Math.min(end, index);
            }
        }
        return text.substring(0, end).trim();
    }

    /**
     * The classes named by {@code using static} directives that are in force where a type of the given
     * namespace is declared: a directive written in a namespace applies inside it.
     *
     * @param usings        The usings of the file.
     * @param namespaceName The namespace of the type.
     * @return The targets as written.
     */
    static Set<String> staticImportTargets(final List<CSharpModel.CSharpUsingModel> usings,
                                           final String namespaceName) {
        final Set<String> targets = new LinkedHashSet<>();
        for (final CSharpModel.CSharpUsingModel usingModel : usings) {
            if (!usingModel.staticImport || usingModel.aliasImport || usingModel.implicitImport
                    || usingModel.target == null || usingModel.target.isEmpty()) {
                continue;
            }
            final String scope = usingModel.scopeNamespace;
            if (scope == null || scope.isEmpty() || scope.equals(namespaceName)
                    || (namespaceName != null && namespaceName.startsWith(scope + "."))) {
                targets.add(usingModel.target);
            }
        }
        return targets;
    }
}
