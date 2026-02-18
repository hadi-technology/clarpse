package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonInitModuleTest {

    private static final String FIXTURE = "init-module";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void testInitPyIsParsedAsModule() {
        String className = PythonTestUtil.uniqueName("src/pkg", "__init__", "Root");
        Assert.assertTrue(model.containsComponent(className));
    }

    @Test
    public void testInitPyClassModuleAttribute() {
        String className = PythonTestUtil.uniqueName("src/pkg", "__init__", "Root");
        Component component = model.getComponent(className).orElseThrow();
        Assert.assertEquals("__init__", component.module());
    }
}
