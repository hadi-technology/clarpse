package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonCodeFragmentTest {

    private static final String FIXTURE = "method-components";
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
    public void fieldCodeFragmentTest() {
        Component field = model.copyOfComponent(name("Service.owner")).orElseThrow();
        Assert.assertEquals("owner : User", field.codeFragment());
    }

    @Test
    public void constructorCodeFragmentTest() {
        String ctorName = name("Service." + PythonTestUtil.signature("__init__", "None", "user: User"));
        Component ctor = model.copyOfComponent(ctorName).orElseThrow();
        Assert.assertEquals("__init__(user: User) : None", ctor.codeFragment());
    }

    @Test
    public void methodCodeFragmentTest() {
        String methodName = name("Service." + PythonTestUtil.signature("update", "User", "user: User"));
        Component method = model.copyOfComponent(methodName).orElseThrow();
        Assert.assertEquals("update(user: User) : User", method.codeFragment());
    }

    @Test
    public void methodParamCodeFragmentTest() {
        String methodSig = PythonTestUtil.signature("update", "User", "user: User");
        String paramName = name("Service." + methodSig + ".user");
        Component param = model.copyOfComponent(paramName).orElseThrow();
        Assert.assertEquals("User", param.codeFragment());
    }

    private static String name(final String symbolPath) {
        return PythonTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
