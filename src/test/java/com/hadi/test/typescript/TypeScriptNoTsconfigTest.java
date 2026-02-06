package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileException;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TypeScriptNoTsconfigTest {

    private static final String FIXTURE = "no-tsconfig";

    @Test
    public void missingTsconfigSkipsFiles() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        ProjectFiles projectFiles = TypeScriptTestUtil.loadProject(FIXTURE);
        try {
            new ClarpseProject(projectFiles, Lang.TYPESCRIPT).result();
            fail("Expected CompileException for missing tsconfig.");
        } catch (CompileException e) {
            assertTrue(e.getMessage().contains("NO_TSCONFIG"));
        }
    }
}
