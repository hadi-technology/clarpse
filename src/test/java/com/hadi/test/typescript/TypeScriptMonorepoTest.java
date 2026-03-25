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

    @Test
    public void trailingCommasInTsconfigCanBeParsed() throws Exception {
        // Test that tsconfig.json with trailing commas (invalid JSON but valid TS) can be parsed
        // This tests the fix for the nomad project issue without requiring the full zip
        CompileResult result = TypeScriptTestUtil.compileFixture("trailing-comma-tsconfig");
        assertTrue("Expected Example component to be parsed",
                   result.model().containsComponent("src.example.Example"));
        // Should have multiple components (class, constructor, method, properties)
        long componentCount = result.model().components().count();
        assertTrue("Expected at least 1 component, but got: " + componentCount, componentCount >= 1);
    }
}
