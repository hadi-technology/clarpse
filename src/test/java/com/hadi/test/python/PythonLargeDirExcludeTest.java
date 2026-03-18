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

public class PythonLargeDirExcludeTest {

    @Test(timeout = 5000)
    public void testLargeDirExcludedDirsAreIgnored() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        Path root = Files.createTempDirectory("clarpse-large-py");
        try {
            Path srcDir = root.resolve("src");
            Files.createDirectories(srcDir);
            Files.write(srcDir.resolve("app.py"), "class App:\n    pass\n".getBytes(StandardCharsets.UTF_8));

            Path venvDir = root.resolve(".venv/lib/site-packages");
            Files.createDirectories(venvDir);
            for (int i = 0; i < 300; i++) {
                Files.write(venvDir.resolve("ignored" + i + ".py"),
                        ("class Ignored" + i + ":\n    pass\n").getBytes(StandardCharsets.UTF_8));
            }

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
}
