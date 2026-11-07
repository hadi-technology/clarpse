package com.hadi.test.typescript;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.FailureCode;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.compiler.typescript.TypeScriptDaemon;
import com.hadi.clarpse.compiler.typescript.TypeScriptDaemonException;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.Assume;
import org.junit.Test;

/**
 * The TypeScript daemon is bounded: files that belong to no program do not rebuild programs, a
 * request that gets no answer is killed and reported, a killed daemon can be replaced, and an
 * analysis that runs out of time says which files it did not analyse. Node-gated.
 */
public class TypeScriptBoundedDaemonTest {

    private static final String FIXTURE = "stuck-program-cache";
    private static final String BUDGET_PROP = "clarpse.typescript.compileBudgetSeconds";

    @Test
    public void filesInNoProgramBuildEachProgramOnce() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final Path root = TypeScriptTestUtil.fixturePath(FIXTURE);
        try (TypeScriptDaemon daemon = new TypeScriptDaemon()) {
            daemon.start();
            final TypeScriptDaemon.InitResult init = daemon.initRepo(root.toString());
            assertEquals(4, init.configCount());
            assertEquals("The cache holds fewer programs than there are configs.", 2, init.maxPrograms());
            for (int i = 1; i <= 6; i++) {
                daemon.getFileModel(root.resolve("src/feature/deep/helper" + i + ".ts").toString());
            }
            assertTrue("Each program is built at most once to learn whether it holds the file, was "
                    + daemon.programBuilds(), daemon.programBuilds() <= 5);
            assertFalse(daemon.getFileModel(root.resolve("src/app/app1.ts").toString()).declarations.isEmpty());
        }
    }

    @Test
    public void filesAreGroupedByTheProgramThatHoldsThem() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final Path root = TypeScriptTestUtil.fixturePath(FIXTURE);
        try (TypeScriptDaemon daemon = new TypeScriptDaemon()) {
            daemon.start();
            daemon.initRepo(root.toString());
            final String app = root.resolve("src/app/app1.ts").toString();
            final String spec = root.resolve("src/feature/a.spec.ts").toString();
            final String helper = root.resolve("src/feature/deep/helper1.ts").toString();
            final java.util.Map<String, Integer> owners = daemon.ownersOf(java.util.List.of(app, spec, helper));
            assertTrue(owners.get(app) >= 0);
            assertTrue(owners.get(spec) >= 0);
            assertFalse(owners.get(app).equals(owners.get(spec)));
            assertTrue(owners.get(helper) >= 0);
            assertFalse(owners.get(app).equals(owners.get(helper)));
        }
    }

    @Test
    public void aRequestWithNoAnswerIsStoppedAndReportedAsTimedOut() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final Path root = TypeScriptTestUtil.fixturePath(FIXTURE);
        try (TypeScriptDaemon daemon = new TypeScriptDaemon(Duration.ofMillis(1))) {
            daemon.start();
            try {
                daemon.initRepo(root.toString());
                fail("Starting node and reading the configs takes longer than a millisecond.");
            } catch (final TypeScriptDaemonException e) {
                assertEquals(TypeScriptDaemonException.CODE_REQUEST_TIMEOUT, e.code());
            }
            assertFalse("A daemon that did not answer is not left running.", daemon.isProcessAlive());
        }
    }

    @Test
    public void aKilledDaemonIsReplacedByOneInTheSameState() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final Path root = TypeScriptTestUtil.fixturePath(FIXTURE);
        try (TypeScriptDaemon daemon = new TypeScriptDaemon()) {
            daemon.start();
            daemon.initRepo(root.toString());
            daemon.forceStop();
            daemon.restart();
            assertTrue(daemon.isProcessAlive());
            assertFalse(daemon.getFileModel(root.resolve("src/app/app2.ts").toString()).declarations.isEmpty());
        }
    }

    @Test
    public void anAnalysisOutOfTimeRecordsEveryFileItDidNotAnalyse() throws Exception {
        System.setProperty(BUDGET_PROP, "0.000001");
        try {
            final CompileResult result = TypeScriptTestUtil.compileFixture("program-cache");
            assertFalse("Nothing was analysed, so nothing is in the model.",
                    result.model().containsComponent("packages.a.src.alpha.Alpha"));
            assertEquals("Every file is accounted for as not analysed.", 3, result.failures().size());
            for (final CompileFailure failure : result.failures()) {
                assertEquals(Integer.valueOf(FailureCode.TIME_BUDGET_EXCEEDED), failure.errorCode());
            }
        } finally {
            System.clearProperty(BUDGET_PROP);
        }
    }
}
