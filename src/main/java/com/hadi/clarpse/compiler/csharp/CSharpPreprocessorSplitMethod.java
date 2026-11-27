package com.hadi.clarpse.compiler.csharp;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Rebuilds a type member from the statement the C# syntax parser emits when a preprocessor
 * directive separates the member's signature from its body.
 */
final class CSharpPreprocessorSplitMethod {

    private CSharpPreprocessorSplitMethod() {
    }

    /**
     * Whether a type-body statement is a method whose signature and body a preprocessor directive
     * separates. The parser emits a call-shaped statement for that shape rather than a method
     * declaration.
     */
    static boolean isSplitMethod(final CSharpFileParser.SyntaxNode node) {
        if (!"cs:kops-statement".equals(node.type)) {
            return false;
        }
        final CSharpFileParser.SyntaxNode call = CSharpFileParser.firstChild(node, "cs:fun-call-role");
        final CSharpFileParser.SyntaxNode preprocessor = CSharpFileParser.firstChild(node, "cs:preprocessor");
        final CSharpFileParser.SyntaxNode block = CSharpFileParser.firstChild(node, "cs:block-list");
        return call != null && preprocessor != null && block != null
                && call.startOffset < preprocessor.startOffset
                && preprocessor.startOffset <= block.startOffset;
    }

    /**
     * Rebuilds a method or constructor from the statement the parser emits when a preprocessor
     * directive separates its signature from its body. The name and parameters come from the call
     * node; the return type and modifiers precede it in the source text. A statement whose member
     * cannot be recognised is left out, as it was before.
     */
    static CSharpModel.CSharpMemberModel parse(
            final CSharpFileParser.SyntaxNode statement,
            final CSharpModel.CSharpFileModel fileModel,
            final CSharpModel.CSharpTypeModel ownerType) {
        final CSharpFileParser.SyntaxNode call = CSharpFileParser.firstChild(statement, "cs:fun-call-role");
        final CSharpFileParser.SyntaxNode preprocessor = CSharpFileParser.firstChild(statement, "cs:preprocessor");
        final CSharpFileParser.SyntaxNode block = CSharpFileParser.firstChild(statement, "cs:block-list");
        final String sourceText = fileModel.sourceText;
        if (sourceText == null || statement.startOffset < 0 || statement.endOffset > sourceText.length()
                || call.startOffset < statement.startOffset || preprocessor.startOffset < call.startOffset
                || preprocessor.startOffset > statement.endOffset) {
            return null;
        }
        final String name = methodName(call);
        if (name.isEmpty() || name.startsWith("~") || name.contains("operator")) {
            return null;
        }
        final String declarationPrefix = sourceText.substring(statement.startOffset, call.startOffset);
        final String returnType = methodReturnType(declarationPrefix);
        if (returnType.startsWith("~") || declarationPrefix.contains("operator")) {
            return null;
        }
        final String kind;
        if (returnType.isEmpty()) {
            if (!name.equals(ownerType.name)) {
                return null;
            }
            kind = "constructor";
        } else {
            kind = "method";
        }
        final String signature = sourceText.substring(statement.startOffset, preprocessor.startOffset);
        final CSharpFileParser.SyntaxNode method =
                new CSharpFileParser.SyntaxNode("cs:method-declaration", statement.startOffset, statement.endOffset);
        method.text = signature + " " + block.text;
        method.parent = statement.parent;
        final CSharpModel.CSharpMemberModel member = CSharpFileParser.buildSingleMember(
                method,
                fileModel,
                ownerType,
                kind,
                name,
                null,
                returnType
        );
        final CSharpFileParser.SyntaxNode parentList = CSharpFileParser.firstChild(call, "cs:parent-list");
        final CSharpFileParser.SyntaxNode parameterList = parameterListWithParsedChildren(parentList);
        if (parentList != null && parameterList == null) {
            return null;
        }
        CSharpFileParser.populateCallable(
                member,
                method,
                fileModel,
                ownerType,
                parameterList,
                block,
                preprocessor.startOffset
        );
        final List<CSharpModel.CSharpAttribute> attributes = CSharpFileParser.precedingAttributes(statement);
        if (!attributes.isEmpty()) {
            member.annotations = new ArrayList<>(attributes);
        }
        return member;
    }

    /**
     * A call's parameter list with its parameter declarations. The syntax parser emits the list's
     * text but no declarations for a statement it wraps, so the text is parsed as a method's list.
     * Null is returned when the text cannot be parsed.
     */
    private static CSharpFileParser.SyntaxNode parameterListWithParsedChildren(
            final CSharpFileParser.SyntaxNode parentList) {
        if (parentList == null) {
            return null;
        }
        for (final CSharpFileParser.SyntaxNode child : parentList.children) {
            if ("cs:parameter-declaration".equals(child.type)) {
                return parentList;
            }
        }
        final String parameters = parentList.text;
        if (parameters == null || parameters.isBlank()) {
            return parentList;
        }
        final CSharpFileParser.SyntaxTree tree;
        try {
            final String source = "class __Parameters { void __Method" + parameters + " { } }";
            tree = CSharpFileParser.parseSyntaxTree(source, CSharpFileParser.validateBalancedDelimiters(source));
        } catch (final IllegalStateException e) {
            return null;
        }
        final CSharpFileParser.SyntaxNode method =
                CSharpFileParser.firstDescendant(tree.root(), "cs:method-declaration");
        if (method == null) {
            return null;
        }
        final CSharpFileParser.SyntaxNode parsed = CSharpFileParser.firstChild(method, "cs:parent-list");
        if (parsed == null) {
            return null;
        }
        final CSharpFileParser.SyntaxNode list =
                new CSharpFileParser.SyntaxNode("cs:parent-list", parentList.startOffset, parentList.endOffset);
        list.text = parentList.text;
        for (final CSharpFileParser.SyntaxNode child : parsed.children) {
            if ("cs:parameter-declaration".equals(child.type)) {
                child.parent = list;
                list.children.add(child);
            }
        }
        return list;
    }

    private static String methodName(final CSharpFileParser.SyntaxNode call) {
        final String identifier = CSharpFileParser.firstDirectChildText(call, "cs:id-role");
        if (identifier != null && !identifier.isEmpty()) {
            return identifier;
        }
        final String text = CSharpFileParser.normalizeWhitespace(call.text);
        final int parameters = text.indexOf('(');
        if (parameters < 0) {
            return text.trim();
        }
        return text.substring(0, parameters).trim();
    }

    private static String methodReturnType(final String prefix) {
        final String code =
                CSharpFileParser.normalizeWhitespace(CSharpBodyText.withoutCommentsAndStrings(prefix));
        final Set<String> modifiers = new LinkedHashSet<>(CSharpFileParser.parseModifiers(code));
        modifiers.addAll(CSharpDeclarationScanner.MODIFIERS);
        // `ref` starts a return type such as `ref int` or `ref readonly int`, not a modifier.
        modifiers.remove("ref");
        int start = 0;
        while (start < code.length()) {
            int end = code.indexOf(' ', start);
            if (end < 0) {
                end = code.length();
            }
            if (!modifiers.contains(code.substring(start, end).toLowerCase(Locale.ROOT))) {
                break;
            }
            start = end + 1;
        }
        if (start >= code.length()) {
            return "";
        }
        return code.substring(start).trim();
    }
}
