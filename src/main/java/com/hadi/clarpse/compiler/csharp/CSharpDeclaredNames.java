package com.hadi.clarpse.compiler.csharp;

import java.util.Set;

/**
 * Reads a member's text for declarations of a name beyond the ones the parse holds, so that a
 * receiver name is trusted only when it cannot stand for a different variable in the body.
 */
final class CSharpDeclaredNames {

    private static final Set<String> NOT_A_TYPE_WORD = Set.of("return", "throw", "else", "in", "case", "yield",
            "await", "new", "is", "as", "not", "and", "or", "when", "do", "goto", "using", "lock", "typeof",
            "nameof", "default", "ref", "out");

    private CSharpDeclaredNames() {
    }

    /** Whether the member's text could declare another variable of the name: a lambda parameter, a loop or pattern variable. */
    static boolean bodyMayDeclare(final CSharpModel.CSharpMemberModel memberModel, final String name) {
        return bodyMayDeclare(memberModel, name, 0);
    }

    /**
     * Whether the member's text writes more declarations of the name than the {@code known} ones the
     * parse holds for it -- its parameters, locals and typed lambda parameters. A local function's
     * parameter, a pattern variable, a loop or an {@code out} variable of the same name is such a
     * further declaration.
     */
    static boolean bodyMayDeclare(final CSharpModel.CSharpMemberModel memberModel, final String name,
                                          final int known) {
        final String source = memberModel.sourceText;
        if (source == null || memberModel.startOffset < 0 || memberModel.endOffset > source.length()
                || memberModel.startOffset > memberModel.endOffset) {
            return true;
        }
        final String body = source.substring(memberModel.startOffset, memberModel.endOffset);
        final String quoted = java.util.regex.Pattern.quote(name);
        if (java.util.regex.Pattern.compile("\\b" + quoted + "\\s*=>").matcher(body).find()
                || java.util.regex.Pattern.compile("[(,]\\s*" + quoted + "\\s*[,)]\\s*=>").matcher(body).find()) {
            return true;
        }
        final java.util.regex.Matcher declaration = java.util.regex.Pattern
                .compile("(?<![\\w.])([A-Za-z_][\\w.<>,\\[\\]?]*)\\s+" + quoted + "\\b(?!\\s*\\()").matcher(body);
        int written = 0;
        while (declaration.find()) {
            if (!NOT_A_TYPE_WORD.contains(declaration.group(1))) {
                written += 1;
                if (written > known) {
                    return true;
                }
            }
        }
        return false;
    }
}
