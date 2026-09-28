package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.compiler.typescript.TypeScriptDaemonException;
import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TypeScriptNoTsconfigTest {

    private static final String FIXTURE = "no-tsconfig";

    /** A project run by a runtime that needs no config has none, and is read all the same. */
    @Test
    public void aProjectWithNoTsconfigIsModelled() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        assertTrue(result.failures().toString(), result.failures().isEmpty());
        assertTrue(result.model().size() > 0);
    }

    @Test
    public void missingTsconfigSkipsFilesWhereUnownedFilesAreReported() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        CompileResult result = TypeScriptTestUtil.compileReportingUnownedFiles(FIXTURE);
        assertEquals(0, result.model().size());
        assertEquals(1, result.failures().size());
        assertEquals(TypeScriptDaemonException.CODE_NO_TSCONFIG,
                result.failures().iterator().next().errorCode().intValue());
        assertTrue(result.failures().iterator().next().message().contains("NO_TSCONFIG"));
    }
}
