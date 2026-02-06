package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.SkipReason;
import com.hadi.clarpse.compiler.SkippedFile;
import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TypeScriptNoTsconfigTest {

    private static final String FIXTURE = "no-tsconfig";

    @Test
    public void missingTsconfigSkipsFiles() throws Exception {
        ProjectFiles projectFiles = TypeScriptTestUtil.loadProject(FIXTURE);
        CompileResult result = new ClarpseProject(projectFiles, Lang.TYPESCRIPT).result();

        for (SkippedFile skipped : result.skipped()) {
            if (skipped.reason() == SkipReason.NODE_NOT_FOUND
                    || skipped.reason() == SkipReason.TYPESCRIPT_NOT_FOUND) {
                Assume.assumeTrue("TypeScript runtime unavailable.", false);
            }
        }

        assertEquals(0, result.model().size());
        assertEquals(1, result.skipped().size());

        SkippedFile skipped = result.skipped().iterator().next();
        assertEquals(SkipReason.NO_TSCONFIG, skipped.reason());
        assertEquals(Integer.valueOf(1002), skipped.errorCode());
        assertEquals("NO_TSCONFIG", skipped.detail());

        String expectedPath = TypeScriptTestUtil.fixturePath(FIXTURE)
                .resolve("src")
                .resolve("Sample.ts")
                .toString();
        assertEquals(expectedPath, skipped.file().path());
    }
}
