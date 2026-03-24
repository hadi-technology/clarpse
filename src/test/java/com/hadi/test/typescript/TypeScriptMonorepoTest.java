package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TypeScriptMonorepoTest {

    @Test
    public void multipleTsconfigsAreHandled() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture("monorepo");
        assertTrue(result.model().containsComponent("packages.a.src.foo.Foo"));
        assertTrue(result.model().containsComponent("packages.b.src.bar.Bar"));
    }

    @Test
    public void monorepoOutputIsDeterministic() throws Exception {
        CompileResult first = TypeScriptTestUtil.compileFixture("monorepo");
        CompileResult second = TypeScriptTestUtil.compileFixture("monorepo");
        String snapshotA = TypeScriptTestUtil.modelSnapshot(first.model());
        String snapshotB = TypeScriptTestUtil.modelSnapshot(second.model());
        assertEquals(snapshotA, snapshotB);
    }

    @Test
    public void heroui3PatternsCanBeParsed() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture("heroui3-regression");
        assertTrue(result.model().containsComponent("src.components.button.ButtonRoot"));
        assertTrue(result.model().containsComponent("src.components.button.buttonVariants"));
        assertTrue(result.model().containsComponent("src.components.button.ButtonRootProps"));
    }

    @Test
    public void heroui3OutputIsDeterministic() throws Exception {
        CompileResult first = TypeScriptTestUtil.compileFixture("heroui3-regression");
        CompileResult second = TypeScriptTestUtil.compileFixture("heroui3-regression");
        String snapshotA = TypeScriptTestUtil.modelSnapshot(first.model());
        String snapshotB = TypeScriptTestUtil.modelSnapshot(second.model());
        assertEquals(snapshotA, snapshotB);
    }
}
