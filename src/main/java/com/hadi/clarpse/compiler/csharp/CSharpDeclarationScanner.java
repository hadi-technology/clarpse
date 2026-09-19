package com.hadi.clarpse.compiler.csharp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Reads the type declarations of one C# file from its text, without a parse tree.
 *
 * <p>The scanner skips comments, string literals (regular, verbatim, raw and interpolated, with
 * nested holes), character literals and preprocessor lines; tracks brace scopes; and records each
 * namespace, block-scoped or file-scoped, and each type declared in it, nested types included, with
 * the unique name the C# assembler gives the same type. A leading byte-order mark is ignored.
 *
 * <p>It is a lexical approximation: it does not evaluate preprocessor conditionals, and a construct
 * it does not recognise yields no declaration rather than a wrong one.
 */
final class CSharpDeclarationScanner {

    /**
     * One declared type.
     *
     * @param kind          the kind, spelled as the C# parser spells it
     * @param name          the simple name
     * @param namespaceName the enclosing namespace, empty for none
     * @param componentName the name within the namespace, outer types first
     * @param uniqueName    the namespace and component name
     * @param partial       whether the declaration is marked {@code partial}
     */
    record Declaration(String kind, String name, String namespaceName, String componentName,
                       String uniqueName, boolean partial) {
    }

    private static final Set<String> TYPE_KEYWORDS = Set.of("class", "struct", "interface", "enum", "record");
    private static final Set<String> MODIFIERS = Set.of("public", "private", "protected", "internal", "static",
            "sealed", "abstract", "partial", "readonly", "ref", "unsafe", "new", "file", "extern");

    private enum ScopeKind { OTHER, NAMESPACE, TYPE }

    private record Scope(ScopeKind kind, String name) {
    }

    private final String text;
    private final List<Declaration> declarations = new ArrayList<>();
    private final Deque<Scope> scopes = new ArrayDeque<>();
    private final List<String> recent = new ArrayList<>();
    private int position;
    private String fileScopedNamespace = "";
    private String previous = "";
    private Scope pending;
    private int parenDepth;

    private CSharpDeclarationScanner(final String text) {
        if (text.startsWith("﻿")) {
            this.text = text.substring(1);
        } else {
            this.text = text;
        }
    }

    /**
     * The types a file declares, in source order.
     *
     * @param text The file's text; {@code null} is read as empty.
     * @return The declarations.
     */
    static List<Declaration> scan(final String text) {
        String source = text;
        if (source == null) {
            source = "";
        }
        final CSharpDeclarationScanner scanner = new CSharpDeclarationScanner(source);
        scanner.run();
        return scanner.declarations;
    }

    private String currentNamespace() {
        final StringBuilder builder = new StringBuilder(fileScopedNamespace);
        final Iterator<Scope> outermostFirst = scopes.descendingIterator();
        while (outermostFirst.hasNext()) {
            final Scope scope = outermostFirst.next();
            if (scope.kind() == ScopeKind.NAMESPACE) {
                if (builder.length() > 0) {
                    builder.append('.');
                }
                builder.append(scope.name());
            }
        }
        return builder.toString();
    }

    private String enclosingTypes() {
        final StringBuilder builder = new StringBuilder();
        final Iterator<Scope> outermostFirst = scopes.descendingIterator();
        while (outermostFirst.hasNext()) {
            final Scope scope = outermostFirst.next();
            if (scope.kind() == ScopeKind.TYPE) {
                if (builder.length() > 0) {
                    builder.append('.');
                }
                builder.append(scope.name());
            }
        }
        return builder.toString();
    }

    private void run() {
        final int length = text.length();
        boolean lineStart = true;
        while (position < length) {
            final char c = text.charAt(position);
            if (c == '\n') {
                lineStart = true;
                position++;
                continue;
            }
            if (Character.isWhitespace(c)) {
                position++;
                continue;
            }
            if (lineStart && c == '#') {
                skipToLineEnd();
                continue;
            }
            lineStart = false;
            if (c == '/' && position + 1 < length && text.charAt(position + 1) == '/') {
                skipToLineEnd();
                continue;
            }
            if (c == '/' && position + 1 < length && text.charAt(position + 1) == '*') {
                position = blockCommentEnd(position);
                continue;
            }
            if (isStringStart(position)) {
                position = skipString(position);
                previous = "\"";
                continue;
            }
            if (c == '\'') {
                position = skipChar(position);
                previous = "'";
                continue;
            }
            if (Character.isLetter(c) || c == '_' || c == '@') {
                final int start = position;
                position++;
                while (position < length && isIdentifierPart(text.charAt(position))) {
                    position++;
                }
                final String raw = text.substring(start, position);
                onIdentifier(raw.replace("@", ""), raw.startsWith("@"));
                continue;
            }
            onPunctuation(c);
            previous = String.valueOf(c);
            position++;
        }
    }

    private void onPunctuation(final char c) {
        switch (c) {
            case '{':
                if (pending != null && parenDepth == 0) {
                    scopes.push(pending);
                    pending = null;
                } else {
                    scopes.push(new Scope(ScopeKind.OTHER, ""));
                }
                recent.clear();
                break;
            case '}':
                if (!scopes.isEmpty()) {
                    scopes.pop();
                }
                recent.clear();
                pending = null;
                parenDepth = 0;
                break;
            case ';':
                if (parenDepth == 0) {
                    pending = null;
                    recent.clear();
                }
                break;
            case '(':
                parenDepth++;
                break;
            case ')':
                parenDepth = Math.max(0, parenDepth - 1);
                break;
            case ']':
                recent.clear();
                break;
            default:
                break;
        }
    }

    private static boolean isIdentifierPart(final char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private void skipToLineEnd() {
        while (position < text.length() && text.charAt(position) != '\n') {
            position++;
        }
    }

    private int blockCommentEnd(final int from) {
        final int end = text.indexOf("*/", from + 2);
        if (end < 0) {
            return text.length();
        }
        return end + 2;
    }

    private void onIdentifier(final String identifier, final boolean verbatim) {
        final String before = previous;
        previous = identifier;
        if (verbatim) {
            recent.add(identifier);
            return;
        }
        if ("record".equals(identifier) && !(before.isEmpty() || MODIFIERS.contains(before)
                || ";{}]".contains(before))) {
            recent.add(identifier);
            return;
        }
        if ("namespace".equals(identifier) && parenDepth == 0) {
            final String name = readQualifiedName();
            final int next = skipWhitespace(position);
            if (next < text.length() && text.charAt(next) == ';') {
                fileScopedNamespace = name;
            } else {
                pending = new Scope(ScopeKind.NAMESPACE, name);
            }
            previous = name;
            return;
        }
        if (TYPE_KEYWORDS.contains(identifier) && parenDepth == 0 && !":".equals(before) && !",".equals(before)) {
            onTypeKeyword(identifier);
            return;
        }
        if ("delegate".equals(identifier) && parenDepth == 0) {
            onDelegate();
            return;
        }
        recent.add(identifier);
    }

    private void onTypeKeyword(final String keyword) {
        String kind = keyword;
        final int save = position;
        String next = peekIdentifier();
        if ("record".equals(keyword) && ("struct".equals(next) || "class".equals(next))) {
            if ("struct".equals(next)) {
                kind = "recordStruct";
            }
            position = skipWhitespace(position) + next.length();
            next = peekIdentifier();
        }
        if (next == null || TYPE_KEYWORDS.contains(next) || "where".equals(next)) {
            position = save;
            recent.add(keyword);
            return;
        }
        position = skipWhitespace(position) + next.length();
        final int follower = skipWhitespace(position);
        char followingChar = ';';
        if (follower < text.length()) {
            followingChar = text.charAt(follower);
        }
        if ("record".equals(keyword) && "(<{:;".indexOf(followingChar) < 0 && !text.startsWith("where", follower)) {
            position = save;
            recent.add(keyword);
            return;
        }
        declare(kind, next);
    }

    /** {@code delegate <return type> Name<...>(...);} declares a type; an anonymous delegate does not. */
    private void onDelegate() {
        int index = position;
        String last = null;
        int identifiers = 0;
        int angle = 0;
        while (index < text.length()) {
            final char c = text.charAt(index);
            if (c == '<') {
                angle++;
            } else if (c == '>') {
                angle--;
            } else if (angle == 0 && (c == '(' || c == '{' || c == ';')) {
                break;
            } else if (angle == 0 && (Character.isLetter(c) || c == '_')) {
                final int start = index;
                while (index < text.length() && isIdentifierPart(text.charAt(index))) {
                    index++;
                }
                last = text.substring(start, index);
                identifiers++;
                continue;
            }
            index++;
        }
        if (identifiers >= 2 && last != null) {
            record("delegate", last);
        }
    }

    private void declare(final String kind, final String name) {
        record(kind, name);
        pending = new Scope(ScopeKind.TYPE, name);
        recent.clear();
    }

    private void record(final String kind, final String name) {
        final String owner = enclosingTypes();
        final String namespace = currentNamespace();
        String componentName = name;
        if (!owner.isEmpty()) {
            componentName = owner + "." + name;
        }
        String uniqueName = componentName;
        if (!namespace.isEmpty()) {
            uniqueName = namespace + "." + componentName;
        }
        declarations.add(new Declaration(kind, name, namespace, componentName, uniqueName,
                recent.contains("partial")));
    }

    private String peekIdentifier() {
        int index = skipWhitespace(position);
        if (index < text.length() && text.charAt(index) == '@') {
            index++;
        }
        final int start = index;
        while (index < text.length() && isIdentifierPart(text.charAt(index))) {
            index++;
        }
        if (index == start || !(Character.isLetter(text.charAt(start)) || text.charAt(start) == '_')) {
            return null;
        }
        return text.substring(start, index);
    }

    private String readQualifiedName() {
        final StringBuilder builder = new StringBuilder();
        while (true) {
            int index = skipWhitespace(position);
            final int start = index;
            while (index < text.length() && (isIdentifierPart(text.charAt(index)) || text.charAt(index) == '@')) {
                index++;
            }
            if (index == start) {
                break;
            }
            builder.append(text, start, index);
            position = index;
            final int next = skipWhitespace(position);
            if (next < text.length() && text.charAt(next) == '.') {
                builder.append('.');
                position = next + 1;
            } else {
                break;
            }
        }
        return builder.toString().replace("@", "");
    }

    private int skipWhitespace(final int from) {
        int index = from;
        while (index < text.length()) {
            final char c = text.charAt(index);
            if (Character.isWhitespace(c)) {
                index++;
            } else if (c == '/' && index + 1 < text.length() && text.charAt(index + 1) == '/') {
                while (index < text.length() && text.charAt(index) != '\n') {
                    index++;
                }
            } else if (c == '/' && index + 1 < text.length() && text.charAt(index + 1) == '*') {
                index = blockCommentEnd(index);
            } else {
                break;
            }
        }
        return index;
    }

    private boolean isStringStart(final int index) {
        final int length = text.length();
        final char c = text.charAt(index);
        if (c == '"') {
            return true;
        }
        if ((c == '$' || c == '@') && index + 1 < length) {
            final char d = text.charAt(index + 1);
            if (d == '"') {
                return true;
            }
            if ((d == '$' || d == '@') && index + 2 < length && text.charAt(index + 2) == '"') {
                return true;
            }
            if (c == '$' && d == '$') {
                int k = index;
                while (k < length && text.charAt(k) == '$') {
                    k++;
                }
                return k < length && text.charAt(k) == '"';
            }
        }
        return false;
    }

    /** The index just past the string literal starting at {@code from}. */
    private int skipString(final int from) {
        final int length = text.length();
        int index = from;
        boolean interpolated = false;
        boolean verbatim = false;
        int dollars = 0;
        while (index < length && (text.charAt(index) == '$' || text.charAt(index) == '@')) {
            if (text.charAt(index) == '$') {
                interpolated = true;
                dollars++;
            } else {
                verbatim = true;
            }
            index++;
        }
        int quotes = 0;
        while (index + quotes < length && text.charAt(index + quotes) == '"') {
            quotes++;
        }
        if (quotes >= 3) {
            return skipRawString(index, quotes, interpolated, dollars);
        }
        index++;
        while (index < length) {
            final char c = text.charAt(index);
            if (verbatim) {
                if (c == '"') {
                    if (index + 1 < length && text.charAt(index + 1) == '"') {
                        index += 2;
                        continue;
                    }
                    return index + 1;
                }
            } else {
                if (c == '\\') {
                    index += 2;
                    continue;
                }
                if (c == '"' || c == '\n') {
                    return index + 1;
                }
            }
            if (interpolated && c == '{') {
                if (index + 1 < length && text.charAt(index + 1) == '{') {
                    index += 2;
                    continue;
                }
                index = skipHole(index + 1);
                continue;
            }
            index++;
        }
        return length;
    }

    private int skipRawString(final int from, final int quotes, final boolean interpolated, final int dollars) {
        final String close = "\"".repeat(quotes);
        final String holeOpen = "{".repeat(Math.max(1, dollars));
        int index = from + quotes;
        while (index < text.length()) {
            if (text.startsWith(close, index)) {
                return index + quotes;
            }
            if (interpolated && text.charAt(index) == '{' && text.startsWith(holeOpen, index)) {
                index = skipHole(index + dollars);
                continue;
            }
            index++;
        }
        return text.length();
    }

    /** The index just past the closing brace of an interpolation hole whose code starts at {@code from}. */
    private int skipHole(final int from) {
        int index = from;
        int depth = 0;
        while (index < text.length()) {
            final char c = text.charAt(index);
            if (isStringStart(index)) {
                index = skipString(index);
                continue;
            }
            if (c == '\'') {
                index = skipChar(index);
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                if (depth == 0) {
                    return index + 1;
                }
                depth--;
            }
            index++;
        }
        return text.length();
    }

    private int skipChar(final int from) {
        int index = from + 1;
        while (index < text.length()) {
            final char c = text.charAt(index);
            if (c == '\\') {
                index += 2;
                continue;
            }
            if (c == '\'' || c == '\n') {
                return index + 1;
            }
            index++;
        }
        return text.length();
    }
}
