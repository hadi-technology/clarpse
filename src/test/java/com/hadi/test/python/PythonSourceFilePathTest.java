package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonSourceFilePathTest {

    private static final String FIXTURE = "component-types";
    private static final String PACKAGE_PATH = "src";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void testClassSourceFilePath() {
        Component component = model.getComponent(PythonTestUtil.uniqueName(PACKAGE_PATH, "sample", "Service"))
                .orElseThrow();
        assertEndsWith(component.sourceFile(), "/src/sample.py");
    }

    @Test
    public void testMethodSourceFilePath() {
        String signature = PythonTestUtil.signature("update", "User", "user: User");
        Component component = model.getComponent(PythonTestUtil.uniqueName(PACKAGE_PATH, "sample",
                        "Service." + signature))
                .orElseThrow();
        assertEndsWith(component.sourceFile(), "/src/sample.py");
    }

    @Test
    public void testFieldSourceFilePath() {
        Component component = model.getComponent(PythonTestUtil.uniqueName(PACKAGE_PATH, "sample", "Service.owner"))
                .orElseThrow();
        assertEndsWith(component.sourceFile(), "/src/sample.py");
    }

    @Test
    public void testModuleFieldSourceFilePath() {
        Component component = model.getComponent(PythonTestUtil.uniqueName(PACKAGE_PATH, "sample", "DEFAULT_USER"))
                .orElseThrow();
        assertEndsWith(component.sourceFile(), "/src/sample.py");
    }

    @Test
    public void testImportedClassSourceFilePath() {
        Component component = model.getComponent(PythonTestUtil.uniqueName(PACKAGE_PATH, "types", "User"))
                .orElseThrow();
        assertEndsWith(component.sourceFile(), "/src/types.py");
    }

    private static void assertEndsWith(final String actualPath, final String unixSuffix) {
        Assert.assertNotNull(actualPath);
        Assert.assertTrue(actualPath.endsWith(unixSuffix)
                || actualPath.endsWith(unixSuffix.replace('/', '\\')));
    }
}
