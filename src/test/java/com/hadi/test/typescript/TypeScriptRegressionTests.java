package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class TypeScriptRegressionTests {

    private static final String FIXTURE = "regression-tests";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "ParameterizedTestIntegrationTests";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    @Test
    public void testParameterizedTestIntegrationTestsClassIsParsedProperly() {
        assertTrue(model.containsComponent(name("ParameterizedTestIntegrationTests")));
    }

    private static String name(final String symbolPath) {
        return TypeScriptTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
