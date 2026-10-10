package com.hadi.clarpse.compiler.kotlin;

/**
 * The package a Kotlin file's header declares, read from its text without parsing it. A Kotlin file
 * opens with an optional shebang line, then its file annotations, then an optional
 * {@code package} directive, with whitespace and comments anywhere between them; a file with no
 * directive is in the root package. Anything this reader is not certain how to skip makes the
 * package unknown rather than guessed, so a caller can treat such a file as possibly in any package.
 */
final class KotlinPackageHeader {

    private final String text;
    private int pos;

    private KotlinPackageHeader(final String text) {
        this.text = text;
    }

    /**
     * The package a Kotlin file declares.
     *
     * @param text The file's text.
     * @return The package's qualified name, the empty string for the root package, or null when the
     *         header could not be read with certainty.
     */
    static String of(final String text) {
        if (text == null) {
            return null;
        }
        return new KotlinPackageHeader(text).read();
    }

    private String read() {
        if (text.startsWith("﻿")) {
            pos = 1;
        }
        if (text.startsWith("#!", pos)) {
            skipLine();
        }
        while (true) {
            if (!skipTrivia()) {
                return null;
            }
            if (!text.startsWith("@file", pos)) {
                break;
            }
            if (!skipFileAnnotation()) {
                return null;
            }
        }
        if (!keywordAt("package")) {
            return "";
        }
        pos += "package".length();
        return qualifiedName();
    }

    /** Skips whitespace and comments; false on an unterminated block comment. */
    private boolean skipTrivia() {
        while (pos < text.length()) {
            final char ch = text.charAt(pos);
            if (Character.isWhitespace(ch)) {
                pos += 1;
            } else if (text.startsWith("//", pos)) {
                skipLine();
            } else if (text.startsWith("/*", pos)) {
                if (!skipBlockComment()) {
                    return false;
                }
            } else {
                return true;
            }
        }
        return true;
    }

    private void skipLine() {
        while (pos < text.length() && text.charAt(pos) != '\n' && text.charAt(pos) != '\r') {
            pos += 1;
        }
    }

    /** Skips a block comment, which Kotlin lets nest; false when it is not terminated. */
    private boolean skipBlockComment() {
        int depth = 0;
        while (pos < text.length()) {
            if (text.startsWith("/*", pos)) {
                depth += 1;
                pos += 2;
            } else if (text.startsWith("*/", pos)) {
                depth -= 1;
                pos += 2;
                if (depth == 0) {
                    return true;
                }
            } else {
                pos += 1;
            }
        }
        return false;
    }

    /**
     * Skips {@code @file:Name}, {@code @file:a.b.Name(args)} or {@code @file:[A B(args)]}; false on
     * anything else, including type arguments and string templates, which no header needs.
     */
    private boolean skipFileAnnotation() {
        pos += "@file".length();
        if (pos < text.length() && isIdentifierPart(text.charAt(pos))) {
            return false;
        }
        if (!skipTrivia() || pos >= text.length() || text.charAt(pos) != ':') {
            return false;
        }
        pos += 1;
        if (!skipTrivia() || pos >= text.length()) {
            return false;
        }
        if (text.charAt(pos) == '[') {
            return skipBalanced('[', ']');
        }
        return skipAnnotationEntry();
    }

    private boolean skipAnnotationEntry() {
        if (qualifiedName() == null) {
            return false;
        }
        final int afterName = pos;
        if (!skipTrivia()) {
            return false;
        }
        if (pos < text.length() && text.charAt(pos) == '<') {
            return false;
        }
        if (pos < text.length() && text.charAt(pos) == '(') {
            return skipBalanced('(', ')');
        }
        pos = afterName;
        return true;
    }

    /** Skips from an opening bracket to its match, past strings, characters and comments. */
    private boolean skipBalanced(final char open, final char close) {
        int depth = 0;
        while (pos < text.length()) {
            final char ch = text.charAt(pos);
            if (ch == open) {
                depth += 1;
                pos += 1;
            } else if (ch == close) {
                depth -= 1;
                pos += 1;
                if (depth == 0) {
                    return true;
                }
            } else if (ch == '"') {
                if (!skipString()) {
                    return false;
                }
            } else if (ch == '\'') {
                if (!skipCharacter()) {
                    return false;
                }
            } else if (text.startsWith("//", pos)) {
                skipLine();
            } else if (text.startsWith("/*", pos)) {
                if (!skipBlockComment()) {
                    return false;
                }
            } else {
                pos += 1;
            }
        }
        return false;
    }

    private boolean skipString() {
        if (text.startsWith("\"\"\"", pos)) {
            final int end = text.indexOf("\"\"\"", pos + 3);
            if (end < 0 || text.substring(pos, end).contains("${")) {
                return false;
            }
            pos = end + 3;
            while (pos < text.length() && text.charAt(pos) == '"') {
                pos += 1;
            }
            return true;
        }
        pos += 1;
        while (pos < text.length()) {
            final char ch = text.charAt(pos);
            if (ch == '\\') {
                pos += 2;
            } else if (ch == '"') {
                pos += 1;
                return true;
            } else if (ch == '\n' || ch == '\r' || text.startsWith("${", pos)) {
                return false;
            } else {
                pos += 1;
            }
        }
        return false;
    }

    private boolean skipCharacter() {
        pos += 1;
        while (pos < text.length()) {
            final char ch = text.charAt(pos);
            if (ch == '\\') {
                pos += 2;
            } else if (ch == '\'') {
                pos += 1;
                return true;
            } else if (ch == '\n' || ch == '\r') {
                return false;
            } else {
                pos += 1;
            }
        }
        return false;
    }

    private boolean keywordAt(final String keyword) {
        return text.startsWith(keyword, pos)
                && (pos + keyword.length() >= text.length() || !isIdentifierPart(text.charAt(pos + keyword.length())));
    }

    /**
     * Reads {@code a.b.c}, whose parts may be backticked and may have whitespace around the dots;
     * null when no identifier follows, or a comment sits inside the name.
     */
    private String qualifiedName() {
        final StringBuilder name = new StringBuilder();
        while (true) {
            skipWhitespace();
            final String part = identifier();
            if (part == null) {
                return null;
            }
            name.append(part);
            final int afterPart = pos;
            if (!skipTrivia()) {
                return null;
            }
            if (pos >= text.length() || text.charAt(pos) != '.') {
                pos = afterPart;
                return name.toString();
            }
            if (text.substring(afterPart, pos).indexOf('/') >= 0) {
                return null;
            }
            pos += 1;
            name.append('.');
            final int afterDot = pos;
            skipWhitespace();
            if (text.startsWith("//", pos) || text.startsWith("/*", pos)) {
                return null;
            }
            pos = afterDot;
        }
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos += 1;
        }
    }

    private String identifier() {
        if (pos >= text.length()) {
            return null;
        }
        if (text.charAt(pos) == '`') {
            final int end = text.indexOf('`', pos + 1);
            if (end < 0 || end == pos + 1 || text.substring(pos + 1, end).indexOf('\n') >= 0) {
                return null;
            }
            final String part = text.substring(pos + 1, end);
            pos = end + 1;
            return part;
        }
        if (!Character.isLetter(text.charAt(pos)) && text.charAt(pos) != '_') {
            return null;
        }
        final int start = pos;
        while (pos < text.length() && isIdentifierPart(text.charAt(pos))) {
            pos += 1;
        }
        return text.substring(start, pos);
    }

    private static boolean isIdentifierPart(final char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_';
    }
}
