package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.compiler.typescript.TypeScriptDaemon;
import org.junit.Assume;
import org.junit.Test;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TypeScriptProgramCacheTest {

    private static final String FIXTURE = "program-cache";
    private static final String MAX_PROGRAMS_PROP = "clarpse.typescript.maxPrograms";

    @Test
    public void initialisationReadsEveryConfigAndBuildsNoPrograms() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final Path repoRoot = TypeScriptTestUtil.fixturePath(FIXTURE);
        try (TypeScriptDaemon daemon = new TypeScriptDaemon()) {
            daemon.start();
            final TypeScriptDaemon.InitResult result = daemon.initRepo(repoRoot.toString());
            assertEquals(3, result.configCount());
            assertTrue("Every config's root files should be known without a program.",
                    result.fileCount() >= 3);
            assertEquals("Initialization must not build a program per config.",
                    0, result.residentProgramCount());
        }
    }

    @Test
    public void everyPackageIsParsedWhenTheCacheHoldsFewerProgramsThanConfigs() throws Exception {
        System.setProperty(MAX_PROGRAMS_PROP, "1");
        try {
            final CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
            assertTrue(result.model().containsComponent("packages.a.src.alpha.Alpha"));
            assertTrue(result.model().containsComponent("packages.b.src.beta.Beta"));
            assertTrue(result.model().containsComponent("packages.c.src.gamma.Gamma"));
            assertTrue(result.failures().isEmpty());
        } finally {
            System.clearProperty(MAX_PROGRAMS_PROP);
        }
    }
}
