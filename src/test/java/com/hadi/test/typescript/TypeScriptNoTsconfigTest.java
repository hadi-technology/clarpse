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

    @Test
    public void missingTsconfigSkipsFiles() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        ProjectFiles projectFiles = TypeScriptTestUtil.loadProject(FIXTURE);
        CompileResult result = new ClarpseProject(projectFiles, Lang.TYPESCRIPT).result();
        assertEquals(0, result.model().size());
        assertEquals(1, result.failures().size());
        assertEquals(TypeScriptDaemonException.CODE_NO_TSCONFIG,
                result.failures().iterator().next().errorCode().intValue());
        assertTrue(result.failures().iterator().next().message().contains("NO_TSCONFIG"));
    }
}
