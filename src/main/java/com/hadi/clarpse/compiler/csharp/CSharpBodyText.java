package com.hadi.clarpse.compiler.csharp;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the references a member's body makes from its text alone, where the syntax tree does not
 * name them: receivers written before a member access, {@code typeof} operands, extension-method
 * calls with their receiver chain and argument count, and the initializer of a {@code var} local.
 *
 * <p>Every method is a pure function of the text it is given. Scanning ignores comments and the
 * contents of string and character literals, which {@link #withoutCommentsAndStrings(String)}
 * blanks out while keeping every offset and line break where it was.
 */
final class CSharpBodyText {

    private static final Pattern STATIC_RECEIVER_PATTERN =
            Pattern.compile("(?<![\\w.@])([A-Z][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*)"
                    + "\\s*(?:<[\\w.,\\s?\\[\\]]*(?:<[\\w.,\\s?\\[\\]]*>[\\w.,\\s?\\[\\]]*)*>)?\\s*\\.");

    /**
     * Whether a {@code var} declaration is initialised by nothing but {@code new T(...)}, with an
     * optional object or collection initializer, so that the local has exactly the type T. A
     * conditional, a call on the new object, an array creation or an anonymous type is not.
     */
    static boolean isPlainNewInitializer(final String declarationText, final String localName) {
        final String clean = withoutCommentsAndStrings(declarationText);
        final Matcher head = Pattern.compile("\\bvar\\s+" + Pattern.quote(localName)
                + "\\s*=\\s*new\\s+[A-Za-z_@][\\w.@]*\\s*(?:<[\\w.,\\s?\\[\\]<>]*>)?\\s*").matcher(clean);
        if (!head.find()) {
            return false;
        }
        int i = head.end();
        final int n = clean.length();
        if (i < n && clean.charAt(i) == '(') {
            i = skipBalanced(clean, i);
            while (i >= 0 && i < n && Character.isWhitespace(clean.charAt(i))) {
                i += 1;
            }
        }
        if (i >= 0 && i < n && clean.charAt(i) == '{') {
            i = skipBalanced(clean, i);
        }
        if (i < 0) {
            return false;
        }
        while (i < n && (Character.isWhitespace(clean.charAt(i)) || clean.charAt(i) == ';')) {
            i += 1;
        }
        return i == n && head.end() < n;
    }

    private static final Pattern LITERAL_INITIALIZER = Pattern.compile(
            "(?s)(?:\"(?:[^\"\\\\]|\\\\.)*\"|\\$?@\"(?:[^\"]|\"\")*\"|\\$\"[^\"]*\")"
                    + "|('(?:[^'\\\\]|\\\\.)')|(true|false)"
                    + "|([0-9][0-9_]{0,8})|([0-9][0-9_]*[lL])"
                    + "|([0-9][0-9_]*\\.[0-9][0-9_]*[dD]?|[0-9][0-9_]*[dD])|([0-9][0-9_]*(?:\\.[0-9][0-9_]*)?[fF])"
                    + "|([0-9][0-9_]*(?:\\.[0-9][0-9_]*)?[mM])");

    /**
     * The keyword of the type a {@code var} local's initializer fixes when the whole initializer is
     * one literal: {@code var s = "x";} is a {@code string}. Anything else, including a number that
     * needs a wider type than its digits show or an unsigned suffix, is {@code null}.
     *
     * @param declarationText The text of the declaration.
     * @param localName       The local's name.
     * @return The type keyword, or null.
     */
    static String literalType(final String declarationText, final String localName) {
        final Matcher head = Pattern.compile("\\bvar\\s+" + Pattern.quote(localName) + "\\s*=\\s*").matcher(declarationText);
        if (!head.find()) {
            return null;
        }
        final String rest = declarationText.substring(head.end()).trim();
        String initializer = rest;
        if (rest.endsWith(";")) {
            initializer = rest.substring(0, rest.length() - 1).trim();
        }
        final Matcher literal = LITERAL_INITIALIZER.matcher(initializer);
        if (!literal.matches()) {
            return null;
        }
        if (literal.group(1) != null) {
            return "char";
        }
        if (literal.group(2) != null) {
            return "bool";
        }
        if (literal.group(3) != null) {
            return "int";
        }
        if (literal.group(4) != null) {
            return "long";
        }
        if (literal.group(5) != null) {
            return "double";
        }
        if (literal.group(6) != null) {
            return "float";
        }
        if (literal.group(7) != null) {
            return "decimal";
        }
        return "string";
    }

    /** The index just past the bracket that closes the one at {@code open}, or -1 when it never closes. */
    private static int skipBalanced(final String text, final int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth += 1;
            } else if (c == ')' || c == ']' || c == '}') {
                depth -= 1;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return -1;
    }

    private static final Pattern VAR_INITIALIZER_PATTERN = Pattern.compile(
            "\\bvar\\s+(\\w+)\\s*=\\s*([^=][\\s\\S]*?);?\\s*$");

    /**
     * The names a {@code var} declaration's initializer reads when the initializer is nothing but
     * a receiver and member accesses, {@code ?.} included: {@code context?.Request} is
     * {@code [context, Request]} and {@code maker.Make()} is {@code [maker, Make()]}. Anything else --
     * an operator, a cast, a generic call, an {@code await}, a string in the arguments -- gives an empty
     * list, because the type of such an expression is not the type of a member.
     */
    static List<String> initializerAccess(final String declarationText, final String localName) {
        final Matcher matcher = VAR_INITIALIZER_PATTERN.matcher(declarationText);
        if (!matcher.find() || !matcher.group(1).equals(localName)) {
            return new ArrayList<>();
        }
        final String text = matcher.group(2).trim();
        final List<String> names = new ArrayList<>();
        int i = 0;
        final int n = text.length();
        while (i < n) {
            final int start = i;
            if (!Character.isJavaIdentifierStart(text.charAt(i))) {
                return new ArrayList<>();
            }
            while (i < n && Character.isJavaIdentifierPart(text.charAt(i))) {
                i += 1;
            }
            String name = text.substring(start, i);
            if (i < n && text.charAt(i) == '(') {
                if (names.isEmpty()) {
                    return new ArrayList<>();
                }
                int depth = 0;
                int j = i;
                for (; j < n; j++) {
                    final char c = text.charAt(j);
                    if (c == '"' || c == '\'') {
                        return new ArrayList<>();
                    }
                    if (c == '(') {
                        depth += 1;
                    } else if (c == ')') {
                        depth -= 1;
                        if (depth == 0) {
                            break;
                        }
                    }
                }
                if (depth != 0) {
                    return new ArrayList<>();
                }
                name = name + "()";
                i = j + 1;
            }
            names.add(name);
            if (i == n) {
                break;
            }
            if (text.startsWith("?.", i)) {
                i += 2;
            } else if (text.charAt(i) == '.') {
                i += 1;
            } else {
                return new ArrayList<>();
            }
            if (i >= n) {
                return new ArrayList<>();
            }
        }
        if (names.size() < 2) {
            return new ArrayList<>();
        }
        return names;
    }

    private static final Pattern MEMBER_CALL_PATTERN = Pattern.compile(
            "\\.\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*(?:<[\\w.,\\s?\\[\\]<>]*>)?\\s*\\(");

    /**
     * Records every {@code receiver.Name(...)} outside comments and string literals, with the
     * receiver written as a plain chain of names when it is one, and the number of arguments.
     */
    static void collectExtensionCalls(final String text, final CSharpModel.CSharpMemberModel member) {
        final String clean = withoutCommentsAndStrings(text);
        final Matcher matcher = MEMBER_CALL_PATTERN.matcher(clean);
        while (matcher.find()) {
            int i = matcher.start() - 1;
            while (i >= 0 && Character.isWhitespace(clean.charAt(i))) {
                i -= 1;
            }
            if (i >= 0 && clean.charAt(i) == '?') {
                i -= 1;
                while (i >= 0 && Character.isWhitespace(clean.charAt(i))) {
                    i -= 1;
                }
            }
            if (i < 0 || clean.charAt(i) == '>' || clean.charAt(i) == '.') {
                // Nothing before it, a generic type's static call, or a range: not a member call.
                continue;
            }
            final String receiver = receiverChain(clean, i);
            member.extensionCalls.add(new CSharpModel.ExtensionCall(
                    receiver, matcher.group(1), argumentCount(clean, matcher.end())));
        }
    }

    /** The dotted chain of names that ends at {@code last}, or null when it is not all of the receiver. */
    private static String receiverChain(final String text, final int last) {
        if (!isNamePart(text.charAt(last))) {
            return null;
        }
        final List<String> segments = new ArrayList<>();
        int end = last + 1;
        while (true) {
            int start = end;
            while (start > 0 && isNamePart(text.charAt(start - 1))) {
                start -= 1;
            }
            final String segment = text.substring(start, end);
            if (Character.isDigit(segment.charAt(0))) {
                return null;
            }
            segments.add(0, segment);
            int j = start - 1;
            while (j >= 0 && Character.isWhitespace(text.charAt(j))) {
                j -= 1;
            }
            if (j < 0 || text.charAt(j) != '.') {
                break;
            }
            int k = j - 1;
            while (k >= 0 && Character.isWhitespace(text.charAt(k))) {
                k -= 1;
            }
            if (k >= 0 && text.charAt(k) == '?') {
                k -= 1;
                while (k >= 0 && Character.isWhitespace(text.charAt(k))) {
                    k -= 1;
                }
            }
            if (k < 0 || !isNamePart(text.charAt(k))) {
                return null;
            }
            end = k + 1;
        }
        return String.join(".", segments);
    }

    private static boolean isNamePart(final char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '@';
    }

    /** The number of arguments in the list that opens just before {@code from}, or -1 when unreadable. */
    private static int argumentCount(final String text, final int from) {
        int depth = 0;
        int commas = 0;
        boolean any = false;
        for (int i = from; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '<') {
                return -1;
            }
            if (c == '(' || c == '[' || c == '{') {
                depth += 1;
                any = true;
            } else if (c == ')' || c == ']' || c == '}') {
                if (depth == 0) {
                    if (c != ')') {
                        return -1;
                    }
                    if (any) {
                        return commas + 1;
                    }
                    return 0;
                }
                depth -= 1;
            } else if (c == ',' && depth == 0) {
                commas += 1;
            } else if (!Character.isWhitespace(c)) {
                any = true;
            }
        }
        return -1;
    }

    private static final Pattern TYPEOF_OPERAND_PATTERN =
            Pattern.compile("(?<![\\w.@])typeof\\s*\\(\\s*([^()]+?)\\s*\\)");

    /** Records the operand of every {@code typeof} outside comments and string literals. */
    static void collectTypeofOperands(final String text, final CSharpModel.CSharpMemberModel member) {
        member.typeofOperands.addAll(typeofOperands(text));
    }

    /** The operand of every {@code typeof} in the text, outside comments and string literals. */
    static List<String> typeofOperands(final String text) {
        final List<String> operands = new ArrayList<>();
        if (text == null) {
            return operands;
        }
        final Matcher matcher = TYPEOF_OPERAND_PATTERN.matcher(withoutCommentsAndStrings(text));
        while (matcher.find()) {
            operands.add(matcher.group(1));
        }
        return operands;
    }

    /**
     * Records every dotted name that is written before a further member access, outside comments
     * and string literals, and does not itself follow a member access.
     */
    static void collectStaticReceivers(final String text, final CSharpModel.CSharpMemberModel member) {
        final String clean = withoutCommentsAndStrings(text);
        final Matcher matcher = STATIC_RECEIVER_PATTERN.matcher(clean);
        while (matcher.find()) {
            if (!namesATypeInstead(clean, matcher.start(), matcher.end())) {
                member.staticReceivers.add(matcher.group(1));
            }
        }
    }

    private static final Pattern NEW_BEFORE = Pattern.compile("(?<![\\w.@])new\\s+$");

    private static final Pattern DECLARATION_AFTER = Pattern.compile(
            "\\G\\s*[A-Za-z_]\\w*(?:\\s*<[^;(){}]*?>)?(?:\\s*\\.\\s*[A-Za-z_]\\w*(?:\\s*<[^;(){}]*?>)?)*"
                    + "\\s+([A-Za-z_@]\\w*)");

    /** Words that may follow an expression, so that a name before one is not a declared type. */
    private static final Set<String> AFTER_EXPRESSION = Set.of(
            "is", "as", "in", "and", "or", "when", "with", "switch", "select", "where", "orderby", "group",
            "join", "let", "into", "by", "on", "equals", "ascending", "descending", "from");

    /**
     * Whether the dotted name that a receiver match starts is a type written in a type position, and
     * so not a receiver: the match is only the leading segments of {@code new App.Lib.Thing()} or
     * {@code Vendor.Pkg.Widget w;}, whose last segment is the type and whose earlier ones are
     * namespaces.
     */
    private static boolean namesATypeInstead(final String clean, final int start, final int end) {
        final String before = clean.substring(Math.max(0, start - 16), start);
        if (NEW_BEFORE.matcher(before).find()) {
            return true;
        }
        final Matcher declaration = DECLARATION_AFTER.matcher(clean);
        declaration.region(end, clean.length());
        return declaration.lookingAt() && !AFTER_EXPRESSION.contains(declaration.group(1));
    }

    /** The text with comments and the contents of plain string and character literals blanked out. */
    static String withoutCommentsAndStrings(final String text) {
        final StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        final int n = text.length();
        while (i < n) {
            final char c = text.charAt(i);
            final char next;
            if (i + 1 < n) {
                next = text.charAt(i + 1);
            } else {
                next = '\0';
            }
            if (c == '/' && next == '/') {
                while (i < n && text.charAt(i) != '\n') {
                    out.append(' ');
                    i += 1;
                }
            } else if (c == '/' && next == '*') {
                final int close = text.indexOf("*/", i + 2);
                int end = n;
                if (close >= 0) {
                    end = close + 2;
                }
                for (; i < end; i++) {
                    appendBlanked(out, text.charAt(i));
                }
            } else if (c == '$' && (next == '"' || (next == '@' && i + 2 < n && text.charAt(i + 2) == '"'))
                    || c == '@' && next == '$') {
                // An interpolated string holds code in its braces; it is left as written.
                out.append(c);
                i += 1;
            } else if (c == '"' || c == '\'') {
                final boolean verbatim = i > 0 && text.charAt(i - 1) == '@';
                final int end = literalEnd(text, i, verbatim);
                out.append(c);
                for (int k = i + 1; k < end - 1; k++) {
                    appendBlanked(out, text.charAt(k));
                }
                if (end - 1 > i) {
                    out.append(c);
                }
                i = end;
            } else {
                out.append(c);
                i += 1;
            }
        }
        return out.toString();
    }

    private static void appendBlanked(final StringBuilder out, final char c) {
        if (c == '\n') {
            out.append('\n');
        } else {
            out.append(' ');
        }
    }

    /** The index just past the literal that opens at {@code start}. */
    private static int literalEnd(final String text, final int start, final boolean verbatim) {
        final char quote = text.charAt(start);
        final int n = text.length();
        int i = start + 1;
        while (i < n) {
            final char c = text.charAt(i);
            if (verbatim && c == quote && i + 1 < n && text.charAt(i + 1) == quote) {
                i += 2;
            } else if (!verbatim && c == '\\') {
                i += 2;
            } else if (c == quote) {
                return i + 1;
            } else if (!verbatim && c == '\n') {
                return i;
            } else {
                i += 1;
            }
        }
        return n;
    }

    private CSharpBodyText() {
    }
}
