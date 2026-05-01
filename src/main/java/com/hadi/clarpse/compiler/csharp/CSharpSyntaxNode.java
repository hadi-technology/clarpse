package com.hadi.clarpse.compiler.csharp;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight syntax node used while rebuilding a navigable tree from the
 * flat JetBrains production stream. It keeps only the offsets, type, parent,
 * children, and recovered source slice needed by the C# extractor.
 */
final class CSharpSyntaxNode {

    final String type;
    final int startOffset;
    final int endOffset;
    final List<CSharpSyntaxNode> children = new ArrayList<>();
    CSharpSyntaxNode parent;
    String text = "";

    CSharpSyntaxNode(final String type, final int startOffset, final int endOffset) {
        this.type = type;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    boolean sameRange(final CSharpSyntaxNode other) {
        return other != null
                && this.startOffset == other.startOffset
                && this.endOffset == other.endOffset
                && this.type.equals(other.type);
    }
}
