package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.ClarpseProject;
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

public class TypeScriptFileNotInProgramTest {

    private static final String FIXTURE = "file-not-in-program";

    @Test
    public void fileNotIncludedInTsconfigIsSkipped() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        ProjectFiles projectFiles = TypeScriptTestUtil.loadProject(FIXTURE);
        CompileResult result = new ClarpseProject(projectFiles, Lang.TYPESCRIPT).result();

        OOPSourceCodeModel model = result.model();
        String includedName = TypeScriptTestUtil.uniqueName("src", "Included", "Included");
        assertTrue(model.getComponent(includedName).isPresent());

        assertEquals(1, result.failures().size());
        CompileFailure failure = result.failures().iterator().next();
        assertEquals(Integer.valueOf(2001), failure.errorCode());
        assertEquals("FILE_NOT_IN_PROGRAM", failure.message());

        String expectedPath = TypeScriptTestUtil.fixturePath(FIXTURE)
                .resolve("src")
                .resolve("Ignored.ts")
                .toString();
        assertEquals(expectedPath, failure.file().path());
    }
}
