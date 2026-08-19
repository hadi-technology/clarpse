package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonModuleAliasDottedTest {

    private static final String FIXTURE = "module-alias-dotted";
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
    public void testModuleAliasDottedFieldResolvesInternal() {
        String fieldName = PythonTestUtil.uniqueName(PACKAGE_PATH, MODULE, "Service.current");
        Component field = model.copyOfComponent(fieldName).orElseThrow();
        Assert.assertTrue(hasSimpleRef(field, userTypeName()));
    }

    @Test
    public void testModuleAliasDottedMethodAndParamResolveInternal() {
        String signature = PythonTestUtil.signature("set_current", "model_types.User", "user: model_types.User");
        String methodName = PythonTestUtil.uniqueName(PACKAGE_PATH, MODULE, "Service." + signature);
        Component method = model.copyOfComponent(methodName).orElseThrow();
        Assert.assertTrue(hasSimpleRef(method, userTypeName()));

        String paramName = PythonTestUtil.uniqueName(PACKAGE_PATH, MODULE, "Service." + signature + ".user");
        Component param = model.copyOfComponent(paramName).orElseThrow();
        Assert.assertTrue(hasSimpleRef(param, userTypeName()));
    }

    private static String userTypeName() {
        return PythonTestUtil.uniqueName(PACKAGE_PATH, "models", "User");
    }

    private static boolean hasSimpleRef(final Component component, final String invokedName) {
        for (ComponentReference ref : component.references(TypeReferences.SIMPLE)) {
            if (invokedName.equals(ref.invokedComponent())) {
                return true;
            }
        }
        return false;
    }
}
