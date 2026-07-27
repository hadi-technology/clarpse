package com.hadi.test;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import org.apache.commons.io.FileUtils;
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
    public void testInsertConfigFileIsExposedByFiles() {
        ProjectFiles pfs = new ProjectFiles();
        pfs.insertFile(new ProjectFile("/tsconfig.json", "{}"));
        assertEquals(1, pfs.size());
        assertEquals(1, pfs.files().size());
        assertTrue(pfs.files().stream().anyMatch(file -> "/tsconfig.json".equals(file.path())));
    }

    @Test
    public void testRemoveConfigFileRemovesItFromAllFiles() {
        ProjectFiles pfs = new ProjectFiles();
        pfs.insertFile(new ProjectFile("/package.json", "{\"name\":\"demo\"}"));
        assertTrue(pfs.removeFile("/package.json"));
        assertEquals(0, pfs.size());
        assertEquals(0, pfs.files().size());
    }

    @Test
    public void testEmptyProjectFilesSize() {
        ProjectFiles pfs = new ProjectFiles();
        assertEquals(0, pfs.size());
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
    public void testMatchingFilesByNameIncludesConfigFiles() {
        ProjectFiles pfs = new ProjectFiles();
        pfs.insertFile(new ProjectFile("/tsconfig.json", "{}"));
        assertEquals(1, pfs.matchingFilesByName("tsconfig.json").size());
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
        Path tsconfig = Paths.get(projectDir, "project", "tsconfig.json");
        assertTrue("tsconfig should be persisted", Files.exists(tsconfig));
        assertEquals("{\"compilerOptions\":{\"allowJs\":true}}",
                Files.readString(tsconfig, StandardCharsets.UTF_8));
    }

    @Test
    public void testZipLoadedConfigFileCountsTowardSizeAndCanBeRemoved() throws Exception {
        Path zipPath = Files.createTempFile("zip-config-only", ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            ZipEntry configEntry = new ZipEntry("project/tsconfig.json");
            zos.putNextEntry(configEntry);
            zos.write("{\"compilerOptions\":{}}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        ProjectFiles projectFiles;
        try (var in = Files.newInputStream(zipPath)) {
            projectFiles = new ProjectFiles(in);
        }

        assertEquals(1, projectFiles.size());
        assertTrue(projectFiles.removeFile("/project/tsconfig.json"));
        assertEquals(0, projectFiles.size());
        assertEquals(0, projectFiles.files().size());
    }

    @Test
    public void testConfigFilesFromDirAreExposed() throws Exception {
        Path tempDir = Files.createTempDirectory("clarpse-project-files");
        try {
            Files.writeString(tempDir.resolve("tsconfig.json"), "{\"compilerOptions\":{}}", StandardCharsets.UTF_8);
            Files.createDirectories(tempDir.resolve("src"));
            Files.writeString(tempDir.resolve("src").resolve("app.ts"), "export const app = 1;", StandardCharsets.UTF_8);

            ProjectFiles projectFiles = new ProjectFiles(tempDir.toString());

            assertEquals(2, projectFiles.size());
            assertTrue(projectFiles.files().stream().anyMatch(file -> "tsconfig.json".equals(file.name())));
            assertTrue(projectFiles.files(Lang.TYPESCRIPT).stream().anyMatch(file -> "app.ts".equals(file.name())));
        } finally {
            FileUtils.deleteQuietly(tempDir.toFile());
        }
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
        Path pyrightConfig = Paths.get(projectDir, "project", "pyrightconfig.json");
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
        Path pyproject = Paths.get(projectDir, "project", "pyproject.toml");
        assertTrue("pyproject.toml should be persisted", Files.exists(pyproject));
        assertEquals("[tool.pyright]\npythonVersion = \"3.12\"\n",
                Files.readString(pyproject, StandardCharsets.UTF_8));
    }

    @Test
    public void testNestedConfigPathIsPreservedWhenZipHasNoWrapperDir() throws Exception {
        Path zipPath = Files.createTempFile("nested-config", ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            ZipEntry configEntry = new ZipEntry("packages/app/tsconfig.json");
            zos.putNextEntry(configEntry);
            String configContent = "{\"include\":[\"src/**/*.ts\"]}";
            zos.write(configContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            ZipEntry tsEntry = new ZipEntry("packages/app/src/main.ts");
            zos.putNextEntry(tsEntry);
            zos.write("export const x = 1;".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        ProjectFiles projectFiles;
        try (var in = Files.newInputStream(zipPath)) {
            projectFiles = new ProjectFiles(in);
        }

        String projectDir = projectFiles.projectDir();
        Path expectedConfigPath = Paths.get(projectDir, "packages", "app", "tsconfig.json");
        Path shiftedConfigPath = Paths.get(projectDir, "app", "tsconfig.json");
        assertTrue("nested config should be persisted at original path", Files.exists(expectedConfigPath));
        assertFalse("config path should not be shifted", Files.exists(shiftedConfigPath));
    }

    @Test
    public void testShiftSubDirsLeftAlsoShiftsPersistedTsconfigPaths() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        Path zipPath = Files.createTempFile("wrapper-tsconfig", ".zip");
        try {
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
                ZipEntry configEntry = new ZipEntry("repo-root/apps/backend/tsconfig.json");
                zos.putNextEntry(configEntry);
                zos.write("""
                        {
                          "compilerOptions": {
                            "target": "ES2020",
                            "module": "CommonJS",
                            "strict": true
                          },
                          "include": ["src/**/*.ts"]
                        }
                        """.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                ZipEntry tsEntry = new ZipEntry("repo-root/apps/backend/src/service.ts");
                zos.putNextEntry(tsEntry);
                zos.write("""
                        export class Service {
                          greet(): string {
                            return "hi";
                          }
                        }
                        """.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            ProjectFiles projectFiles;
            try (var in = Files.newInputStream(zipPath)) {
                projectFiles = new ProjectFiles(in);
            }
            projectFiles.shiftSubDirsLeft();

            assertTrue(new ClarpseProject(projectFiles, Lang.TYPESCRIPT).result().failures().isEmpty());
        } finally {
            Files.deleteIfExists(zipPath);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testZipEntryLimitAppliesToAllEntries() throws Exception {
        File zipFile = createZipWithEntries(100001);
        try {
            new ProjectFiles(zipFile.getAbsolutePath());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            zipFile.delete();
        }
    }

    @Test
    public void testOversizedZipEntryIsSkippedAndRemainingEntriesStillLoad() throws Exception {
        Path zipPath = Files.createTempFile("clarpse-large-entry", ".zip");
        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        try {
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
                ZipEntry entry = new ZipEntry("project/big.py");
                zos.putNextEntry(entry);
                zos.write(oversized);
                zos.closeEntry();

                ZipEntry validEntry = new ZipEntry("project/good.py");
                zos.putNextEntry(validEntry);
                zos.write("class Good:\n    pass\n".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            ProjectFiles projectFiles;
            try (var in = Files.newInputStream(zipPath)) {
                projectFiles = new ProjectFiles(in);
            }

            assertEquals(1, projectFiles.size());
            assertTrue(projectFiles.files(Lang.PYTHON).stream()
                    .anyMatch(file -> "/project/good.py".equals(file.path())));
            assertFalse(projectFiles.files(Lang.PYTHON).stream()
                    .anyMatch(file -> "/project/big.py".equals(file.path())));
        } finally {
            Files.deleteIfExists(zipPath);
        }
    }

    @Test
    public void testUnsafeZipEntryIsSkippedAndRemainingEntriesStillLoad() throws Exception {
        Path zipPath = Files.createTempFile("clarpse-unsafe-entry", ".zip");
        try {
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
                ZipEntry unsafeEntry = new ZipEntry("../escape.py");
                zos.putNextEntry(unsafeEntry);
                zos.write("class Escape:\n    pass\n".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                ZipEntry validEntry = new ZipEntry("project/ok.py");
                zos.putNextEntry(validEntry);
                zos.write("class Ok:\n    pass\n".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            ProjectFiles projectFiles;
            try (var in = Files.newInputStream(zipPath)) {
                projectFiles = new ProjectFiles(in);
            }

            assertEquals(1, projectFiles.size());
            assertTrue(projectFiles.files(Lang.PYTHON).stream()
                    .anyMatch(file -> "/project/ok.py".equals(file.path())));
            assertFalse(projectFiles.files(Lang.PYTHON).stream()
                    .anyMatch(file -> file.path().contains("escape.py")));
        } finally {
            Files.deleteIfExists(zipPath);
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

    /**
     * A persisted temp dir whose owner never calls {@link ProjectFiles#close()} must still be removed
     * by the JVM-shutdown hook, so an interrupted or crashed process cannot leak extracted-source
     * temp dirs. Runs a child JVM that persists and exits without closing, then asserts the dir is gone.
     */
    @Test
    public void leakedTempDirIsDeletedByJvmShutdownHook() throws Exception {
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");
        Process proc = new ProcessBuilder(javaBin, "-cp", classpath, "com.hadi.test.TempDirLeakHelper")
                .redirectErrorStream(true)
                .start();
        String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        proc.waitFor();

        String tempDir = null;
        for (String line : output.split("\\R")) {
            if (line.startsWith("TEMPDIR:")) {
                tempDir = line.substring("TEMPDIR:".length()).trim();
            }
        }
        assertTrue("helper did not report a temp dir; child output:\n" + output,
                tempDir != null && !tempDir.isEmpty());
        assertFalse("temp dir leaked despite the shutdown hook: " + tempDir,
                Files.exists(Paths.get(tempDir)));
    }
}
