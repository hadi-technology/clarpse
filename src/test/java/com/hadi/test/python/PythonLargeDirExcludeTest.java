package com.hadi.test.python;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Files under an excluded directory are not in the model, at a size where the exclusion has to be
 * doing real work.
 *
 * <p>Deliberately has no time budget, for the reason recorded on
 * {@link PythonLargeZipExcludeTest}: the cost of a Python compile is dominated by starting the
 * daemon, so a budget large enough to survive a cold start is far larger than the cost of parsing
 * the files that were supposed to be skipped. It measured which test ran first, not whether
 * exclusion worked.
 */
public class PythonLargeDirExcludeTest {

    /**
     * Writes {@code src/app.py} plus 300 modules under {@code <bulkDir>/lib/site-packages}.
     */
    private static Path buildProject(final String bulkDir) throws Exception {
        Path root = Files.createTempDirectory("clarpse-large-py");
        Path srcDir = root.resolve("src");
        Files.createDirectories(srcDir);
        Files.write(srcDir.resolve("app.py"), "class App:\n    pass\n".getBytes(StandardCharsets.UTF_8));

        Path bulk = root.resolve(bulkDir + "/lib/site-packages");
        Files.createDirectories(bulk);
        for (int i = 0; i < 300; i++) {
            Files.write(bulk.resolve("ignored" + i + ".py"),
                    ("class Ignored" + i + ":\n    pass\n").getBytes(StandardCharsets.UTF_8));
        }
        return root;
    }

    @Test
    public void testLargeDirExcludedDirsAreIgnored() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        Path root = buildProject(".venv");
        try {
            ProjectFiles files = new ProjectFiles(root.toString());
            CompileResult result = new ClarpseProject(files, Lang.PYTHON).result();
            OOPSourceCodeModel model = result.model();

            String appName = PythonTestUtil.uniqueName("src", "app", "App");
            String ignoredName = PythonTestUtil.uniqueName(".venv/lib/site-packages", "ignored0", "Ignored0");
            Assert.assertTrue(model.containsComponent(appName));
            Assert.assertFalse(model.containsComponent(ignoredName));
            Assert.assertTrue(result.failures().isEmpty());
        } finally {
            FileUtils.deleteQuietly(root.toFile());
        }
    }

    /**
     * The same 300 modules under a directory nobody excludes, which must all be parsed.
     *
     * <p>This is what keeps the assertion above from being vacuous. {@code assertFalse(contains(...))}
     * passes just as well when the parser found nothing anywhere -- a broken exclusion and a broken
     * parser produce the same empty answer, and the test that was supposed to separate them was a
     * stopwatch that could not. Renaming the directory is the one variable that changes, so if
     * these 300 modules appear here and not there, the exclusion is what did it.
     */
    @Test
    public void testUnexcludedDirsOfTheSameSizeAreParsed() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        Path root = buildProject("vendorlib");
        try {
            ProjectFiles files = new ProjectFiles(root.toString());
            CompileResult result = new ClarpseProject(files, Lang.PYTHON).result();
            OOPSourceCodeModel model = result.model();

            String appName = PythonTestUtil.uniqueName("src", "app", "App");
            String parsedName = PythonTestUtil.uniqueName("vendorlib/lib/site-packages", "ignored0", "Ignored0");
            Assert.assertTrue(model.containsComponent(appName));
            Assert.assertTrue(model.containsComponent(parsedName));
            Assert.assertTrue(result.failures().isEmpty());
        } finally {
            FileUtils.deleteQuietly(root.toFile());
        }
    }
}
