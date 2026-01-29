package com.hadi.test;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
    public void testZipInputStreamComponentCheck() throws Exception {
        assertTrue(new ClarpseProject(InputStreamProjectFiles, Lang.JAVA)
                .result().model().getComponent(
            "com.hadi.clarpse.listener.GoLangTreeListener.currPkg").isPresent());
    }

    @Test
    public void testZipPathComponentCheck() throws Exception {
        assertTrue(new ClarpseProject(zipPathProjectFiles, Lang.JAVA).result().model().getComponent(
            "com.hadi.clarpse.listener.GoLangTreeListener.currPkg").isPresent());
    }

    @Test
    public void testSourceDirFilesComponentCheck() throws Exception {
        assertTrue(new ClarpseProject(sourceDirProjectFiles, Lang.JAVA).result().model().getComponent(
            "com.hadi.clarpse.listener.GoLangTreeListener.currPkg").isPresent());
    }

    @Test
    public void testParseEmptyJavaProjectFiles() throws Exception {
        assertEquals(0,
                new ClarpseProject(new ProjectFiles(Collections.emptyList()), Lang.JAVA).result().model().size());
    }

    @Test
    public void testParseEmptyGoLangProjectFiles() throws Exception {
        assertEquals(0, new ClarpseProject(new ProjectFiles(Collections.emptyList()), Lang.GOLANG).result().model().size());
    }

    @Test
    public void testParseEmptyJavascriptProjectFiles() throws Exception {
        assertEquals(0, new ClarpseProject(new ProjectFiles(Collections.emptyList()), Lang.JAVASCRIPT).result().model().size());
    }

    @Test
    public void testShiftSubDirs() {
        ProjectFiles projectFiles = new ProjectFiles();
        ProjectFile projectFile = new ProjectFile("/test/lol/cakes.go", "{}");
        projectFiles.insertFile(projectFile);
        projectFiles.shiftSubDirsLeft();
        assertEquals("/lol/cakes.go",
                     new ArrayList<>(projectFiles.files(Lang.GOLANG)).get(0).path());
    }

    @Test
    public void testFilterByNonExistentPath() {
        ProjectFiles projectFiles = new ProjectFiles();
        ProjectFile projectFile = new ProjectFile("/test/lol/cakes.go", "{}");
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
        pfs.insertFile(new ProjectFile("/tester.go", "{}"));
        assertEquals(2, pfs.files().size());
    }

    @Test
    public void testGetAllGoFiles() {
        ProjectFiles pfs = new ProjectFiles();
        pfs.insertFile(new ProjectFile("/test.java", "{}"));
        pfs.insertFile(new ProjectFile("/tester.go", "{}"));
        assertEquals(1, pfs.files(Lang.GOLANG).size());
    }

    @Test
    public void testGetAllFilesForNonExistentLang() {
        ProjectFiles pfs = new ProjectFiles();
        pfs.insertFile(new ProjectFile("/test.java", "{}"));
        pfs.insertFile(new ProjectFile("/tester.go", "{}"));
        assertEquals(0, pfs.files(Lang.JAVASCRIPT).size());
    }

    @Test
    public void testGetAllJavaFiles() {
        ProjectFiles pfs = new ProjectFiles();
        pfs.insertFile(new ProjectFile("/test.java", "{}"));
        pfs.insertFile(new ProjectFile("/tester.go", "{}"));
        assertEquals(1, pfs.files(Lang.JAVA).size());
    }

    @Test
    public void testGetAllJSFiles() {
        ProjectFiles pfs = new ProjectFiles();
        pfs.insertFile(new ProjectFile("/test.js", "{}"));
        pfs.insertFile(new ProjectFile("/tester.go", "{}"));
        assertEquals(1, pfs.files(Lang.JAVASCRIPT).size());
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
        pfs.insertFile(new ProjectFile("/go.mod", "{}"));
        assertEquals(1, pfs.matchingFilesByName("go.mod").size());
    }

    @Test
    public void testMatchingFilesByNameNoMatch() {
        ProjectFiles pfs = new ProjectFiles();
        pfs.insertFile(new ProjectFile("/go.mod", "{}"));
        assertEquals(0, pfs.matchingFilesByName("go2.mod").size());
    }


    @Test
    public void testFilterByExistentPath() {
        ProjectFiles projectFiles = new ProjectFiles();
        ProjectFile projectFile = new ProjectFile("/test/lol/cakes.go", "{}");
        projectFiles.insertFile(projectFile);
        ArrayList<String> filterFilePaths = new ArrayList<>();
        filterFilePaths.add("/test/lol/cakes.go");
        projectFiles.filter(filterFilePaths);
        assertEquals(1, projectFiles.size());
    }
    @Test
    public void testShiftSubDirsv2() {
        ProjectFiles projectFiles = new ProjectFiles();
        ProjectFile projectFile = new ProjectFile("/test/lol.go", "{}");
        projectFiles.insertFile(projectFile);
        projectFiles.shiftSubDirsLeft();
        assertEquals("/lol.go", new ArrayList<>(projectFiles.files(Lang.GOLANG)).get(0).path());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testShiftSubDirsInvalid() {
        ProjectFiles projectFiles = new ProjectFiles();
        ProjectFile projectFile = new ProjectFile("/cakes.go", "{}");
        projectFiles.insertFile(projectFile);
        projectFiles.shiftSubDirsLeft();
    }

}
