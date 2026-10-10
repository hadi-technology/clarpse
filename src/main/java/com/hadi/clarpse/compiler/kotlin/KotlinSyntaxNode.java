package com.hadi.clarpse.compiler.kotlin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * One production of a Kotlin parse: its element type name, its range in the file's text, and the
 * productions nested in it. Tokens such as keywords and identifiers are not nodes; they are read
 * from the file's {@link KotlinSyntaxTree} by range.
 */
final class KotlinSyntaxNode {

    final String type;
    final int startOffset;
    final int endOffset;
    final List<KotlinSyntaxNode> children = new ArrayList<>();
    KotlinSyntaxNode parent;

    KotlinSyntaxNode(final String type, final int startOffset, final int endOffset) {
        this.type = type;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    boolean is(final String nodeType) {
        return this.type.equals(nodeType);
    }

    /** The first direct child of the given type, or null. */
    KotlinSyntaxNode child(final String nodeType) {
        for (final KotlinSyntaxNode child : children) {
            if (child.is(nodeType)) {
                return child;
            }
        }
        return null;
    }

    /** Every direct child of the given type, in source order. */
    List<KotlinSyntaxNode> children(final String nodeType) {
        final List<KotlinSyntaxNode> matches = new ArrayList<>();
        for (final KotlinSyntaxNode child : children) {
            if (child.is(nodeType)) {
                matches.add(child);
            }
        }
        return matches;
    }

    /** Every node below this one, in source order. */
    List<KotlinSyntaxNode> descendants() {
        final List<KotlinSyntaxNode> result = new ArrayList<>();
        final Deque<KotlinSyntaxNode> stack = new ArrayDeque<>();
        for (int i = children.size() - 1; i >= 0; i -= 1) {
            stack.push(children.get(i));
        }
        while (!stack.isEmpty()) {
            final KotlinSyntaxNode node = stack.pop();
            result.add(node);
            for (int i = node.children.size() - 1; i >= 0; i -= 1) {
                stack.push(node.children.get(i));
            }
        }
        return result;
    }

    /** Whether the given offset lies inside one of this node's direct children. */
    boolean childCovers(final int offset) {
        for (final KotlinSyntaxNode child : children) {
            if (offset >= child.startOffset && offset < child.endOffset) {
                return true;
            }
        }
        return false;
    }
}
