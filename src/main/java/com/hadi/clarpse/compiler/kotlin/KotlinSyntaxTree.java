package com.hadi.clarpse.compiler.kotlin;

import fleet.com.intellij.lang.PsiBuilder;
import fleet.com.intellij.lang.SyntaxTreeBuilder.Production;
import fleet.com.intellij.psi.ArrayTokenSequence;
import fleet.com.intellij.psi.FleetPsiParser;
import fleet.com.intellij.psi.tree.IElementType;
import fleet.com.intellij.psi.tree.ILazyParseableElementType;
import fleet.com.intellij.psi.tree.TokenSet;
import fleet.com.jetbrains.lang.parsing.builder.MarkerPsiBuilder;
import fleet.org.jetbrains.kotlin.KotlinPsiParser;
import fleet.org.jetbrains.kotlin.KtNodeTypes;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * The parse of one Kotlin file: a tree of {@link KotlinSyntaxNode}s over the file's tokens.
 *
 * <p>The parser leaves function bodies, initializer blocks and lambdas as lazily parseable nodes
 * whose contents are not productions. A tree built with bodies re-parses each such node from its
 * own text and grafts the result in place, recursively, so a body's expressions are nodes like any
 * other. A tree built without bodies keeps them as leaves, which is all a declaration needs and
 * costs a fraction of a full parse.
 *
 * <p>Keywords and identifiers are tokens, not nodes; {@link #directTokens(KotlinSyntaxNode)} reads
 * the significant tokens a node holds outside its children.
 */
final class KotlinSyntaxTree {

    /** A significant token: its type name, its range, and its text. */
    record Token(String type, int start, int end, String text) {
    }

    private static final String FILE = "FILE";

    private final String source;
    private final ArrayTokenSequence tokens;
    private final TokenSet whitespaces;
    private final TokenSet comments;
    private final KotlinSyntaxNode root;

    private KotlinSyntaxTree(final String source, final ArrayTokenSequence tokens, final TokenSet whitespaces,
                             final TokenSet comments, final KotlinSyntaxNode root) {
        this.source = source;
        this.tokens = tokens;
        this.whitespaces = whitespaces;
        this.comments = comments;
        this.root = root;
    }

    /**
     * Parses a file.
     *
     * @param source     The file's text.
     * @param withBodies Whether lazily parsed bodies are parsed too.
     * @return The tree.
     * @throws IllegalStateException When the text's delimiters do not balance or nothing was parsed.
     */
    static KotlinSyntaxTree parse(final String source, final boolean withBodies) {
        final FleetPsiParser parser = new KotlinPsiParser();
        final ArrayTokenSequence tokens = new ArrayTokenSequence.Builder(source, parser.getLexer()).performLexing();
        requireBalancedDelimiters(tokens);
        final KotlinSyntaxNode root = build(source, tokens, parser, 0, parser::parse);
        if (root == null) {
            throw new IllegalStateException("No syntax nodes produced for source.");
        }
        if (withBodies) {
            expand(root, source, parser);
        }
        return new KotlinSyntaxTree(source, tokens, parser.getWhitespaces(), parser.getComments(), root);
    }

    private static KotlinSyntaxNode build(final String text, final ArrayTokenSequence tokens,
                                          final FleetPsiParser parser, final int base,
                                          final Consumer<PsiBuilder> parse) {
        final PsiBuilder builder = new MarkerPsiBuilder(text, tokens, parser.getWhitespaces(), parser.getComments(),
                0, tokens.getLexemeCount());
        parse.accept(builder);
        final Deque<KotlinSyntaxNode> stack = new ArrayDeque<>();
        final Deque<Boolean> collapsed = new ArrayDeque<>();
        KotlinSyntaxNode root = null;
        for (final Production production : builder.getProductions()) {
            final IElementType elementType = production.getTokenType();
            final KotlinSyntaxNode current = new KotlinSyntaxNode(String.valueOf(elementType),
                    base + production.getStartOffset(), base + production.getEndOffset());
            final KotlinSyntaxNode open = stack.peek();
            if (open != null && open.startOffset == current.startOffset && open.endOffset == current.endOffset
                    && open.type.equals(current.type)) {
                stack.pop();
                final boolean wasCollapsed = collapsed.pop();
                if (wasCollapsed && elementType instanceof ILazyParseableElementType) {
                    // A collapsed node reports only part of its contents; it is re-parsed whole.
                    open.children.clear();
                }
                continue;
            }
            if (root == null) {
                root = current;
            } else if (open != null) {
                current.parent = open;
                open.children.add(current);
            }
            stack.push(current);
            collapsed.push(production.isCollapsed());
        }
        return root;
    }

    /** Re-parses every lazily parseable leaf under {@code node} and grafts its contents in. */
    private static void expand(final KotlinSyntaxNode node, final String source, final FleetPsiParser parser) {
        final Deque<KotlinSyntaxNode> pending = new ArrayDeque<>();
        pending.push(node);
        while (!pending.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Interrupted while parsing Kotlin bodies.");
            }
            final KotlinSyntaxNode current = pending.pop();
            if (current.children.isEmpty() && current.endOffset > current.startOffset && !current.is(FILE)) {
                graftLazyContents(current, source, parser);
            }
            for (final KotlinSyntaxNode child : current.children) {
                pending.push(child);
            }
        }
    }

    private static void graftLazyContents(final KotlinSyntaxNode node, final String source,
                                          final FleetPsiParser parser) {
        final IElementType elementType = lazyType(node.type);
        if (elementType == null) {
            return;
        }
        final ILazyParseableElementType lazy = (ILazyParseableElementType) elementType;
        final String text = source.substring(node.startOffset, Math.min(node.endOffset, source.length()));
        final ArrayTokenSequence innerTokens = new ArrayTokenSequence.Builder(text, parser.getLexer()).performLexing();
        final KotlinSyntaxNode reparsed = build(text, innerTokens, parser, node.startOffset, lazy::parse);
        if (reparsed == null) {
            return;
        }
        for (final KotlinSyntaxNode child : reparsed.children) {
            child.parent = node;
            node.children.add(child);
        }
    }

    /** The lazily parseable element types a body can be left as. */
    private static IElementType lazyType(final String typeName) {
        switch (typeName) {
            case "BLOCK":
                return KtNodeTypes.BLOCK;
            case "LAMBDA_EXPRESSION":
                return KtNodeTypes.LAMBDA_EXPRESSION;
            default:
                return null;
        }
    }

    /**
     * Rejects text whose braces, parentheses or brackets do not balance. The parser recovers from
     * anything, so without this a truncated file would be modelled as though it were whole.
     */
    private static void requireBalancedDelimiters(final ArrayTokenSequence tokens) {
        int braces = 0;
        int parens = 0;
        int brackets = 0;
        for (int i = 0; i < tokens.getLexemeCount(); i += 1) {
            switch (String.valueOf(tokens.lexType(i))) {
                case "LBRACE":
                    braces += 1;
                    break;
                case "RBRACE":
                    braces -= 1;
                    break;
                case "LPAR":
                    parens += 1;
                    break;
                case "RPAR":
                    parens -= 1;
                    break;
                case "LBRACKET":
                    brackets += 1;
                    break;
                case "RBRACKET":
                    brackets -= 1;
                    break;
                default:
                    break;
            }
            if (braces < 0 || parens < 0 || brackets < 0) {
                break;
            }
        }
        if (braces != 0 || parens != 0 || brackets != 0) {
            throw new IllegalStateException("Unbalanced delimiters in Kotlin source.");
        }
    }

    KotlinSyntaxNode root() {
        return root;
    }

    String source() {
        return source;
    }

    /** The node's text, without the whitespace and comments that trail its last token. */
    String text(final KotlinSyntaxNode node) {
        final int end = significantEnd(node.startOffset, node.endOffset);
        return source.substring(Math.min(node.startOffset, end), end);
    }

    /** The significant tokens in the node's range that lie outside all of its children. */
    List<Token> directTokens(final KotlinSyntaxNode node) {
        final List<Token> result = new ArrayList<>();
        if (tokens.getLexemeCount() == 0 || node.endOffset <= node.startOffset) {
            return result;
        }
        int index = tokens.lexemeIndexByChar(Math.min(node.startOffset, tokens.getTextLength() - 1));
        while (index >= 0 && index < tokens.getLexemeCount() && tokens.lexStart(index) < node.endOffset) {
            final int start = tokens.lexStart(index);
            if (start >= node.startOffset && !isTrivia(index) && !node.childCovers(start)) {
                final int end = lexEnd(index);
                result.add(new Token(String.valueOf(tokens.lexType(index)), start, end, source.substring(start, end)));
            }
            index += 1;
        }
        return result;
    }

    /**
     * The comments written immediately before an offset, separated from it by whitespace only, in
     * source order and joined by line breaks.
     */
    String leadingComment(final int offset) {
        if (tokens.getLexemeCount() == 0 || offset <= 0) {
            return "";
        }
        int index = tokens.lexemeIndexByChar(Math.min(offset, tokens.getTextLength()) - 1);
        while (index >= 0 && tokens.lexStart(index) >= offset) {
            index -= 1;
        }
        final List<String> found = new ArrayList<>();
        while (index >= 0 && isTrivia(index)) {
            if (comments.contains(tokens.lexType(index))) {
                found.add(0, source.substring(tokens.lexStart(index), lexEnd(index)).trim());
            }
            index -= 1;
        }
        return String.join("\n", found);
    }

    /** Every significant token in the node's range, children included. */
    List<Token> allTokens(final KotlinSyntaxNode node) {
        final List<Token> result = new ArrayList<>();
        if (tokens.getLexemeCount() == 0 || node.endOffset <= node.startOffset) {
            return result;
        }
        int index = tokens.lexemeIndexByChar(Math.min(node.startOffset, tokens.getTextLength() - 1));
        while (index >= 0 && index < tokens.getLexemeCount() && tokens.lexStart(index) < node.endOffset) {
            final int start = tokens.lexStart(index);
            if (start >= node.startOffset && !isTrivia(index)) {
                final int end = lexEnd(index);
                result.add(new Token(String.valueOf(tokens.lexType(index)), start, end, source.substring(start, end)));
            }
            index += 1;
        }
        return result;
    }

    private int significantEnd(final int start, final int end) {
        if (end <= start || tokens.getLexemeCount() == 0) {
            return Math.min(end, source.length());
        }
        int index = tokens.lexemeIndexByChar(Math.min(end, tokens.getTextLength()) - 1);
        while (index >= 0 && tokens.lexStart(index) >= start && isTrivia(index)) {
            index -= 1;
        }
        if (index < 0 || tokens.lexStart(index) < start) {
            return start;
        }
        return Math.max(start, Math.min(end, lexEnd(index)));
    }

    private boolean isTrivia(final int index) {
        final IElementType type = tokens.lexType(index);
        return whitespaces.contains(type) || comments.contains(type);
    }

    private int lexEnd(final int index) {
        if (index + 1 < tokens.getLexemeCount()) {
            return tokens.lexStart(index + 1);
        }
        return tokens.getTextLength();
    }
}
