package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.compiler.typescript.TypeScriptDaemon;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assume;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Which programs a repository's files are read in: what its configs say, what they say that the
 * compiler does not understand, and what becomes of the files they leave out.
 */
public class TypeScriptProjectDiscoveryTest {

    /**
     * A config that asks the compiler to report on its own work makes it write to standard output,
     * which is the channel replies come back on. The project is read, and nothing the compiler
     * wrote is taken for a reply.
     */
    @Test
    public void aConfigThatMakesTheCompilerReportIsRead() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture("reporting-options");

        assertTrue(result.failures().toString(), result.failures().isEmpty());
        OOPSourceCodeModel model = result.model();
        Component car = model.copyOfComponent(
                TypeScriptTestUtil.uniqueName("src", "car", "Car")).orElseThrow();
        assertTrue(model.copyOfComponent(
                TypeScriptTestUtil.uniqueName("src", "engine", "Engine")).isPresent());
        assertTrue(targets(car).toString(),
                targets(car).contains(TypeScriptTestUtil.uniqueName("src", "engine", "Engine")));
    }

    /** A line that is not a reply is passed over, whatever wrote it. */
    @Test
    public void aLineThatIsNotAReplyIsPassedOver() throws Exception {
        Method replyIn = TypeScriptDaemon.class.getDeclaredMethod("replyIn", String.class);
        replyIn.setAccessible(true);
        try (TypeScriptDaemon daemon = new TypeScriptDaemon()) {
            assertNull(replyIn.invoke(daemon,
                    "Found 'package.json' at '/tmp/project/packages/bench/package.json'."));
            assertNull(replyIn.invoke(daemon, "======== Resolving module './engine' ========"));
            assertNull(replyIn.invoke(daemon, "{ not json"));
            assertNull(replyIn.invoke(daemon, "[1, 2]"));
            assertNotNull(replyIn.invoke(daemon, "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}"));
        }
    }

    /**
     * An option newer than the bundled compiler, or one it has never heard of, is a statement
     * about that option. The config's files are read with the options the compiler understood.
     */
    @Test
    public void aConfigWithAnOptionTheCompilerDoesNotKnowIsUsed() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileReportingUnownedFiles("unknown-option");

        assertTrue(result.failures().toString(), result.failures().isEmpty());
        assertTrue(result.model().copyOfComponent(
                TypeScriptTestUtil.uniqueName("pkg/src", "newer", "Newer")).isPresent());
        assertTrue(result.model().copyOfComponent(
                TypeScriptTestUtil.uniqueName("ok/src", "steady", "Steady")).isPresent());
    }

    /** What the compiler said of such a config is kept for the caller. */
    @Test
    public void whatTheCompilerSaidOfAConfigItUsedIsReported() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        try (TypeScriptDaemon daemon = new TypeScriptDaemon()) {
            daemon.start();
            TypeScriptDaemon.InitResult init = daemon.initRepo(
                    TypeScriptTestUtil.fixturePath("unknown-option").toString());

            assertEquals(2, init.configCount());
            assertEquals(0, init.invalidConfigCount());
            assertTrue(init.configDiagnostics().size() >= 2);
            for (TypeScriptDaemon.ConfigDiagnostic diagnostic : init.configDiagnostics()) {
                assertTrue(diagnostic.configPath().replace('\\', '/').endsWith("pkg/tsconfig.json"));
                assertTrue(diagnostic.code() > 0);
                assertTrue(!diagnostic.message().isEmpty());
            }
        }
    }

    /**
     * A repository that builds one tree several ways keeps a config for each and no
     * {@code tsconfig.json}. It is read through them, so an import written with an alias they
     * declare resolves, and the config they all extend owns nothing.
     */
    @Test
    public void aRepositoryWithOnlyVariantConfigsIsReadThroughThem() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileReportingUnownedFiles("variant-configs");

        assertTrue(result.failures().toString(), result.failures().isEmpty());
        Component billing = result.model().copyOfComponent(
                TypeScriptTestUtil.uniqueName("server", "billing", "Billing")).orElseThrow();
        assertTrue(targets(billing).toString(), targets(billing).contains(
                TypeScriptTestUtil.uniqueName("src/lib", "ledger", "Ledger")));
    }

    /** The variants are the repository's configs, and the config they extend is not one. */
    @Test
    public void aConfigAnotherExtendsIsNotAProject() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        try (TypeScriptDaemon daemon = new TypeScriptDaemon()) {
            daemon.start();
            TypeScriptDaemon.InitResult init = daemon.initRepo(
                    TypeScriptTestUtil.fixturePath("variant-configs").toString());

            assertEquals(2, init.configCount());
            assertEquals(0, init.unownedFileCount());
        }
    }

    /**
     * Files no config names are read in a program for each top-level directory that holds any,
     * and an import among them resolves, written with its extension as a runtime that runs
     * TypeScript directly has it written.
     */
    @Test
    public void filesNoConfigNamesAreReadByTheDirectoryTheySitUnder() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        try (TypeScriptDaemon daemon = new TypeScriptDaemon()) {
            daemon.start();
            TypeScriptDaemon.InitResult init = daemon.initRepo(
                    TypeScriptTestUtil.fixturePath("unowned-files").toString());

            assertEquals(1, init.configCount());
            assertEquals(4, init.unownedFileCount());
            assertEquals(3, init.unownedProgramCount());
        }
        CompileResult result = TypeScriptTestUtil.compileFixture("unowned-files");

        assertTrue(result.failures().toString(), result.failures().isEmpty());
        Set<String> modelled = result.model().components()
                .map(Component::uniqueName).collect(Collectors.toSet());
        assertTrue(modelled.toString(), modelled.containsAll(Set.of(
                TypeScriptTestUtil.uniqueName("packages/core/src", "core", "Core"),
                TypeScriptTestUtil.uniqueName("scripts", "seed", "Seeder"),
                TypeScriptTestUtil.uniqueName("scripts/tools", "report", "Report"),
                TypeScriptTestUtil.uniqueName("skills/fetch", "main", "Fetcher"),
                TypeScriptTestUtil.uniqueName("", "entry", "Entry"))));
        Component seeder = result.model().copyOfComponent(
                TypeScriptTestUtil.uniqueName("scripts", "seed", "Seeder")).orElseThrow();
        assertTrue(targets(seeder).toString(), targets(seeder).contains(
                TypeScriptTestUtil.uniqueName("scripts/tools", "report", "Report")));
    }

    private static Set<String> targets(final Component component) {
        return component.references().stream()
                .map(ComponentReference::invokedComponent).collect(Collectors.toSet());
    }
}
