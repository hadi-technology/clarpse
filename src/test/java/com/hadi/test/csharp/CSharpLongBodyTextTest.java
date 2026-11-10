package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.ProjectFile;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * The text scans over a member body read a body of any length: none of them recurses once per
 * repetition of a long dotted chain, a long generic argument list or a long run of one kind of
 * character, and none takes more than a bounded time on one.
 */
public class CSharpLongBodyTextTest {

    private static final int LENGTH = 200_000;

    private static void readsInBoundedTime(final String body) throws Exception {
        final long start = System.nanoTime();
        final CompileResult result = CSharpTestUtil.compileInline(new ProjectFile("/U.cs",
                "namespace App { public class U { public void M(object o) { " + body + " } } }"));
        assertTrue(result.failures().isEmpty());
        assertTrue(result.model().components().anyMatch(c -> c.uniqueName().startsWith("App.U.M(")));
        assertTrue("took " + (System.nanoTime() - start) / 1_000_000 + " ms",
                (System.nanoTime() - start) < 20_000_000_000L);
    }

    @Test(timeout = 60_000)
    public void aLongDottedReceiverChain() throws Exception {
        readsInBoundedTime("var x = A" + ".b".repeat(LENGTH / 2) + ".c;");
    }

    @Test(timeout = 60_000)
    public void aLongNestedGenericReceiver() throws Exception {
        readsInBoundedTime("var x = A<" + "<x>".repeat(LENGTH / 3) + ">.C;");
    }

    @Test(timeout = 60_000)
    public void aLongRunOfOpeningGenericBracketsInCalls() throws Exception {
        readsInBoundedTime("var x = " + "a.b<c.d ".repeat(LENGTH / 8) + ";");
    }

    @Test(timeout = 60_000)
    public void aLongTypeTestArgumentList() throws Exception {
        readsInBoundedTime("var x = o is A<" + "a".repeat(LENGTH) + ">;");
    }

    @Test(timeout = 60_000)
    public void aLongTypeTestWithoutAClosingBracket() throws Exception {
        readsInBoundedTime("var x = o is A<" + "a".repeat(LENGTH) + ";");
    }

    @Test(timeout = 60_000)
    public void aLongTypeTestOfNestedArguments() throws Exception {
        readsInBoundedTime("var x = o is A<" + "<a>".repeat(LENGTH / 3) + ">;");
    }

    @Test(timeout = 60_000)
    public void aLongDottedTypeTest() throws Exception {
        readsInBoundedTime("var x = o is A" + ".b".repeat(LENGTH / 2) + ";");
    }

    @Test(timeout = 60_000)
    public void aLongParenthesisedGenericRun() throws Exception {
        readsInBoundedTime("var x = (a<" + "b ".repeat(LENGTH / 2) + ")c;");
    }

    @Test(timeout = 60_000)
    public void aLongDottedDeclarationAfterANewOrType() throws Exception {
        readsInBoundedTime("A" + ".b".repeat(LENGTH / 2) + " v = null;");
    }

    @Test(timeout = 60_000)
    public void aLongGenericDeclarationType() throws Exception {
        readsInBoundedTime("A<" + "b, ".repeat(LENGTH / 3) + "> v = null;");
    }

    @Test(timeout = 60_000)
    public void aLongRunOfWhitespaceInAnInitializer() throws Exception {
        readsInBoundedTime("var x = a" + " ".repeat(20_000) + ".b;");
    }

    @Test(timeout = 60_000)
    public void aLongTypeofOperand() throws Exception {
        readsInBoundedTime("var x = typeof(" + "a".repeat(LENGTH) + ");");
    }

    @Test(timeout = 60_000)
    public void aLongSwitchExpressionOfTypePatterns() throws Exception {
        readsInBoundedTime("var x = o switch { " + "A a => 1, ".repeat(LENGTH / 10) + "_ => 0 };");
    }
}
