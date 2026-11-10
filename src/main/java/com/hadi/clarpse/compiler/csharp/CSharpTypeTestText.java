package com.hadi.clarpse.compiler.csharp;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the types that type tests, casts, patterns, {@code default(T)} and {@code nameof(T)} name in a
 * body of C# text.
 *
 * <p>Two kinds of names are told apart. A name written where only a type can stand ({@code o as T},
 * {@code o is T t}, {@code case T t:}, {@code T t =>} in a switch expression, {@code default(T)}) is a
 * type operand. A name that could equally be a constant, a member or a parenthesised value
 * ({@code o is T}, {@code case T:}, {@code nameof(T)}, {@code (T)x}) is only a candidate: the caller
 * records it when the name resolves to a type of the repository through the scoping rules and is not
 * the name of a variable or member in scope.
 *
 * <p>A cast {@code (T)x} is read only when the parenthesised text is one type name, the open
 * parenthesis stands where an expression begins, and an operand follows the closing one:
 * {@code (a) - b} is a subtraction and {@code if (a) b();} a condition, never casts.
 *
 * <p>Every method is a pure function of the text it is given, which is scanned with comments and
 * the contents of string literals blanked out.
 */
final class CSharpTypeTestText {

    private static final String NAME = "(?:global::)?[A-Za-z_@][\\w@]*(?:\\s*\\.\\s*[A-Za-z_@][\\w@]*)*";
    private static final String ARGUMENTS = "(?:\\s*<(?:[^<>;(){}]|<[^<>;(){}]*>)*>)?";
    private static final String TYPE = NAME + ARGUMENTS;
    private static final String SUFFIX = "(?:\\s*\\?(?![?.]))?(?:\\s*\\[[\\s,]*\\])*";
    private static final String IDENT = "[A-Za-z_@][\\w@]*";

    private static final Pattern AS_OPERAND = Pattern.compile("(?<![\\w.@])as\\s+(" + TYPE + ")");
    private static final Pattern DEFAULT_OPERAND =
            Pattern.compile("(?<![\\w.@])default\\s*\\(\\s*(" + TYPE + SUFFIX + ")\\s*\\)");
    private static final Pattern NAMEOF_OPERAND =
            Pattern.compile("(?<![\\w.@])nameof\\s*\\(\\s*(" + TYPE + ")\\s*\\)");
    private static final Pattern IS_PATTERN =
            Pattern.compile("(?<![\\w.@])is\\s+(?:not\\s+)?(" + TYPE + ")");
    private static final Pattern CASE_PATTERN =
            Pattern.compile("(?<![\\w.@])case\\s+(?:not\\s+)?(" + TYPE + ")");
    private static final Pattern CHAINED_PATTERN =
            Pattern.compile("\\G\\s+(?:and|or)\\s+(?:not\\s+)?(" + TYPE + ")");
    private static final Pattern SWITCH_ARM =
            Pattern.compile("[{,]\\s*(" + TYPE + ")\\s+(" + IDENT + ")\\s*(?:=>|when(?![\\w@]))");
    private static final Pattern PARENTHESISED_TYPE = Pattern.compile("\\(\\s*(" + TYPE + SUFFIX + ")\\s*\\)");
    private static final Pattern WORD_AFTER = Pattern.compile("\\G\\s*(" + IDENT + ")");

    /** Words that are not a type name or a variable's name where a pattern or an operand is read. */
    private static final Set<String> NOT_A_NAME = Set.of(
            "var", "null", "true", "false", "default", "this", "base", "new", "typeof", "nameof", "sizeof",
            "_", "not", "and", "or", "when", "is", "as", "in", "switch", "with", "if", "else", "return",
            "case", "checked", "unchecked", "await", "throw", "yield", "ref", "out", "stackalloc", "from",
            "select", "where", "group", "join", "orderby", "let", "into", "by", "on", "equals",
            "ascending", "descending");

    /** Keywords after which an expression, and so a cast, can begin. */
    private static final Set<String> BEFORE_EXPRESSION = Set.of("return", "await", "throw", "in", "else", "case");

    private static final String BEFORE_CAST = "=(,{};?:!&|+-*/%^~[";

    private CSharpTypeTestText() {
    }

    /**
     * Records on the member the types its text names: the type operands, the candidates that need
     * the scoping rules to confirm them, and the casts.
     */
    static void collect(final String text, final CSharpModel.CSharpMemberModel member) {
        final String code = CSharpBodyText.withoutCommentsAndStrings(text);
        collectOperands(AS_OPERAND, code, member);
        final Matcher defaults = DEFAULT_OPERAND.matcher(code);
        while (defaults.find()) {
            addOperand(defaults.group(1), member);
        }
        final Matcher nameofs = NAMEOF_OPERAND.matcher(code);
        while (nameofs.find()) {
            addCandidate(nameofs.group(1), member);
        }
        collectPatterns(IS_PATTERN, code, member);
        collectPatterns(CASE_PATTERN, code, member);
        final Matcher arms = SWITCH_ARM.matcher(code);
        while (arms.find()) {
            addOperand(arms.group(1), member);
        }
        collectCasts(code, member);
    }

    private static void collectOperands(final Pattern pattern, final String code,
                                        final CSharpModel.CSharpMemberModel member) {
        final Matcher matcher = pattern.matcher(code);
        while (matcher.find()) {
            addOperand(matcher.group(1), member);
        }
    }

    /**
     * A pattern after {@code is} or {@code case}: a type operand when a designation or a property
     * pattern follows it, otherwise a candidate, with the names chained to it by {@code and}/{@code or}.
     */
    private static void collectPatterns(final Pattern pattern, final String code,
                                        final CSharpModel.CSharpMemberModel member) {
        final Matcher matcher = pattern.matcher(code);
        while (matcher.find()) {
            int end = matcher.end();
            if (declaresOrDestructures(code, end)) {
                addOperand(matcher.group(1), member);
                continue;
            }
            addCandidate(matcher.group(1), member);
            final Matcher chained = CHAINED_PATTERN.matcher(code);
            chained.region(end, code.length());
            while (chained.lookingAt()) {
                addCandidate(chained.group(1), member);
                end = chained.end();
                chained.region(end, code.length());
            }
        }
    }

    /** Whether a designation ({@code T t}) or a property pattern ({@code T { ... }}) follows a type. */
    private static boolean declaresOrDestructures(final String code, final int from) {
        int index = from;
        while (index < code.length() && Character.isWhitespace(code.charAt(index))) {
            index += 1;
        }
        if (index >= code.length()) {
            return false;
        }
        if (code.charAt(index) == '{') {
            return true;
        }
        final Matcher word = WORD_AFTER.matcher(code);
        word.region(from, code.length());
        return index > from && word.lookingAt() && !NOT_A_NAME.contains(word.group(1));
    }

    private static void collectCasts(final String code, final CSharpModel.CSharpMemberModel member) {
        final Matcher matcher = PARENTHESISED_TYPE.matcher(code);
        while (matcher.find()) {
            if (!beginsAnExpression(code, matcher.start())) {
                continue;
            }
            int after = matcher.end();
            while (after < code.length() && Character.isWhitespace(code.charAt(after))) {
                after += 1;
            }
            if (after >= code.length() || !isOperandStart(code, after)) {
                continue;
            }
            final String name = matcher.group(1);
            if (code.charAt(after) == '(') {
                addCandidate(name, member);
            } else {
                member.castTypes.add(name);
            }
        }
    }

    /** Whether the open parenthesis at the offset stands where an operand can start rather than after a name. */
    private static boolean beginsAnExpression(final String code, final int open) {
        int index = open - 1;
        while (index >= 0 && Character.isWhitespace(code.charAt(index))) {
            index -= 1;
        }
        if (index < 0) {
            return true;
        }
        final char before = code.charAt(index);
        if (BEFORE_CAST.indexOf(before) >= 0) {
            return true;
        }
        if (before == '>') {
            return index > 0 && code.charAt(index - 1) == '=';
        }
        int start = index;
        while (start >= 0 && (Character.isLetterOrDigit(code.charAt(start)) || code.charAt(start) == '_')) {
            start -= 1;
        }
        return BEFORE_EXPRESSION.contains(code.substring(start + 1, index + 1));
    }

    private static boolean isOperandStart(final String code, final int index) {
        final char c = code.charAt(index);
        if (c == '(' || c == '~' || c == '"' || c == '\'' || c == '$' || Character.isDigit(c)) {
            return true;
        }
        if (c == '@' && index + 1 < code.length() && code.charAt(index + 1) == '"') {
            return true;
        }
        final Matcher word = WORD_AFTER.matcher(code);
        word.region(index, code.length());
        if (!word.lookingAt()) {
            return false;
        }
        final String text = word.group(1);
        return !Set.of("as", "is", "and", "or", "when", "with", "switch", "in", "into", "equals", "by", "on",
                "ascending", "descending", "select", "group", "join", "orderby", "let", "where").contains(text);
    }

    private static void addOperand(final String written, final CSharpModel.CSharpMemberModel member) {
        final String name = written.trim();
        if (!NOT_A_NAME.contains(name)) {
            member.typeTestOperands.add(name);
        }
    }

    private static void addCandidate(final String written, final CSharpModel.CSharpMemberModel member) {
        final String name = written.trim();
        if (!NOT_A_NAME.contains(name)) {
            member.typeCandidates.add(name);
        }
    }
}
