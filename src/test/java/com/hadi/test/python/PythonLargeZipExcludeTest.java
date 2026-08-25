package com.hadi.test.python;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Files under an excluded directory are not in the model, at a size where the exclusion has to be
 * doing real work.
 *
 * <p>Deliberately has no time budget. It used to have a five-second one, on the theory that
 * reading 300 files it was supposed to ignore would take longer than ignoring them -- but a Python
 * compile spawns a daemon and unzips a 6 MB bundle to do it, and that start-up costs whole seconds
 * while 300 small modules cost almost nothing next to it. Measured both ways, parsing every file
 * and parsing one took the same time to within the noise, so the budget could not tell a working
 * exclusion from a broken one. What it could tell was whether the daemon was already warm, which
 * is to say whether some other test had run first: it failed in isolation and passed in a full
 * suite, on unmodified master.
 *
 * <p>The assertions below say directly what the budget was a proxy for, and say it exactly.
 */
public class PythonLargeZipExcludeTest {

    @Test
    public void testLargeZipExcludedDirsAreIgnored() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry appEntry = new ZipEntry("project/src/main.py");
            zos.putNextEntry(appEntry);
            zos.write("class Main:\n    pass\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            for (int i = 0; i < 300; i++) {
                String path = "project/.venv/lib/site-packages/ignored" + i + ".py";
                ZipEntry ignoredEntry = new ZipEntry(path);
                zos.putNextEntry(ignoredEntry);
                zos.write(("class Ignored" + i + ":\n    pass\n").getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }

        ProjectFiles files = new ProjectFiles(new ByteArrayInputStream(baos.toByteArray()));
        CompileResult result = new ClarpseProject(files, Lang.PYTHON).result();
        OOPSourceCodeModel model = result.model();

        String mainName = PythonTestUtil.uniqueName("project/src", "main", "Main");
        String ignoredName = PythonTestUtil.uniqueName("project/.venv/lib/site-packages", "ignored0", "Ignored0");
        Assert.assertTrue(model.containsComponent(mainName));
        Assert.assertFalse(model.containsComponent(ignoredName));
        Assert.assertTrue(result.failures().isEmpty());
    }
}
