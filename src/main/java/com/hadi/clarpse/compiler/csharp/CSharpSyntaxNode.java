package com.hadi.clarpse.compiler.csharp;

import java.util.ArrayList;
import java.util.List;

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
