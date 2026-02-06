package com.hadi.test;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.hadi.test.ClarpseTestUtil.unzipArchive;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProjectFilesTest {

    private static ProjectFiles zipPathProjectFiles;
    private static ProjectFiles InputStreamProjectFiles;
    private static ProjectFiles sourceDirProjectFiles;
    private static String sourceDir;


    @BeforeClass
    public static void setup() throws Exception {
        zipPathProjectFiles = new ProjectFiles(
            Objects.requireNonNull(ClarpseTestUtil.class.getResource("/clarpse.zip")).getFile());
        sourceDir = unzipArchive(
                new File(Objects.requireNonNull(ProjectFilesTest.class.getResource(
                        "/clarpse.zip")).toURI()));
        sourceDirProjectFiles = new ProjectFiles(sourceDir);
        InputStreamProjectFiles =
            new ProjectFiles(ClarpseTestUtil.class.getResourceAsStream("/clarpse.zip"));
    }

    @Test
    public void testFilesFromZipInputStreamFilesNo() {
        assertEquals(35, InputStreamProjectFiles.size());

    }

    @Test
    public void testFilesFromZipPathFilesNo() {
        assertEquals(35, zipPathProjectFiles.size());
    }

    @Test
    public void testPersistedDirFromSourceDir() {
        assertEquals(sourceDirProjectFiles.projectDir(), sourceDir);
    }

    @Test
    public void testPersistedDirFromZipPath() {
        assertFalse(zipPathProjectFiles.projectDir().isEmpty());
    }

    @Test
    public void testPersistedDirFromInputStream() {
        assertFalse(InputStreamProjectFiles.projectDir().isEmpty());
    }

    @Test
    public void testFilesFromSourceDirFilesNo() {
        assertEquals(35, sourceDirProjectFiles.size());
    }

    @Test
    public void testParseEmptyJavaProjectFiles() throws Exception {
        assertEquals(0,
                new ClarpseProject(new ProjectFiles(Collections.emptyList()), Lang.JAVA).result().model().size());
    }

    @Test
    public void testZipInputStreamParsesJavaCompiler() throws Exception {
        assertTrue(new ClarpseProject(InputStreamProjectFiles, Lang.JAVA)
                .result().model().containsComponent("com.hadi.clarpse.compiler.ClarpseJavaCompiler"));
    }

    @Test
    public void testSourceDirParsesJavaCompiler() throws Exception {
        assertTrue(new ClarpseProject(sourceDirProjectFiles, Lang.JAVA)
                .result().model().containsComponent("com.hadi.clarpse.compiler.ClarpseJavaCompiler"));
    }

    @Test
    public void testShiftSubDirs() {
        ProjectFiles projectFiles = new ProjectFiles();
        ProjectFile projectFile = new ProjectFile("/test/lol/cakes.java", "{}");
        projectFiles.insertFile(projectFile);
        projectFiles.shiftSubDirsLeft();
        assertEquals("/lol/cakes.java",
                     new ArrayList<>(projectFiles.files(Lang.JAVA)).get(0).path());
    }

    @Test
    public void testFilterByNonExistentPath() {
        ProjectFiles projectFiles = new ProjectFiles();
        ProjectFile projectFile = new ProjectFile("/test/lol/cakes.java", "{}");
        projectFiles.insertFile(projectFile);
        ArrayList<String> filterPaths = new ArrayList<>();
        filterPaths.add("/");
        projectFiles.filter(filterPaths);
        // Should remove everything...
        assertEquals(0, projectFiles.size());
    }

    @Test
    public void testProjectFilesSize() {
        ProjectFiles pfs = new ProjectFiles();
        pfs.insertFile(new ProjectFile("/test.java", "{}"));
        pfs.insertFile(new ProjectFile("/tester.java", "{}"));
        assertEquals(2, pfs.size());
    }

    @Test
    public void testGetAllFiles() {
        ProjectFiles pfs = new ProjectFiles();
        pfs.insertFile(new ProjectFile("/test.java", "{}"));
        pfs.insertFile(new ProjectFile("/tester.java", "{}"));
        assertEquals(2, pfs.files().size());
    }

    @Test
    public void testGetAllJavaFiles() {
        ProjectFiles pfs = new ProjectFiles();
        pfs.insertFile(new ProjectFile("/test.java", "{}"));
        pfs.insertFile(new ProjectFile("/tester.java", "{}"));
        assertEquals(2, pfs.files(Lang.JAVA).size());
    }

    @Test
    public void testInsertUnsupportedFileIsSkipped() {
        ProjectFiles pfs = new ProjectFiles();
        pfs.insertFile(new ProjectFile("/script.js", "{}"));
        assertEquals(0, pfs.size());
    }

    @Test
    public void testEmptyProjectFilesSize() {
        ProjectFiles pfs = new ProjectFiles();
        assertEquals(0, pfs.size());
    }

    @Test
    public void testProjectFilesSizeAfterFilter() {
        ProjectFiles pfs = new ProjectFiles();
        pfs.insertFile(new ProjectFile("/test.java", "{}"));
        pfs.insertFile(new ProjectFile("/tester.java", "{}"));
        pfs.filter(List.of("/test.java"));
        assertEquals(1, pfs.size());
    }

    @Test
    public void testMatchingFilesByName() {
        ProjectFiles pfs = new ProjectFiles();
        pfs.insertFile(new ProjectFile("/test.java", "{}"));
        assertEquals(1, pfs.matchingFilesByName("test.java").size());
    }

    @Test
    public void testMatchingFilesByNameNoMatch() {
        ProjectFiles pfs = new ProjectFiles();
        pfs.insertFile(new ProjectFile("/test.java", "{}"));
        assertEquals(0, pfs.matchingFilesByName("missing.java").size());
    }

    @Test
    public void testFilterByExistentPath() {
        ProjectFiles projectFiles = new ProjectFiles();
        ProjectFile projectFile = new ProjectFile("/test/lol/cakes.java", "{}");
        projectFiles.insertFile(projectFile);
        ArrayList<String> filterFilePaths = new ArrayList<>();
        filterFilePaths.add("/test/lol/cakes.java");
        projectFiles.filter(filterFilePaths);
        assertEquals(1, projectFiles.size());
    }

    @Test
    public void testShiftSubDirsTwice() {
        ProjectFiles projectFiles = new ProjectFiles();
        ProjectFile projectFile = new ProjectFile("/test/lol.java", "{}");
        projectFiles.insertFile(projectFile);
        projectFiles.shiftSubDirsLeft();
        assertEquals("/lol.java", new ArrayList<>(projectFiles.files(Lang.JAVA)).get(0).path());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testShiftSubDirsInvalid() {
        ProjectFiles projectFiles = new ProjectFiles();
        ProjectFile projectFile = new ProjectFile("/cakes.java", "{}");
        projectFiles.insertFile(projectFile);
        projectFiles.shiftSubDirsLeft();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testZipEntryLimitAppliesToAllEntries() throws Exception {
        File zipFile = createZipWithEntries(10001);
        try {
            new ProjectFiles(zipFile.getAbsolutePath());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            zipFile.delete();
        }
    }

    private static File createZipWithEntries(final int count) throws IOException {
        File tmp = File.createTempFile("clarpse-zip-limit", ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tmp))) {
            for (int i = 0; i < count; i++) {
                ZipEntry entry = new ZipEntry("file-" + i + ".txt");
                zos.putNextEntry(entry);
                zos.write(0);
                zos.closeEntry();
            }
        }
        return tmp;
    }
}
