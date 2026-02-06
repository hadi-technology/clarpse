package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.reference.TypeExtensionReference;
import com.hadi.clarpse.reference.TypeImplementationReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertTrue;

public class TypeScriptClaudeMemSpotCheckTest {

    private static final String ZIP_NAME = "claude-mem-main_ready.zip";
    private static OOPSourceCodeModel model;
    private static Path tempDir;

    @BeforeClass
    public static void setup() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        Path zipPath = Paths.get("src/test/resources/typescript", ZIP_NAME).toAbsolutePath();
        Assume.assumeTrue("Zip fixture missing: " + zipPath, Files.exists(zipPath));

        tempDir = Files.createTempDirectory("clarpse-claude-mem");
        unzip(zipPath, tempDir);
        Path repoRoot = tempDir.resolve("claude-mem-main");
        Assume.assumeTrue("Zip root missing: " + repoRoot, Files.exists(repoRoot));

        writeTsconfig(repoRoot);
        ensureTypeScriptAvailable(repoRoot);

        ProjectFiles projectFiles = new ProjectFiles(repoRoot.toString());
        Path srcRoot = repoRoot.resolve("src").toAbsolutePath();
        List<String> keep = new ArrayList<>();
        for (ProjectFile file : projectFiles.files()) {
            Path filePath = Paths.get(file.path()).toAbsolutePath();
            if (filePath.startsWith(srcRoot)) {
                keep.add(file.path());
            }
        }
        projectFiles.filter(keep);
        CompileResult result = new ClarpseProject(projectFiles, Lang.TYPESCRIPT).result();
        if (!result.skipped().isEmpty()) {
            Assume.assumeTrue("TypeScript resolver unavailable.", false);
        }
        model = result.model();
    }

    @AfterClass
    public static void cleanup() {
        if (tempDir != null) {
            FileUtils.deleteQuietly(tempDir.toFile());
        }
    }

    @Test
    public void spotCheckComponentsAndMethods() {
        assertPresent(name("src/services/domain", "ModeManager", "ModeManager"));
        assertPresent(name("src/services/domain", "types", "ModeConfig"));
        assertPresent(name("src/services/domain", "types", "ObservationType"));
        assertPresent(name("src/services/worker", "SearchManager", "SearchManager"));
        assertPresent(name("src/services/worker/http/routes", "SearchRoutes", "SearchRoutes"));
        assertPresent(name("src/services/worker/http", "BaseRouteHandler", "BaseRouteHandler"));
        assertPresent(name("src/services/worker/search/strategies", "HybridSearchStrategy", "HybridSearchStrategy"));
        assertPresent(name("src/services/worker/search/strategies", "SearchStrategy", "BaseSearchStrategy"));
        assertPresent(name("src/services/worker/search/strategies", "SearchStrategy", "SearchStrategy"));

        assertPresent(name("src/services/domain", "ModeManager",
                "ModeManager." + TypeScriptTestUtil.signature("getInstance")));
        assertPresent(name("src/services/domain", "ModeManager",
                "ModeManager." + TypeScriptTestUtil.signature("getActiveMode")));
    }

    @Test
    public void spotCheckFunctions() {
        assertPresent(name("src/sdk", "parser", "parseObservations"));
        assertPresent(name("src/sdk", "parser", "parseSummary"));
        assertPresent(name("src/sdk", "prompts", "buildInitPrompt"));
        assertPresent(name("src/sdk", "prompts", "buildSummaryPrompt"));
        assertPresent(name("src/utils", "tag-stripping", "stripMemoryTagsFromJson"));
        assertPresent(name("src/utils", "project-name", "getProjectName"));
        assertPresent(name("src/utils", "worktree", "detectWorktree"));
        assertPresent(name("src/services/context", "ObservationCompiler", "queryObservations"));
        assertPresent(name("src/services/context", "ObservationCompiler", "querySummaries"));
    }

    @Test
    public void spotCheckReferencesAndRelations() {
        String hybrid = name("src/services/worker/search/strategies", "HybridSearchStrategy", "HybridSearchStrategy");
        String baseStrategy = name("src/services/worker/search/strategies", "SearchStrategy", "BaseSearchStrategy");
        String strategy = name("src/services/worker/search/strategies", "SearchStrategy", "SearchStrategy");
        assertTrue(model.getComponent(hybrid).orElseThrow()
                .references(OOPSourceModelConstants.TypeReferences.EXTENSION)
                .contains(new TypeExtensionReference(baseStrategy)));
        assertTrue(model.getComponent(hybrid).orElseThrow()
                .references(OOPSourceModelConstants.TypeReferences.IMPLEMENTATION)
                .contains(new TypeImplementationReference(strategy)));

        String searchRoutes = name("src/services/worker/http/routes", "SearchRoutes", "SearchRoutes");
        String baseRouteHandler = name("src/services/worker/http", "BaseRouteHandler", "BaseRouteHandler");
        assertTrue(model.getComponent(searchRoutes).orElseThrow()
                .references(OOPSourceModelConstants.TypeReferences.EXTENSION)
                .contains(new TypeExtensionReference(baseRouteHandler)));

        assertSimpleReference(searchRoutes, name("src/services/worker", "SearchManager", "SearchManager"));
        assertSimpleReference(name("src/services/worker", "SearchManager", "SearchManager"),
                name("src/services/sqlite", "SessionStore", "SessionStore"));
        assertSimpleReference(name("src/services/domain", "ModeManager", "ModeManager"),
                name("src/services/domain", "types", "ModeConfig"));
    }

    private static void assertPresent(final String uniqueName) {
        assertTrue("Missing component: " + uniqueName, model.getComponent(uniqueName).isPresent());
    }

    private static void assertSimpleReference(final String owner, final String target) {
        assertTrue(model.getComponent(owner).orElseThrow()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE)
                .contains(new SimpleTypeReference(target)));
        assertTrue("Missing referenced component: " + target, model.containsComponent(target));
    }

    private static String name(final String packagePath, final String moduleName, final String symbolPath) {
        return TypeScriptTestUtil.uniqueName(packagePath, moduleName, symbolPath);
    }

    private static void writeTsconfig(final Path repoRoot) throws IOException {
        Path tsconfig = repoRoot.resolve("tsconfig.json");
        if (Files.exists(tsconfig)) {
            return;
        }
        String content = "{\n"
                + "  \"compilerOptions\": {\n"
                + "    \"target\": \"ES2019\",\n"
                + "    \"module\": \"NodeNext\",\n"
                + "    \"moduleResolution\": \"NodeNext\",\n"
                + "    \"jsx\": \"react-jsx\",\n"
                + "    \"strict\": true,\n"
                + "    \"esModuleInterop\": true,\n"
                + "    \"skipLibCheck\": true\n"
                + "  },\n"
                + "  \"include\": [\"src/**/*\"]\n"
                + "}\n";
        Files.writeString(tsconfig, content, StandardCharsets.UTF_8);
    }

    private static void ensureTypeScriptAvailable(final Path repoRoot) throws IOException {
        Path cwd = Paths.get(System.getProperty("user.dir"));
        Path globalTypescript = cwd.resolve("..").resolve("node_modules").resolve("typescript").normalize()
                .toAbsolutePath();
        if (!Files.exists(globalTypescript)) {
            return;
        }
        Path nodeModules = repoRoot.resolve("node_modules");
        Files.createDirectories(nodeModules);
        Path localTypescript = nodeModules.resolve("typescript");
        if (Files.exists(localTypescript)) {
            return;
        }
        try {
            Files.createSymbolicLink(localTypescript, globalTypescript);
        } catch (final IOException | UnsupportedOperationException e) {
            FileUtils.copyDirectory(globalTypescript.toFile(), localTypescript.toFile());
        }
    }

    private static void unzip(final Path zipPath, final Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path outputPath = destDir.resolve(entry.getName()).normalize();
                if (!outputPath.startsWith(destDir)) {
                    throw new IOException("Zip entry outside target: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Path parent = outputPath.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(zis, outputPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }
}
