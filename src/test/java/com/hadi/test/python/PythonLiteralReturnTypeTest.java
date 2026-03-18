package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonLiteralReturnTypeTest {

    private static final String FIXTURE = "literal-return-types";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "literals";

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void literalStringReturnTypeIsNormalizedToStr() {
        String methodName = name("LiteralTypes." + PythonTestUtil.signature("text", "str"));
        Component method = model.getComponent(methodName).orElseThrow();
        Assert.assertEquals("text() : str", method.codeFragment());
    }

    @Test
    public void literalIntegerReturnTypeIsNormalizedToInt() {
        String methodName = name("LiteralTypes." + PythonTestUtil.signature("count", "int"));
        Component method = model.getComponent(methodName).orElseThrow();
        Assert.assertEquals("count() : int", method.codeFragment());
    }

    @Test
    public void literalBooleanReturnTypeIsNormalizedToBool() {
        String methodName = name("LiteralTypes." + PythonTestUtil.signature("flag", "bool"));
        Component method = model.getComponent(methodName).orElseThrow();
        Assert.assertEquals("flag() : bool", method.codeFragment());
    }

    private static String name(final String symbolPath) {
        return PythonTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
