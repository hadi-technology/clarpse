package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileException;
import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TypeScriptInvalidTsconfigTest {

    private static final String FIXTURE = "invalid-tsconfig";

    @Test
    public void invalidTsconfigIsSkippedWhenValidConfigExists() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        ProjectFiles projectFiles = TypeScriptTestUtil.loadProject(FIXTURE);
        CompileResult result;
        try {
            result = new ClarpseProject(projectFiles, Lang.TYPESCRIPT).result();
        } catch (CompileException e) {
            if (e.getMessage() != null && e.getMessage().contains("TYPESCRIPT_NOT_FOUND")) {
                Assume.assumeTrue("TypeScript runtime unavailable.", false);
                return;
            }
            throw e;
        }

        OOPSourceCodeModel model = result.model();
        String goodName = TypeScriptTestUtil.uniqueName("good/src", "Good", "Good");
        assertTrue(model.getComponent(goodName).isPresent());

        assertEquals(1, result.failures().size());
        CompileFailure failure = result.failures().iterator().next();
        assertEquals("FILE_NOT_IN_PROGRAM", failure.message());
        assertTrue(failure.file().path().endsWith("bad/src/Bad.ts"));
    }
}
