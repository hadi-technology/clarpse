package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonTypeCheckingImportTest {

    private static final String FIXTURE = "type-checking-imports";
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
    public void testTypeCheckingFieldResolvesInternal() {
        String fieldName = PythonTestUtil.uniqueName(PACKAGE_PATH, MODULE, "Service.user");
        Component field = model.getComponent(fieldName).orElseThrow();
        String userName = PythonTestUtil.uniqueName(PACKAGE_PATH, "types", "User");
        Assert.assertTrue(hasSimpleRef(field, userName));
    }

    @Test
    public void testTypeCheckingMethodAndParamResolveInternal() {
        String signature = PythonTestUtil.signature("set_user", "User", "user: User");
        String methodName = PythonTestUtil.uniqueName(PACKAGE_PATH, MODULE, "Service." + signature);
        Component method = model.getComponent(methodName).orElseThrow();
        String userName = PythonTestUtil.uniqueName(PACKAGE_PATH, "types", "User");
        Assert.assertTrue(hasSimpleRef(method, userName));

        String paramName = PythonTestUtil.uniqueName(PACKAGE_PATH, MODULE, "Service." + signature + ".user");
        Component param = model.getComponent(paramName).orElseThrow();
        Assert.assertTrue(hasSimpleRef(param, userName));
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
