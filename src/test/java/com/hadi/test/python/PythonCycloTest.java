package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonCycloTest {

    private static final String FIXTURE = "cyclo";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "cyclo";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void constructorCycloIncludesBranchAndBooleanOperator() {
        String ctorName = name("Metrics." + PythonTestUtil.signature("__init__", "None", "value: int"));
        Assert.assertEquals(3, model.copyOfComponent(ctorName).orElseThrow().cyclo());
    }

    @Test
    public void methodCycloIncludesIfAndElif() {
        String methodName = name("Metrics." + PythonTestUtil.signature("evaluate", "int", "limit: int"));
        Assert.assertEquals(3, model.copyOfComponent(methodName).orElseThrow().cyclo());
    }

    @Test
    public void functionCycloIncludesLoopAndBooleanOperator() {
        String functionName = name(PythonTestUtil.signature("compute", "int", "total: int"));
        Assert.assertEquals(3, model.copyOfComponent(functionName).orElseThrow().cyclo());
    }

    private static String name(final String symbolPath) {
        return PythonTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
