package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonMethodComponentsTest {

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
    public void testFieldComponentTypeAndReference() {
        String fieldName = PythonTestUtil.uniqueName(PACKAGE_PATH, MODULE, "Service.owner");
        Component field = model.copyOfComponent(fieldName).orElseThrow();
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.FIELD, field.componentType());
        Assert.assertTrue(hasSimpleRef(field, userTypeName()));
    }

    @Test
    public void testConstructorComponentTypeAndParamReference() {
        String ctorSignature = PythonTestUtil.signature("__init__", "None", "user: User");
        String ctorName = PythonTestUtil.uniqueName(PACKAGE_PATH, MODULE, "Service." + ctorSignature);
        Component ctor = model.copyOfComponent(ctorName).orElseThrow();
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.CONSTRUCTOR, ctor.componentType());

        String paramName = PythonTestUtil.uniqueName(PACKAGE_PATH, MODULE, "Service." + ctorSignature + ".user");
        Component param = model.copyOfComponent(paramName).orElseThrow();
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.CONSTRUCTOR_PARAMETER_COMPONENT,
                param.componentType());
        Assert.assertTrue(hasSimpleRef(param, userTypeName()));
    }

    @Test
    public void testMethodComponentTypeAndParamReference() {
        String methodSignature = PythonTestUtil.signature("update", "User", "user: User");
        String methodName = PythonTestUtil.uniqueName(PACKAGE_PATH, MODULE, "Service." + methodSignature);
        Component method = model.copyOfComponent(methodName).orElseThrow();
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.METHOD, method.componentType());
        Assert.assertTrue(hasSimpleRef(method, userTypeName()));

        String paramName = PythonTestUtil.uniqueName(PACKAGE_PATH, MODULE, "Service." + methodSignature + ".user");
        Component param = model.copyOfComponent(paramName).orElseThrow();
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.METHOD_PARAMETER_COMPONENT, param.componentType());
        Assert.assertTrue(hasSimpleRef(param, userTypeName()));
    }

    private static String userTypeName() {
        return PythonTestUtil.uniqueName(PACKAGE_PATH, "types", "User");
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
