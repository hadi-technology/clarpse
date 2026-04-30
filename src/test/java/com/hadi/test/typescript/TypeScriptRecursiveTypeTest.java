package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.compiler.typescript.TypeScriptDaemonException;
import com.hadi.clarpse.sourcemodel.Component;
import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies that the TypeScript resolver handles deeply recursive type structures
 * gracefully without crashing (issue #144).
 */
public class TypeScriptRecursiveTypeTest {

    @Test
    public void recursiveTypesDoNotCrashResolver() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        CompileResult result = TypeScriptTestUtil.compileFixture("recursive-types");

        assertNotNull(result);
        assertNotNull(result.model());
        // The model should be returned without throwing CompileException
        assertFalse(result.failures().isEmpty() && result.model().components().count() == 0);
    }

    @Test
    public void recursiveTypesProduceClassComponent() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        CompileResult result = TypeScriptTestUtil.compileFixture("recursive-types");

        assertNotNull(result);
        assertTrue("TreeNode class should be resolved",
                result.model().getComponent("src.index.TreeNode").isPresent());
    }

    @Test
    public void recursiveTypesProduceFunctionComponent() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        CompileResult result = TypeScriptTestUtil.compileFixture("recursive-types");

        assertNotNull(result);
        assertTrue("processNode function should be resolved",
                result.model().components().anyMatch(c ->
                        c.name().equals("processNode")));
    }

    @Test
    public void recursiveTypesProduceInterfaceComponent() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        CompileResult result = TypeScriptTestUtil.compileFixture("recursive-types");

        assertNotNull(result);
        assertTrue("Container interface should be resolved",
                result.model().getComponent("src.index.Container").isPresent());
    }

    @Test
    public void recursiveTypeResolutionFailuresAreFileLevel() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        CompileResult result = TypeScriptTestUtil.compileFixture("recursive-types");

        assertNotNull(result);
        assertTrue(result.model().components().count() > 0);
        result.failures().forEach(failure ->
                assertEquals(TypeScriptDaemonException.CODE_RESOLUTION_FAILED,
                        failure.errorCode().intValue()));
    }
}
