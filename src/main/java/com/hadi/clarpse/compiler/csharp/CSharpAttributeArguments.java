package com.hadi.clarpse.compiler.csharp;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the argument list written on a C# attribute, such as {@code ("api/x", Order = 2)}, into
 * the arguments as written.
 *
 * <p>The list is read from its text. A string, character, boolean or number literal, a
 * {@code typeof}, an array initializer, a dotted name, and a concatenation of string literals are
 * recognised; any other expression is kept as an argument whose value is not known. Whether a dotted
 * name is an enum constant is for the caller to settle, since it depends on the types the repository
 * declares.
 */
final class CSharpAttributeArguments {

    /** What kind of expression an argument was written as. */
    enum ValueKind {
        STRING, NUMBER, BOOLEAN, CLASS,
        /** A dotted name, which is an enum constant when its qualifier is an enum. */
        QUALIFIED,
        ARRAY,
        /** Any other expression. */
        OTHER
    }

    /** An argument's value as written: a literal's value, a name's text, or an array's elements. */
    record Value(ValueKind kind, String text, List<Value> elements) {
    }

    /** One argument; the name is null when it was written without one. */
    record Argument(String name, Value value) {
    }

    private static final Value OTHER = new Value(ValueKind.OTHER, null, List.of());
    private static final Pattern NAMED = Pattern.compile(
            "^@?([A-Za-z_][A-Za-z0-9_]*)\\s*(?:=(?!=)|:(?!:))\\s*(.*)$", Pattern.DOTALL);
    private static final Pattern NUMBER = Pattern.compile(
            "^[+-]?(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|[0-9][0-9_]*(?:\\.[0-9][0-9_]*)?(?:[eE][+-]?[0-9]+)?|\\.[0-9]+)"
                    + "[uUlLfFdDmM]*$");
    private static final Pattern HEX_OR_BINARY = Pattern.compile("^[+-]?0[xXbB].*");
    private static final Pattern DOTTED = Pattern.compile(
            "^[A-Za-z_][A-Za-z0-9_]*(?:\\s*\\.\\s*[A-Za-z_][A-Za-z0-9_]*)+$");
    private static final Pattern TYPEOF = Pattern.compile("^typeof\\s*\\(\\s*([^()]+?)\\s*\\)$");
    private static final Pattern ARRAY_NEW = Pattern.compile(
            "^new\\s*(?:[A-Za-z_][A-Za-z0-9_.<>?]*\\s*)?\\[[^\\]]*\\]\\s*(\\{.*\\})$", Pattern.DOTALL);

    private CSharpAttributeArguments() {
    }

    /**
     * The arguments in an attribute's parenthesised list.
     *
     * @param parenthesised The list's text, parentheses included, or null.
     * @return The arguments in written order; empty for no list or an empty one.
     */
    static List<Argument> parse(final String parenthesised) {
        final List<Argument> arguments = new ArrayList<>();
        if (parenthesised == null) {
            return arguments;
        }
        String inner = parenthesised.trim();
        if (inner.startsWith("(") && inner.endsWith(")")) {
            inner = inner.substring(1, inner.length() - 1);
        }
        for (final String part : splitTopLevel(inner, ',')) {
            final String text = part.trim();
            if (text.isEmpty()) {
                continue;
            }
            final Matcher named = NAMED.matcher(text);
            if (named.matches()) {
                arguments.add(new Argument(named.group(1), value(named.group(2).trim())));
            } else {
                arguments.add(new Argument(null, value(text)));
            }
        }
        return arguments;
    }

    private static Value value(final String text) {
        if (text.isEmpty()) {
            return OTHER;
        }
        if (text.startsWith("(") && text.endsWith(")") && splitTopLevel(text, ',').size() == 1
                && closesAtEnd(text)) {
            return value(text.substring(1, text.length() - 1).trim());
        }
        final List<String> summands = splitTopLevel(text, '+');
        if (summands.size() > 1 && text.indexOf('"') >= 0) {
            return concatenation(summands);
        }
        final String string = stringLiteral(text);
        if (string != null) {
            return new Value(ValueKind.STRING, string, List.of());
        }
        if (text.startsWith("'")) {
            final String character = charLiteral(text);
            if (character == null) {
                return OTHER;
            }
            return new Value(ValueKind.STRING, character, List.of());
        }
        if ("true".equals(text) || "false".equals(text)) {
            return new Value(ValueKind.BOOLEAN, text, List.of());
        }
        if (NUMBER.matcher(text).matches()) {
            return new Value(ValueKind.NUMBER, numberText(text), List.of());
        }
        final Matcher typeOf = TYPEOF.matcher(text);
        if (typeOf.matches()) {
            return new Value(ValueKind.CLASS, typeOf.group(1).replaceAll("\\s+", ""), List.of());
        }
        final Matcher array = ARRAY_NEW.matcher(text);
        if (array.matches()) {
            return arrayValue(array.group(1));
        }
        if (text.startsWith("{") && text.endsWith("}")) {
            return arrayValue(text);
        }
        if (DOTTED.matcher(text).matches()) {
            return new Value(ValueKind.QUALIFIED, text.replaceAll("\\s+", ""), List.of());
        }
        return OTHER;
    }

    private static Value concatenation(final List<String> summands) {
        final StringBuilder joined = new StringBuilder();
        for (final String summand : summands) {
            final Value part = value(summand.trim());
            if (part.kind() != ValueKind.STRING) {
                return OTHER;
            }
            joined.append(part.text());
        }
        return new Value(ValueKind.STRING, joined.toString(), List.of());
    }

    private static Value arrayValue(final String braced) {
        final String inner = braced.substring(1, braced.length() - 1);
        final List<Value> elements = new ArrayList<>();
        for (final String part : splitTopLevel(inner, ',')) {
            if (!part.trim().isEmpty()) {
                elements.add(value(part.trim()));
            }
        }
        return new Value(ValueKind.ARRAY, null, elements);
    }

    private static String numberText(final String written) {
        String text = written.replace("_", "");
        if (text.startsWith("+")) {
            text = text.substring(1);
        }
        if (HEX_OR_BINARY.matcher(text).matches()) {
            return text.replaceAll("[uUlL]+$", "");
        }
        return text.replaceAll("[uUlLfFdDmM]+$", "");
    }

    /** The content of a regular or verbatim string literal that makes up all of the text, or null. */
    private static String stringLiteral(final String text) {
        if (text.startsWith("@\"")) {
            final StringBuilder out = new StringBuilder();
            int i = 2;
            while (i < text.length()) {
                final char c = text.charAt(i);
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        out.append('"');
                        i += 2;
                        continue;
                    }
                    if (i == text.length() - 1) {
                        return out.toString();
                    }
                    return null;
                }
                out.append(c);
                i++;
            }
            return null;
        }
        if (!text.startsWith("\"") || text.startsWith("\"\"\"")) {
            return null;
        }
        final StringBuilder out = new StringBuilder();
        int i = 1;
        while (i < text.length()) {
            final char c = text.charAt(i);
            if (c == '"') {
                if (i == text.length() - 1) {
                    return out.toString();
                }
                return null;
            }
            if (c == '\\') {
                final int consumed = escape(text, i, out);
                if (consumed < 0) {
                    return null;
                }
                i += consumed;
                continue;
            }
            out.append(c);
            i++;
        }
        return null;
    }

    private static String charLiteral(final String text) {
        if (text.length() < 3 || !text.endsWith("'")) {
            return null;
        }
        final String body = text.substring(1, text.length() - 1);
        final StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < body.length()) {
            if (body.charAt(i) == '\\') {
                final int consumed = escape(body, i, out);
                if (consumed < 0) {
                    return null;
                }
                i += consumed;
            } else {
                out.append(body.charAt(i));
                i++;
            }
        }
        if (out.codePointCount(0, out.length()) == 1) {
            return out.toString();
        }
        return null;
    }

    /** Decodes the escape at {@code at}, appending it; returns the characters consumed, or -1. */
    private static int escape(final String text, final int at, final StringBuilder out) {
        if (at + 1 >= text.length()) {
            return -1;
        }
        final char next = text.charAt(at + 1);
        switch (next) {
            case 'n': out.append('\n'); return 2;
            case 't': out.append('\t'); return 2;
            case 'r': out.append('\r'); return 2;
            case '0': out.append('\0'); return 2;
            case '"': out.append('"'); return 2;
            case '\'': out.append('\''); return 2;
            case '\\': out.append('\\'); return 2;
            case 'u':
                if (at + 6 <= text.length() && text.substring(at + 2, at + 6).matches("[0-9a-fA-F]{4}")) {
                    out.append((char) Integer.parseInt(text.substring(at + 2, at + 6), 16));
                    return 6;
                }
                return -1;
            default:
                return -1;
        }
    }

    /** Whether the opening parenthesis of the text is the one the closing parenthesis ends. */
    private static boolean closesAtEnd(final String text) {
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            i = skipLiteral(text, i);
            final char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0 && i < text.length() - 1) {
                    return false;
                }
            }
        }
        return depth == 0;
    }

    /** Splits at a separator that is outside every string, character and bracket. */
    private static List<String> splitTopLevel(final String text, final char separator) {
        final List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            i = skipLiteral(text, i);
            final char c = text.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == separator && depth == 0 && !(separator == '+' && text.substring(0, i).isBlank())) {
                parts.add(text.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(text.substring(start));
        return parts;
    }

    /** If a string or character literal begins at {@code at}, the index of its last character. */
    private static int skipLiteral(final String text, final int at) {
        final char c = text.charAt(at);
        if (c == '@' && at + 1 < text.length() && text.charAt(at + 1) == '"') {
            int i = at + 2;
            while (i < text.length()) {
                if (text.charAt(i) == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        i += 2;
                        continue;
                    }
                    return i;
                }
                i++;
            }
            return text.length() - 1;
        }
        if (c == '"' || c == '\'') {
            int i = at + 1;
            while (i < text.length()) {
                if (text.charAt(i) == '\\') {
                    i += 2;
                    continue;
                }
                if (text.charAt(i) == c) {
                    return i;
                }
                i++;
            }
            return text.length() - 1;
        }
        return at;
    }
}
