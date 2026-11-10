package com.hadi.clarpse.compiler.csharp;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the type names that the {@code where} clauses of a generic method or delegate constrain
 * its type parameters to.
 *
 * <p>The text given is what follows the parameter list. Clauses end where the body, the expression
 * body or the terminating semicolon begins. The keyword constraints ({@code class}, {@code struct},
 * {@code notnull}, {@code unmanaged}, {@code default}, {@code new()} and {@code allows ...}) name no
 * type and are dropped. A constraint that is another type parameter is returned like any other
 * name, for the caller to discard.
 */
final class CSharpConstraintText {

    private static final Pattern WHERE = Pattern.compile("(?<![\\w@])where\\s+[A-Za-z_@][\\w@]*\\s*:");

    private static final Set<String> KEYWORD_CONSTRAINTS =
            Set.of("class", "class?", "struct", "notnull", "unmanaged", "default");

    private CSharpConstraintText() {
    }

    /**
     * The constraint types written in the clauses at the start of a declaration's tail.
     *
     * @param tail The source following the parameter list, up to the end of the declaration.
     * @return The written type names, in order; empty when there is no clause.
     */
    static List<String> constraintTypes(final String tail) {
        final List<String> types = new ArrayList<>();
        if (tail == null) {
            return types;
        }
        final String code = CSharpBodyText.withoutCommentsAndStrings(tail);
        final String header = code.substring(0, headerEnd(code));
        final Matcher where = WHERE.matcher(header);
        int clauseStart = -1;
        while (where.find()) {
            if (clauseStart >= 0) {
                addClause(header.substring(clauseStart, where.start()), types);
            }
            clauseStart = where.end();
        }
        if (clauseStart >= 0) {
            addClause(header.substring(clauseStart), types);
        }
        return types;
    }

    /** Where the constraint clauses end: at the body, the expression body or the semicolon. */
    private static int headerEnd(final String code) {
        int end = code.length();
        for (final String stop : new String[] {"{", "=>", ";"}) {
            final int at = code.indexOf(stop);
            if (at >= 0 && at < end) {
                end = at;
            }
        }
        return end;
    }

    private static void addClause(final String clause, final List<String> types) {
        int depth = 0;
        int start = 0;
        for (int i = 0; i <= clause.length(); i++) {
            final boolean last = i == clause.length();
            if (!last) {
                final char c = clause.charAt(i);
                if (c == '<' || c == '(') {
                    depth += 1;
                } else if (c == '>' || c == ')') {
                    depth -= 1;
                }
            }
            if (last || (clause.charAt(i) == ',' && depth == 0)) {
                addConstraint(clause.substring(start, i).trim(), types);
                start = i + 1;
            }
        }
    }

    private static void addConstraint(final String constraint, final List<String> types) {
        if (constraint.isEmpty() || KEYWORD_CONSTRAINTS.contains(constraint)
                || constraint.replaceAll("\\s+", "").equals("new()") || constraint.startsWith("allows ")) {
            return;
        }
        types.add(constraint);
    }
}
