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

public class PythonLargeZipExcludeTest {

    @Test(timeout = 5000)
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
