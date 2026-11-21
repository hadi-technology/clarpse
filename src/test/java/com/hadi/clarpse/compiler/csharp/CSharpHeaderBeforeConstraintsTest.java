package com.hadi.clarpse.compiler.csharp;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

/** The fallback a type header reads when its file carries no usable source text. */
public class CSharpHeaderBeforeConstraintsTest {

    @Test
    public void headerFallsBackToNodeTextWithoutSourceText() throws Exception {
        assertEquals("public ref struct R { }", headerBeforeConstraints(syntaxNode(0, 22), null));
    }

    @Test
    public void headerFallsBackToNodeTextWhenTheHeaderEndIsPastTheSource() throws Exception {
        assertEquals("public ref struct R { }", headerBeforeConstraints(syntaxNode(0, 22), "short"));
    }

    @Test
    public void headerFallsBackToNodeTextWhenTheStartIsPastTheHeaderEnd() throws Exception {
        assertEquals("public ref struct R { }", headerBeforeConstraints(syntaxNode(10, 5), "a long enough source"));
    }

    private static Object syntaxNode(final int startOffset, final int endOffset) throws Exception {
        final Class<?> nodeClass = Class.forName("com.hadi.clarpse.compiler.csharp.CSharpFileParser$SyntaxNode");
        final Constructor<?> constructor =
                nodeClass.getDeclaredConstructor(String.class, int.class, int.class);
        constructor.setAccessible(true);
        final Object node = constructor.newInstance("cs:struct-declaration", startOffset, endOffset);
        final Field text = nodeClass.getDeclaredField("text");
        text.setAccessible(true);
        text.set(node, "public ref struct R { }");
        return node;
    }

    private static String headerBeforeConstraints(final Object node, final String sourceText) throws Exception {
        final Method method = CSharpFileParser.class.getDeclaredMethod(
                "headerBeforeConstraints", node.getClass(), String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, node, sourceText);
    }
}
