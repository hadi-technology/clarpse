package com.hadi.test;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import org.junit.BeforeClass;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    @Test
    public void testRelativeProjectFilePathIsNormalized() {
        ProjectFile projectFile = new ProjectFile("src/main/java/Test.java", "class Test {}");
        assertEquals("/src/main/java/Test.java", projectFile.path());
    }

    @Test
    public void testWindowsProjectFilePathIsSupported() {
        ProjectFile projectFile = new ProjectFile("C:\\repo\\src\\main\\java\\Test.java", "class Test {}");
        assertEquals("C:/repo/src/main/java/Test.java", projectFile.path());
    }

    @Test
    public void testTsconfigPersistedFromZip() throws Exception {
        Path zipPath = Files.createTempFile("tsconfig", ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            ZipEntry configEntry = new ZipEntry("project/tsconfig.json");
            zos.putNextEntry(configEntry);
            String configContent = "{\"compilerOptions\":{\"allowJs\":true}}";
            zos.write(configContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            ZipEntry tsEntry = new ZipEntry("project/src/main/typescript/app.ts");
            zos.putNextEntry(tsEntry);
            String tsContent = "export const greet = () => 'hi';";
            zos.write(tsContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        ProjectFiles projectFiles;
        try (var in = Files.newInputStream(zipPath)) {
            projectFiles = new ProjectFiles(in);
        }

        String projectDir = projectFiles.projectDir();
        Path tsconfig = Paths.get(projectDir, "tsconfig.json");
        assertTrue("tsconfig should be persisted", Files.exists(tsconfig));
        assertEquals("{\"compilerOptions\":{\"allowJs\":true}}",
                Files.readString(tsconfig, StandardCharsets.UTF_8));
    }

    @Test
    public void testPyrightConfigPersistedFromZip() throws Exception {
        Path zipPath = Files.createTempFile("pyrightconfig", ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            ZipEntry configEntry = new ZipEntry("project/pyrightconfig.json");
            zos.putNextEntry(configEntry);
            String configContent = "{\"pythonVersion\":\"3.11\"}";
            zos.write(configContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            ZipEntry pyEntry = new ZipEntry("project/src/main/python/app.py");
            zos.putNextEntry(pyEntry);
            String pyContent = "class App:\n    pass\n";
            zos.write(pyContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        ProjectFiles projectFiles;
        try (var in = Files.newInputStream(zipPath)) {
            projectFiles = new ProjectFiles(in);
        }

        String projectDir = projectFiles.projectDir();
        Path pyrightConfig = Paths.get(projectDir, "pyrightconfig.json");
        assertTrue("pyrightconfig.json should be persisted", Files.exists(pyrightConfig));
        assertEquals("{\"pythonVersion\":\"3.11\"}",
                Files.readString(pyrightConfig, StandardCharsets.UTF_8));
    }

    @Test
    public void testPyprojectPersistedFromZip() throws Exception {
        Path zipPath = Files.createTempFile("pyproject", ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            ZipEntry configEntry = new ZipEntry("project/pyproject.toml");
            zos.putNextEntry(configEntry);
            String configContent = "[tool.pyright]\npythonVersion = \"3.12\"\n";
            zos.write(configContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            ZipEntry pyEntry = new ZipEntry("project/src/main/python/app.py");
            zos.putNextEntry(pyEntry);
            String pyContent = "class App:\n    pass\n";
            zos.write(pyContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        ProjectFiles projectFiles;
        try (var in = Files.newInputStream(zipPath)) {
            projectFiles = new ProjectFiles(in);
        }

        String projectDir = projectFiles.projectDir();
        Path pyproject = Paths.get(projectDir, "pyproject.toml");
        assertTrue("pyproject.toml should be persisted", Files.exists(pyproject));
        assertEquals("[tool.pyright]\npythonVersion = \"3.12\"\n",
                Files.readString(pyproject, StandardCharsets.UTF_8));
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

    @Test
    public void testTempProjectDirPersistsAcrossCompilers() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        Path zipPath = Files.createTempFile("clarpse-mixed", ".zip");
        try {
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
                ZipEntry configEntry = new ZipEntry("project/tsconfig.json");
                zos.putNextEntry(configEntry);
                String configContent = "{\"compilerOptions\":{\"allowJs\":true}}";
                zos.write(configContent.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                ZipEntry tsEntry = new ZipEntry("project/src/main/typescript/app.ts");
                zos.putNextEntry(tsEntry);
                String tsContent = "export const greet = () => 'hi';";
                zos.write(tsContent.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                ZipEntry javaEntry = new ZipEntry("project/src/main/java/com/example/Greeting.java");
                zos.putNextEntry(javaEntry);
                String javaContent = "package com.example;\n"
                        + "public class Greeting {\n"
                        + "  public String message() {\n"
                        + "    return \"hello\";\n"
                        + "  }\n"
                        + "}\n";
                zos.write(javaContent.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            ProjectFiles projectFiles;
            try (var in = Files.newInputStream(zipPath)) {
                projectFiles = new ProjectFiles(in);
            }

            String projectDir = projectFiles.projectDir();
            Path projectDirPath = Paths.get(projectDir);
            assertTrue(Files.exists(projectDirPath));

            new ClarpseProject(projectFiles, Lang.TYPESCRIPT).result();
            assertTrue(Files.exists(projectDirPath));

            new ClarpseProject(projectFiles, Lang.JAVA).result();
            assertTrue(Files.exists(projectDirPath));

            projectFiles.close();
            assertFalse(Files.exists(projectDirPath));
        } finally {
            Files.deleteIfExists(zipPath);
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
