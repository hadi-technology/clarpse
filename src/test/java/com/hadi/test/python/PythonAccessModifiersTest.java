package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonAccessModifiersTest {

    private static final String FIXTURE = "access-modifiers";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "service";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void doubleUnderscoreMethodIsPrivate() {
        Component method = model.copyOfComponent(name("Service."
                + PythonTestUtil.signature("__hidden", "str"))).orElseThrow();
        Assert.assertTrue(method.modifiers().contains("private"));
    }

    @Test
    public void singleUnderscoreMethodIsProtected() {
        Component method = model.copyOfComponent(name("Service."
                + PythonTestUtil.signature("_helper", "str"))).orElseThrow();
        Assert.assertTrue(method.modifiers().contains("protected"));
    }

    @Test
    public void dunderMethodIsNotMarkedPrivate() {
        Component method = model.copyOfComponent(name("Service."
                + PythonTestUtil.signature("__str__", "str"))).orElseThrow();
        Assert.assertFalse(method.modifiers().contains("private"));
        Assert.assertFalse(method.modifiers().contains("protected"));
    }

    private static String name(final String symbolPath) {
        return PythonTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
