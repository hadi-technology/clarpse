package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.SkipReason;
import com.hadi.clarpse.compiler.SkippedFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TypeScriptFileNotInProgramTest {

    private static final String FIXTURE = "file-not-in-program";

    @Test
    public void fileNotIncludedInTsconfigIsSkipped() throws Exception {
        ProjectFiles projectFiles = TypeScriptTestUtil.loadProject(FIXTURE);
        CompileResult result = new ClarpseProject(projectFiles, Lang.TYPESCRIPT).result();

        for (SkippedFile skipped : result.skipped()) {
            if (skipped.reason() == SkipReason.NODE_NOT_FOUND
                    || skipped.reason() == SkipReason.TYPESCRIPT_NOT_FOUND) {
                Assume.assumeTrue("TypeScript runtime unavailable.", false);
            }
        }

        OOPSourceCodeModel model = result.model();
        String includedName = TypeScriptTestUtil.uniqueName("src", "Included", "Included");
        assertTrue(model.getComponent(includedName).isPresent());

        assertEquals(1, result.skipped().size());
        SkippedFile skipped = result.skipped().iterator().next();
        assertEquals(SkipReason.FILE_NOT_IN_PROGRAM, skipped.reason());
        assertEquals(Integer.valueOf(2001), skipped.errorCode());
        assertEquals("FILE_NOT_IN_PROGRAM", skipped.detail());

        String expectedPath = TypeScriptTestUtil.fixturePath(FIXTURE)
                .resolve("src")
                .resolve("Ignored.ts")
                .toString();
        assertEquals(expectedPath, skipped.file().path());
    }
}
