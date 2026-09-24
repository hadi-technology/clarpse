package com.hadi.test;

import com.hadi.clarpse.compiler.DiscardedEntryObserver;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.UnreadSourceFiles;
import com.hadi.clarpse.compiler.UnreadSourceFiles.Answer;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers the record of the source files this library does not read: what reaches it, what must
 * never reach it, what the files it names still look like to every other accessor, and when it
 * answers that it does not know.
 */
public class UnreadSourceFilesTest {

    private static final String JAVA_SOURCE = "package repo; public class Main { void run() { } }";
    private static final String SCALA_SOURCE = "package repo\nclass Main { def run(): Unit = () }\n";
    private static final String KOTLIN_SOURCE = "package repo\nclass Main { fun run() { } }\n";
    private static final String GO_SOURCE = "package repo\n\nfunc Run() {}\n";
    private static final String README = "# Repo\n\nThe architecture lives here.\n";
    private static final byte[] LOGO = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @Test
    public void anArchivesScalaAndKotlinAreReachableThroughTheRecord() throws Exception {
        ProjectFiles projectFiles = zipOf(
                entry("repo/src/Main.java", JAVA_SOURCE),
                entry("repo/src/Main.scala", SCALA_SOURCE),
                entry("repo/src/Main.kt", KOTLIN_SOURCE));

        UnreadSourceFiles unread = projectFiles.unreadSourceFiles();

        assertTrue(unread.complete());
        assertEquals(List.of("/repo/src/Main.scala", "/repo/src/Main.kt"),
                new ArrayList<>(unread.paths()));
        assertEquals(Answer.YES, unread.holdsFileNamed("Main.scala"));
        assertEquals(Answer.YES, unread.holdsPath("/repo/src/Main.kt"));
        assertEquals(List.of("scala", "kotlin"), new ArrayList<>(unread.languages()));
    }

    @Test
    public void anArchivesScalaAndKotlinAreAbsentFromEverythingElse() throws Exception {
        ProjectFiles projectFiles = zipOf(
                entry("repo/src/Main.java", JAVA_SOURCE),
                entry("repo/src/Main.scala", SCALA_SOURCE),
                entry("repo/src/Main.kt", KOTLIN_SOURCE));

        assertEquals(1, projectFiles.size());
        assertEquals(List.of("/repo/src/Main.java"), pathsOf(projectFiles));
        assertEquals(1, projectFiles.files(Lang.JAVA).size());
        assertTrue(projectFiles.matchingFilesByName("Main.scala").isEmpty());
        assertTrue(projectFiles.matchingFilesByName("Main.kt").isEmpty());
    }

    @Test
    public void unreadSourceFilesAreNotPersistedToTheProjectDir() throws Exception {
        ProjectFiles projectFiles = zipOf(
                entry("repo/src/Main.java", JAVA_SOURCE),
                entry("repo/src/Main.scala", SCALA_SOURCE));
        try {
            Path projectDir = Path.of(projectFiles.projectDir());
            assertTrue(Files.exists(projectDir.resolve("repo").resolve("src").resolve("Main.java")));
            assertFalse(Files.exists(projectDir.resolve("repo").resolve("src").resolve("Main.scala")));
        } finally {
            projectFiles.close();
        }
    }

    @Test
    public void anArchiveWithoutThemIsUnchanged() throws Exception {
        ProjectFiles withoutScala = zipOf(entry("repo/src/Main.java", JAVA_SOURCE));
        ProjectFiles withScala = zipOf(
                entry("repo/src/Main.java", JAVA_SOURCE),
                entry("repo/src/Main.scala", SCALA_SOURCE));

        assertTrue(withoutScala.unreadSourceFiles().paths().isEmpty());
        assertTrue(withoutScala.unreadSourceFiles().complete());
        assertEquals(Answer.NO, withoutScala.unreadSourceFiles().holdsFileNamed("Main.scala"));

        assertEquals(withScala.size(), withoutScala.size());
        assertEquals(pathsOf(withScala), pathsOf(withoutScala));
    }

    @Test
    public void filesThisLibraryParsesNeverReachTheRecord() throws Exception {
        ProjectFiles projectFiles = zipOf(
                entry("repo/src/Main.java", JAVA_SOURCE),
                entry("repo/src/app.ts", "export const greet = () => 'hi';"),
                entry("repo/src/app.py", "class App:\n    pass\n"),
                entry("repo/src/App.cs", "public class App { }"));

        assertTrue(projectFiles.unreadSourceFiles().paths().isEmpty());
        assertEquals(Answer.NO, projectFiles.unreadSourceFiles().holdsFileNamed("Main.java"));
    }

    @Test
    public void filesThatAreNotSourceNeverReachTheRecord() throws Exception {
        ProjectFiles projectFiles = zipOf(
                entry("repo/src/Main.java", JAVA_SOURCE),
                entry("repo/README.md", README),
                entry("repo/yarn.lock", "# lockfile\n"),
                entry("repo/data/rows.csv", "a,b\n1,2\n"),
                entry("repo/assets/logo.png", LOGO));

        assertTrue(projectFiles.unreadSourceFiles().paths().isEmpty());
        assertTrue(projectFiles.unreadSourceFiles().complete());
    }

    @Test
    public void aDirectorysRustIsReachableThroughTheRecordAndAbsentFromTheFiles() throws Exception {
        Path dir = Files.createTempDirectory("clarpse-unread-source-files");
        try {
            Files.writeString(dir.resolve("Main.java"), JAVA_SOURCE, StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("main.rs"), "fn main() {}\n", StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("README.md"), README, StandardCharsets.UTF_8);

            ProjectFiles projectFiles = new ProjectFiles(dir.toString());

            assertEquals(1, projectFiles.size());
            assertTrue(projectFiles.matchingFilesByName("main.rs").isEmpty());
            assertEquals(Answer.YES, projectFiles.unreadSourceFiles().holdsFileNamed("main.rs"));
            assertEquals(Answer.NO, projectFiles.unreadSourceFiles().holdsFileNamed("README.md"));
        } finally {
            FileUtils.deleteQuietly(dir.toFile());
        }
    }

    @Test
    public void anInsertedUnreadSourceFileIsRecordedAndStillNotHeld() {
        ProjectFiles projectFiles = new ProjectFiles();
        projectFiles.insertFile(new ProjectFile("/cmd/main.go", GO_SOURCE));

        assertEquals(0, projectFiles.size());
        assertTrue(projectFiles.files().isEmpty());
        assertEquals(Answer.YES, projectFiles.unreadSourceFiles().holdsPath("/cmd/main.go"));
        assertEquals(Answer.YES, projectFiles.unreadSourceFiles().holdsPath("cmd/main.go"));
        assertEquals(Answer.NO, projectFiles.unreadSourceFiles().holdsPath("/cmd/other.go"));
    }

    @Test
    public void aCopyCarriesTheRecord() {
        ProjectFiles projectFiles = new ProjectFiles();
        projectFiles.insertFile(new ProjectFile("/cmd/main.go", GO_SOURCE));

        UnreadSourceFiles copied = projectFiles.copy().unreadSourceFiles();

        assertTrue(copied.complete());
        assertEquals(Answer.YES, copied.holdsFileNamed("main.go"));
    }

    @Test
    public void removingAnUnreadSourceFileDropsItFromTheRecordWithoutBecomingARemoval() {
        ProjectFiles projectFiles = new ProjectFiles();
        projectFiles.insertFile(new ProjectFile("/cmd/main.go", GO_SOURCE));
        projectFiles.insertFile(new ProjectFile("/src/Main.java", JAVA_SOURCE));

        assertFalse(projectFiles.removeFile("/cmd/main.go"));
        assertEquals(1, projectFiles.size());
        assertEquals(Answer.NO, projectFiles.unreadSourceFiles().holdsFileNamed("main.go"));
    }

    @Test
    public void shiftingSubDirsLeftShiftsTheRecordedPaths() throws Exception {
        ProjectFiles projectFiles = zipOf(
                entry("repo/src/Main.java", JAVA_SOURCE),
                entry("repo/src/Main.scala", SCALA_SOURCE));

        projectFiles.shiftSubDirsLeft();

        assertTrue(projectFiles.unreadSourceFiles().complete());
        assertEquals(List.of("/src/Main.scala"),
                new ArrayList<>(projectFiles.unreadSourceFiles().paths()));
    }

    @Test
    public void aPathWithNothingToShiftLeavesTheRecordIncompleteRatherThanThrowing() throws Exception {
        ProjectFiles projectFiles = zipOf(
                entry("repo/Main.java", JAVA_SOURCE),
                entry("Main.scala", SCALA_SOURCE));

        projectFiles.shiftSubDirsLeft();

        UnreadSourceFiles unread = projectFiles.unreadSourceFiles();
        assertFalse(unread.complete());
        assertTrue(unread.paths().isEmpty());
        assertEquals(Answer.UNKNOWN, unread.holdsFileNamed("Main.scala"));
        assertEquals(Answer.UNKNOWN, unread.holdsFileNamed("Other.kt"));
        assertEquals(Answer.NO, unread.holdsFileNamed("Main.java"));
    }

    @Test
    public void anEntryWhosePathCannotBeKeptLeavesTheRecordIncomplete() throws Exception {
        ProjectFiles projectFiles = zipOf(
                entry("../escape.go", GO_SOURCE),
                entry("repo/src/Main.java", JAVA_SOURCE));

        assertEquals(1, projectFiles.size());
        assertFalse(projectFiles.unreadSourceFiles().complete());
        assertTrue(projectFiles.unreadSourceFiles().paths().isEmpty());
        assertEquals(Answer.UNKNOWN, projectFiles.unreadSourceFiles().holdsFileNamed("escape.go"));
    }

    @Test
    public void anUnsafeEntryThisRecordDoesNotCoverLeavesItComplete() throws Exception {
        ProjectFiles projectFiles = zipOf(
                entry("../escape.md", README),
                entry("repo/src/Main.java", JAVA_SOURCE));

        assertEquals(1, projectFiles.size());
        assertTrue(projectFiles.unreadSourceFiles().complete());
    }

    @Test
    public void anEntryTooLargeToReadIsStillRecorded() throws Exception {
        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        ProjectFiles projectFiles = zipOf(
                entry("repo/src/Huge.kt", oversized),
                entry("repo/src/Main.java", JAVA_SOURCE));

        assertEquals(1, projectFiles.size());
        assertTrue(projectFiles.unreadSourceFiles().complete());
        assertEquals(Answer.YES, projectFiles.unreadSourceFiles().holdsFileNamed("Huge.kt"));
    }

    @Test
    public void anObserverAndTheRecordSeeTheSameDiscardedSourceFile() throws Exception {
        byte[] archive = archiveOf(
                entry("repo/src/Main.java", JAVA_SOURCE),
                entry("repo/src/Main.scala", SCALA_SOURCE));
        List<String> observed = new ArrayList<>();
        DiscardedEntryObserver observer = (path, content, lastModified) -> observed.add(path);

        ProjectFiles projectFiles = ProjectFiles.fromZip(new ByteArrayInputStream(archive), observer);

        assertEquals(List.of("repo/src/Main.scala"), observed);
        assertEquals(1, projectFiles.size());
        assertEquals(Answer.YES, projectFiles.unreadSourceFiles().holdsFileNamed("Main.scala"));
    }

    @Test
    public void theRecordedPathsCannotBeChangedFromOutside() {
        ProjectFiles projectFiles = new ProjectFiles();
        projectFiles.insertFile(new ProjectFile("/cmd/main.go", GO_SOURCE));

        UnreadSourceFiles unread = projectFiles.unreadSourceFiles();
        projectFiles.insertFile(new ProjectFile("/cmd/other.go", GO_SOURCE));

        assertEquals(1, unread.paths().size());
        assertEquals(2, projectFiles.unreadSourceFiles().paths().size());
    }

    private static List<String> pathsOf(final ProjectFiles projectFiles) {
        List<String> paths = new ArrayList<>();
        for (ProjectFile file : projectFiles.files()) {
            paths.add(file.path());
        }
        paths.sort(String::compareTo);
        return paths;
    }

    private static ProjectFiles zipOf(final ArchiveEntry... entries) throws Exception {
        return new ProjectFiles(new ByteArrayInputStream(archiveOf(entries)));
    }

    private static byte[] archiveOf(final ArchiveEntry... entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (ArchiveEntry entry : entries) {
                zos.putNextEntry(new ZipEntry(entry.name));
                zos.write(entry.content);
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static ArchiveEntry entry(final String name, final String content) {
        return new ArchiveEntry(name, content.getBytes(StandardCharsets.UTF_8));
    }

    private static ArchiveEntry entry(final String name, final byte[] content) {
        return new ArchiveEntry(name, content);
    }

    private static final class ArchiveEntry {

        private final String name;
        private final byte[] content;

        private ArchiveEntry(final String name, final byte[] content) {
            this.name = name;
            this.content = content;
        }
    }
}
