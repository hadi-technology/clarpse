package com.hadi.test.onelevel;

import com.hadi.clarpse.compiler.AbstractPreparedAnalysis;
import com.hadi.clarpse.compiler.AnalysisOptions;
import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileException;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.PreparedAnalysis;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import org.junit.After;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.hadi.test.onelevel.OneLevelTestSupport.project;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A one-level analysis leaves nothing behind: no temporary directory and no resolver process, after
 * it is closed, after it fails, and after it is interrupted. Java and C# write nothing to disk.
 */
public class OneLevelCleanupTest {

    private static final Path TMP = Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();

    @After
    public void clearInterrupt() {
        Thread.interrupted();
    }

    private static Set<Path> clarpseDirs() throws IOException {
        return dirs("clarpse-src-*");
    }

    private static Set<Path> dirs(final String glob) throws IOException {
        final Set<Path> dirs = new TreeSet<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(TMP, glob)) {
            entries.forEach(dirs::add);
        }
        return dirs;
    }

    private static List<ProcessHandle> liveDaemons() {
        return ProcessHandle.current().descendants()
                .filter(ProcessHandle::isAlive)
                .filter(handle -> handle.info().commandLine().map(line -> line.contains("daemon.js")).orElse(false))
                .collect(Collectors.toList());
    }

    private static ClarpseProject typeScript(final ProjectFiles files) {
        return new ClarpseProject(files, Lang.TYPESCRIPT, List.of("/src/a.ts"), AnalysisOptions.oneLevel());
    }

    private static ProjectFiles typeScriptFiles() {
        return project(TypeScriptOneLevelTest.repository());
    }

    @Test
    public void theTemporaryDirectoryAPreparedAnalysisCausesIsDeletedOnClose() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final Set<Path> before = clarpseDirs();
        final ProjectFiles files = typeScriptFiles();
        final PreparedAnalysis prepared = typeScript(files).prepare();
        assertTrue(files.isTempProjectDir());
        assertEquals(before.size() + 1, clarpseDirs().size());
        prepared.compile(prepared.levelOneFiles());
        assertEquals("compiling reuses the one copy", before.size() + 1, clarpseDirs().size());
        prepared.close();
        prepared.close();
        assertFalse(files.isTempProjectDir());
        assertEquals(before, clarpseDirs());
        assertTrue(liveDaemons().toString(), liveDaemons().isEmpty());
        assertTrue("a TypeScript daemon's extraction ends with its session", dirs("clarpse-ts-daemon-*").isEmpty());
        assertTrue(dirs("clarpse-clarpse-*").isEmpty());
    }

    @Test
    public void aDirectOneLevelCompileDeletesTheCopyItCaused() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final Set<Path> before = clarpseDirs();
        final ProjectFiles files = typeScriptFiles();
        final CompileResult result = typeScript(files).result();
        assertFalse(result.levelOne().levelOneFiles().isEmpty());
        assertEquals(before, clarpseDirs());
        assertTrue(liveDaemons().isEmpty());
    }

    @Test
    public void aCopyTheCallerMadeIsLeftToTheCaller() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        try (ProjectFiles files = typeScriptFiles()) {
            final Path copy = Paths.get(files.projectDir());
            typeScript(files).result();
            assertTrue(Files.isDirectory(copy));
        }
    }

    @Test
    public void theCopyIsDeletedWhenPreparingFails() throws Exception {
        final Set<Path> before = clarpseDirs();
        final ProjectFiles files = typeScriptFiles();
        try {
            AbstractPreparedAnalysis.prepareCleanly(files, () -> {
                files.projectDir();
                throw new CompileException("failed while preparing", new IllegalStateException());
            });
            fail("the failure must propagate");
        } catch (final CompileException expected) {
            assertEquals("failed while preparing", expected.getMessage());
        }
        assertFalse(files.isTempProjectDir());
        assertEquals(before, clarpseDirs());
    }

    /**
     * An analysis interrupted while its resolver runs, as a deadline interrupts it, stops the
     * resolver; closing it then leaves nothing behind.
     */
    @Test
    public void theCopyAndTheDaemonAreGoneAfterAnInterruptedCompile() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final Set<Path> before = clarpseDirs();
        final ProjectFiles files = typeScriptFiles();
        try (PreparedAnalysis prepared = typeScript(files).prepare()) {
            final Thread worker = new Thread(() -> {
                try {
                    prepared.compile(null);
                } catch (final Exception ignored) {
                    // Whether the interrupt surfaces as an exception or as failures, cleanup must follow.
                }
            });
            worker.start();
            final long deadline = System.currentTimeMillis() + 30_000;
            while (liveDaemons().isEmpty() && worker.isAlive() && System.currentTimeMillis() < deadline) {
                Thread.sleep(5);
            }
            worker.interrupt();
            worker.join(60_000);
            assertFalse(worker.isAlive());
            assertTrue(liveDaemons().toString(), liveDaemons().isEmpty());
        }
        assertFalse(files.isTempProjectDir());
        assertEquals(before, clarpseDirs());
    }

    @Test
    public void javaAndCSharpOneLevelCompilesWriteNothingToDisk() throws Exception {
        final Set<Path> before = clarpseDirs();
        final ProjectFiles java = project(OneLevelTestSupport.files(
                "/app/A.java", "package app;\nimport lib.B;\npublic class A { B b; }\n",
                "/lib/B.java", "package lib;\npublic class B { }\n"));
        final ProjectFiles cSharp = project(CSharpOneLevelTest.repository());
        new ClarpseProject(java, Lang.JAVA, List.of("/app/A.java"), AnalysisOptions.oneLevel()).result();
        try (PreparedAnalysis prepared = new ClarpseProject(cSharp, Lang.CSHARP, List.of("/Core/A.cs"),
                AnalysisOptions.oneLevel()).prepare()) {
            prepared.compile(prepared.levelOneFiles());
        }
        assertFalse(java.isTempProjectDir());
        assertFalse(cSharp.isTempProjectDir());
        assertEquals(before, clarpseDirs());
    }

    private static final FileTime TWO_DAYS_AGO = FileTime.from(Instant.now().minus(Duration.ofDays(2)));

    private static long currentStart() {
        return ProcessHandle.current().info().startInstant().map(Instant::toEpochMilli).orElse(-1L);
    }

    /** A directory named as one owned by the given process, last modified two days ago. */
    private static Path ownedBy(final long pid, final long start, final String suffix) throws IOException {
        final Path dir = Files.createDirectory(TMP.resolve("clarpse-test-" + pid + "-" + start + "-" + suffix));
        Files.writeString(dir.resolve("left.txt"), "left behind");
        Files.setLastModifiedTime(dir, TWO_DAYS_AGO);
        return dir;
    }

    private static String suffix() {
        return Long.toString(System.nanoTime());
    }

    @Test
    public void theSweepNeverDeletesADirectoryThisProcessOwnsWhateverItsAge() throws Exception {
        final Path mine = ownedBy(ProcessHandle.current().pid(), currentStart(), suffix());
        try {
            assertFalse(ProjectFiles.deleteStaleTempDirs(Duration.ZERO).contains(mine));
            assertTrue(Files.isDirectory(mine));
        } finally {
            org.apache.commons.io.FileUtils.deleteQuietly(mine.toFile());
        }
    }

    @Test
    public void theSweepDeletesAnOldDirectoryWhoseOwnerIsNotRunning() throws Exception {
        final Path orphan = ownedBy(Long.MAX_VALUE / 2, 1_000L, suffix());
        final List<Path> deleted = ProjectFiles.deleteStaleTempDirs(Duration.ofDays(1));
        assertTrue(deleted.toString(), deleted.contains(orphan.toAbsolutePath().normalize()));
        assertFalse(Files.exists(orphan));
    }

    /** A process id reused by a later process, as a restarted container's JVM reuses it, is not the owner. */
    @Test
    public void theSweepDeletesADirectoryOfAnEarlierProcessWithTheSameId() throws Exception {
        Assume.assumeTrue(currentStart() > 0);
        final Path earlier = ownedBy(ProcessHandle.current().pid(), currentStart() - 60_000L, suffix());
        final List<Path> deleted = ProjectFiles.deleteStaleTempDirs(Duration.ofDays(1));
        assertTrue(deleted.toString(), deleted.contains(earlier.toAbsolutePath().normalize()));
        assertFalse(Files.exists(earlier));
    }

    @Test
    public void theSweepJudgesAnOwnerlessNameByAgeAlone() throws Exception {
        final Path old = Files.createTempDirectory("clarpse-test-old-");
        Files.setLastModifiedTime(old, TWO_DAYS_AGO);
        final Path fresh = Files.createTempDirectory("clarpse-test-fresh-");
        final Path orphanButFresh = Files.createDirectory(
                TMP.resolve("clarpse-test-" + (Long.MAX_VALUE / 2) + "-1000-" + suffix()));
        final Path unrelated = Files.createTempDirectory("unrelated-old-");
        Files.setLastModifiedTime(unrelated, TWO_DAYS_AGO);
        try {
            final List<Path> deleted = ProjectFiles.deleteStaleTempDirs(Duration.ofDays(1));
            assertTrue(deleted.toString(), deleted.contains(old.toAbsolutePath().normalize()));
            assertFalse(Files.exists(old));
            assertTrue(Files.isDirectory(fresh));
            assertTrue("an orphan younger than the age is kept", Files.isDirectory(orphanButFresh));
            assertTrue(Files.isDirectory(unrelated));
        } finally {
            Files.deleteIfExists(fresh);
            Files.deleteIfExists(orphanButFresh);
            Files.deleteIfExists(unrelated);
        }
    }

    @Test
    public void theSweepLeavesDirectoriesOpenInThisJvm() throws Exception {
        try (ProjectFiles open = project(OneLevelTestSupport.files("/A.java", "class A { }"))) {
            final Path openDir = Paths.get(open.projectDir());
            Files.setLastModifiedTime(openDir, TWO_DAYS_AGO);
            assertFalse(ProjectFiles.deleteStaleTempDirs(Duration.ZERO).contains(openDir));
            assertTrue(Files.isDirectory(openDir));
            final String name = openDir.getFileName().toString();
            assertTrue(name, name.startsWith("clarpse-src-" + ProcessHandle.current().pid() + "-"));
        }
    }

    /** A runtime extraction removed from under a running JVM is extracted again on next use. */
    @Test
    public void aRemovedPythonRuntimeIsExtractedAgain() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final ClarpseProject first = new ClarpseProject(project(PythonOneLevelTest.repository()), Lang.PYTHON,
                List.of("/app/a.py"), AnalysisOptions.oneLevel());
        assertFalse(first.result().model().size() == 0);
        final Set<Path> runtimes = dirs("clarpse-py-daemon-" + ProcessHandle.current().pid() + "-*");
        assertFalse(runtimes.isEmpty());
        runtimes.forEach(dir -> org.apache.commons.io.FileUtils.deleteQuietly(dir.toFile()));
        final CompileResult again = new ClarpseProject(project(PythonOneLevelTest.repository()), Lang.PYTHON,
                List.of("/app/a.py"), AnalysisOptions.oneLevel()).result();
        assertTrue(again.failures().toString(), again.failures().isEmpty());
        assertFalse(again.model().size() == 0);
    }
}
