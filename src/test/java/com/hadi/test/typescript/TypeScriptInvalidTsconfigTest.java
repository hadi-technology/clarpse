package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileException;
import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.compiler.typescript.TypeScriptDaemonException;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assume;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TypeScriptInvalidTsconfigTest {

    private static final String FIXTURE = "invalid-tsconfig";

    /**
     * A repository with one good and one unparseable config still yields the good project's model.
     * The bad config is reported, and so is each source file it left in no program - those files
     * used to be discarded along with their config, with nothing recorded to say they had not been
     * analysed.
     */
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
        assertTrue(model.copyOfComponent(goodName).isPresent());

        List<CompileFailure> configFailures = result.failures().stream()
                .filter(failure -> Integer.valueOf(1003).equals(failure.errorCode()))
                .collect(Collectors.toList());
        assertEquals(1, configFailures.size());
        assertEquals("CONFIG_PARSE_FAILED", configFailures.get(0).message());
        assertTrue(configFailures.get(0).file().path().endsWith("bad/tsconfig.json"));

        List<CompileFailure> orphanedFiles = result.failures().stream()
                .filter(failure -> !Integer.valueOf(1003).equals(failure.errorCode()))
                .collect(Collectors.toList());
        assertFalse("The bad config's sources belong to no program and must be reported.",
                orphanedFiles.isEmpty());
        for (CompileFailure failure : orphanedFiles) {
            assertEquals(Integer.valueOf(TypeScriptDaemonException.CODE_FILE_NOT_IN_PROGRAM),
                    failure.errorCode());
            assertTrue(failure.file().path().replace('\\', '/').contains("/bad/"));
        }
    }
}
