package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonExcludeDirTest {

    private static final String FIXTURE = "large-deps";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    @Test
    public void testExcludedVenvFilesNotParsed() {
        String appName = PythonTestUtil.uniqueName("src", "app", "App");
        Assert.assertTrue(model.containsComponent(appName));
        String ignoredName = PythonTestUtil.uniqueName(".venv/lib/site-packages", "ignored", "Ignored");
        Assert.assertFalse(model.containsComponent(ignoredName));
    }
}
