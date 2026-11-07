package com.hadi.test.typescript;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.FailureCode;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.compiler.typescript.TypeScriptDaemon;
import com.hadi.clarpse.compiler.typescript.TypeScriptDaemonException;
import com.hadi.clarpse.compiler.typescript.TypeScriptLimits;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * The TypeScript daemon is bounded: files outside every program do not rebuild programs, a request
 * that gets no answer is killed and reported, a killed or dead daemon is replaced and the rest of
 * the analysis goes on, and whatever was not analysed is on the failure list. Node-gated; the tests
 * that need a daemon that hangs or dies stand a small shell script in for node.
 */
public class TypeScriptBoundedDaemonTest {

    private static final String FIXTURE = "stuck-program-cache";
    private static final String[] PROPS = {
        "clarpse.node.path",
        "clarpse.typescript.requestTimeoutSeconds",
        "clarpse.typescript.heavyRequestTimeoutSeconds",
        "clarpse.typescript.compileBudgetSeconds",
        "clarpse.typescript.maxRestarts",
    };

    private Path scratch;

    @Before
    public void createScratch() throws IOException {
        scratch = Files.createTempDirectory("ts-bounded");
    }

    @After
    public void clearProperties() throws IOException {
        for (final String prop : PROPS) {
            System.clearProperty(prop);
        }
        try (Stream<Path> files = Files.walk(scratch)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    public void filesOutsideEveryConfigBuildEachProgramOnce() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final Path root = TypeScriptTestUtil.fixturePath(FIXTURE);
        try (TypeScriptDaemon daemon = new TypeScriptDaemon()) {
            daemon.start();
            final TypeScriptDaemon.InitResult init = daemon.initRepo(root.toString());
            assertEquals(4, init.configCount());
            assertEquals("The cache holds fewer programs than there are configs.", 2, init.maxPrograms());
            final List<String> helpers = new ArrayList<>();
            for (int i = 1; i <= 6; i++) {
                helpers.add(root.resolve("src/feature/deep/helper" + i + ".ts").toString());
            }
            daemon.ownersOf(helpers);
            for (final String helper : helpers) {
                daemon.getFileModel(helper);
            }
            assertTrue("Each program is built a bounded number of times, was " + daemon.programBuilds(),
                    daemon.programBuilds() <= 5);
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
            final Map<String, Integer> owners = daemon.ownersOf(List.of(app, spec, helper));
            assertTrue(owners.get(app) >= 0);
            assertTrue(owners.get(spec) >= 0);
            assertFalse(owners.get(app).equals(owners.get(spec)));
            assertTrue(owners.get(helper) >= 0);
            assertFalse(owners.get(app).equals(owners.get(helper)));
        }
    }

    /**
     * Asking which program holds each file first must not change what any file's model is, or
     * whether it has one: for a project that reaches a file only by an import, one with nested
     * configs, and one with a referenced project whose sources resolve through its output.
     */
    @Test
    public void groupingFilesFirstDoesNotChangeAnyFilesModel() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        for (final String fixture : List.of("reached-by-import", "ref-redirect", FIXTURE, "project-references")) {
            final Path root = TypeScriptTestUtil.fixturePath(fixture);
            final List<String> files = typeScriptFilesUnder(root);
            final Map<String, String> plain = modelsByFile(root, files, false);
            final Map<String, String> grouped = modelsByFile(root, files, true);
            assertEquals(fixture, plain, grouped);
        }
    }

    @Test
    public void aFileReachedOnlyByImportAndANestedConfigsFileAreNotSkipped() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final Path root = TypeScriptTestUtil.fixturePath("reached-by-import");
        try (TypeScriptDaemon daemon = new TypeScriptDaemon()) {
            daemon.start();
            daemon.initRepo(root.toString());
            final String main = root.resolve("src/main.ts").toString();
            final String shared = root.resolve("shared/util.ts").toString();
            final String stray = root.resolve("shared/stray.ts").toString();
            final String nested = root.resolve("nested/n.ts").toString();
            final Map<String, Integer> owners = daemon.ownersOf(List.of(nested, stray, shared, main));
            assertTrue("The nested config holds its own file.", owners.get(nested) >= 0);
            assertFalse("The nested file is not the root config's.", owners.get(nested).equals(owners.get(main)));
            assertEquals("A file the root program reaches by import is the root program's.",
                    owners.get(main), owners.get(shared));
            assertFalse(daemon.getFileModel(shared).declarations.isEmpty());
            assertFalse(daemon.getFileModel(nested).declarations.isEmpty());
        }
    }

    @Test
    public void aRequestWithNoAnswerIsStoppedAndReportedAsTimedOut() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final Path root = TypeScriptTestUtil.fixturePath(FIXTURE);
        try (TypeScriptDaemon daemon = new TypeScriptDaemon(Duration.ofMillis(1), Duration.ofMillis(1))) {
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
    public void aFileModelRequestWithNoAnswerTimesOutAndTheReplacementReplaysThePlan() throws Exception {
        final Path log = fakeNode("*stuck.ts*", "hang", "0");
        try (TypeScriptDaemon daemon = new TypeScriptDaemon(Duration.ofSeconds(1), Duration.ofSeconds(30))) {
            daemon.start();
            daemon.initRepo("/repo");
            daemon.planOneLevel(List.of("/repo/a.ts", "/repo/stuck.ts"), List.of("/repo/b.ts"));
            daemon.getFileModel("/repo/a.ts");
            try {
                daemon.getFileModel("/repo/stuck.ts");
                fail("The daemon never answers for this file.");
            } catch (final TypeScriptDaemonException e) {
                assertEquals(TypeScriptDaemonException.CODE_REQUEST_TIMEOUT, e.code());
            }
            assertFalse("The stuck daemon is gone by the time the timeout is reported.",
                    daemon.isProcessAlive());
            daemon.restart();
            daemon.getFileModel("/repo/b.ts");
            assertEquals(List.of("initRepo", "planOneLevel", "getFileModel", "getFileModel",
                    "initRepo", "planOneLevel", "getFileModel"), requestsIn(log));
        }
    }

    @Test
    public void aTimedOutFileIsRecordedAndTheRestIsAnalysedByAReplacementDaemon() throws Exception {
        final Path log = fakeNode("*app2.ts*", "hang", "0");
        System.setProperty("clarpse.typescript.requestTimeoutSeconds", "1");
        final CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        assertEquals(1, result.failures().size());
        final CompileFailure failure = result.failures().iterator().next();
        assertTrue(failure.file().path().endsWith("app2.ts"));
        assertEquals(Integer.valueOf(FailureCode.TIME_BUDGET_EXCEEDED), failure.errorCode());
        assertEquals(2, requestsIn(log).stream().filter("initRepo"::equals).count());
        assertEquals("Every file was asked for once, and the stuck one only once.",
                11, requestsIn(log).stream().filter("getFileModel"::equals).count());
    }

    @Test
    public void whenRestartsAreSpentTheRestOfTheFilesAreRecordedAsNotAnalysed() throws Exception {
        fakeNode("*app2.ts*|*helper1.ts*", "hang", "0");
        System.setProperty("clarpse.typescript.requestTimeoutSeconds", "1");
        System.setProperty("clarpse.typescript.maxRestarts", "1");
        final CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        final TreeSet<String> failed = failedPaths(result);
        assertEquals("The two stuck files and every file after the second.", 8, failed.size());
        assertFalse(failed.stream().anyMatch(path -> path.endsWith("app1.ts") || path.endsWith("app3.ts")));
        for (final CompileFailure failure : result.failures()) {
            assertEquals(Integer.valueOf(FailureCode.TIME_BUDGET_EXCEEDED), failure.errorCode());
        }
    }

    @Test
    public void aDaemonThatDiesCostsItsFileAndNotTheWholeRepository() throws Exception {
        fakeNode("*app2.ts*", "die", "0");
        System.setProperty("clarpse.typescript.maxRestarts", "0");
        final CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        final TreeSet<String> failed = failedPaths(result);
        assertEquals("The file it died on and every file after it.", 10, failed.size());
        assertFalse(failed.stream().anyMatch(path -> path.endsWith("app1.ts")));
        for (final CompileFailure failure : result.failures()) {
            assertEquals(Integer.valueOf(FailureCode.DAEMON_ERROR), failure.errorCode());
        }
    }

    @Test
    public void anAnalysisOutOfTimePartwayRecordsTheFilesItDidNotReach() throws Exception {
        fakeNode("*nothing-matches*", "hang", "0.5");
        System.setProperty("clarpse.typescript.compileBudgetSeconds", "2");
        final CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        final String rootPath = TypeScriptTestUtil.fixturePath(FIXTURE).toString();
        final TreeSet<String> failed = failedPaths(result).stream()
                .map(path -> path.substring(path.indexOf(rootPath) == 0 ? rootPath.length() : 0))
                .collect(Collectors.toCollection(TreeSet::new));
        assertFalse("Some files were analysed before the time ran out.", failed.size() >= 11);
        assertFalse("Some files were not.", failed.isEmpty());
        final List<String> all = typeScriptFilesUnder(TypeScriptTestUtil.fixturePath(FIXTURE)).stream()
                .map(path -> path.substring(TypeScriptTestUtil.fixturePath(FIXTURE).toString().length()))
                .sorted().collect(Collectors.toList());
        assertEquals("The files not analysed are the last ones, in path order.",
                new TreeSet<>(all.subList(all.size() - failed.size(), all.size())), failed);
        for (final CompileFailure failure : result.failures()) {
            assertEquals(Integer.valueOf(FailureCode.TIME_BUDGET_EXCEEDED), failure.errorCode());
        }
    }

    @Test
    public void anAnalysisWithNoTimeLeftRecordsEveryFile() throws Exception {
        System.setProperty("clarpse.typescript.compileBudgetSeconds", "0.000001");
        final CompileResult result = TypeScriptTestUtil.compileFixture("program-cache");
        assertFalse(result.model().containsComponent("packages.a.src.alpha.Alpha"));
        assertEquals("Every file is accounted for as not analysed.", 3, result.failures().size());
        for (final CompileFailure failure : result.failures()) {
            assertEquals(Integer.valueOf(FailureCode.TIME_BUDGET_EXCEEDED), failure.errorCode());
        }
    }

    @Test
    public void aNegativeLimitIsRejectedAndTheDefaultUsed() {
        System.setProperty("clarpse.typescript.requestTimeoutSeconds", "-5");
        assertEquals(120.0, TypeScriptLimits.seconds(TypeScriptLimits.REQUEST_TIMEOUT_PROP,
                TypeScriptLimits.REQUEST_TIMEOUT_ENV, TypeScriptLimits.DEFAULT_REQUEST_TIMEOUT_SECONDS), 0.0);
        System.setProperty("clarpse.typescript.requestTimeoutSeconds", "0");
        assertEquals("Zero turns a limit off.", 0.0, TypeScriptLimits.seconds(
                TypeScriptLimits.REQUEST_TIMEOUT_PROP, TypeScriptLimits.REQUEST_TIMEOUT_ENV, 120), 0.0);
        System.setProperty("clarpse.typescript.maxRestarts", "-1");
        assertEquals(3, TypeScriptLimits.count(TypeScriptLimits.MAX_RESTARTS_PROP,
                TypeScriptLimits.MAX_RESTARTS_ENV, TypeScriptLimits.DEFAULT_MAX_RESTARTS));
    }

    /** Models every file, optionally after asking which program holds each. */
    private static Map<String, String> modelsByFile(final Path root, final List<String> files,
                                                    final boolean groupFirst) throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final Map<String, String> models = new java.util.TreeMap<>();
        try (TypeScriptDaemon daemon = new TypeScriptDaemon()) {
            daemon.start();
            daemon.initRepo(root.toString());
            if (groupFirst) {
                daemon.ownersOf(files);
            }
            for (final String file : files) {
                try {
                    models.put(file, mapper.writeValueAsString(daemon.getFileModel(file)));
                } catch (final TypeScriptDaemonException e) {
                    models.put(file, "error " + e.code());
                }
            }
        }
        return models;
    }

    private static List<String> typeScriptFilesUnder(final Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".ts") && !path.toString().endsWith(".d.ts"))
                    .map(Path::toString).sorted().collect(Collectors.toList());
        }
    }

    private static TreeSet<String> failedPaths(final CompileResult result) {
        return result.failures().stream().map(failure -> failure.file().path())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static List<String> requestsIn(final Path log) throws IOException {
        return Files.readAllLines(log);
    }

    /**
     * Stands a script in for node that answers the daemon's requests with empty models, except that
     * for a file matching the pattern it hangs or dies, and that takes a delay to answer each file.
     *
     * @return The log the script appends each request's method to.
     */
    private Path fakeNode(final String pattern, final String behaviour, final String delaySeconds)
            throws IOException {
        Assume.assumeTrue(Files.exists(Path.of("/bin/sh")));
        final Path log = scratch.resolve("requests.log");
        Files.write(log, new byte[0]);
        final String onMatch;
        if ("die".equals(behaviour)) {
            onMatch = "exit 1";
        } else {
            onMatch = "exec sleep 600";
        }
        final String script = String.join("\n",
                "#!/bin/sh",
                "case \"$1\" in --version) echo v22.0.0; exit 0;; esac",
                "while IFS= read -r line; do",
                "  id=$(printf '%s' \"$line\" | sed -n 's/.*\"id\":\\([0-9][0-9]*\\).*/\\1/p')",
                "  method=$(printf '%s' \"$line\" | sed -n 's/.*\"method\":\"\\([A-Za-z]*\\)\".*/\\1/p')",
                "  echo \"$method\" >> '" + log + "'",
                "  case \"$method\" in",
                "    getFileModel)",
                "      case \"$line\" in " + pattern + ") " + onMatch + ";; esac",
                "      sleep " + delaySeconds,
                "      echo '{\"jsonrpc\":\"2.0\",\"id\":'$id',\"result\":{\"filePath\":\"f\","
                        + "\"declarations\":[],\"imports\":[],\"unresolvedBases\":[]}}';;",
                "    ownersOf) echo '{\"jsonrpc\":\"2.0\",\"id\":'$id',\"result\":{\"owners\":{}}}';;",
                "    shutdown) echo '{\"jsonrpc\":\"2.0\",\"id\":'$id',\"result\":{}}'; exit 0;;",
                "    *) echo '{\"jsonrpc\":\"2.0\",\"id\":'$id',\"result\":{}}';;",
                "  esac",
                "done",
                "");
        final Path node = scratch.resolve("fake-node");
        Files.write(node, script.getBytes(StandardCharsets.UTF_8));
        Files.setPosixFilePermissions(node, PosixFilePermissions.fromString("rwxr-xr-x"));
        System.setProperty("clarpse.node.path", node.toString());
        return log;
    }
}
