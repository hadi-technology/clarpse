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

public class PythonComponentTypeTest {

    private static final String FIXTURE = "component-types";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "sample";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void testClassComponentType() {
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.CLASS,
                model.getComponent(name("Service")).orElseThrow().componentType());
    }

    @Test
    public void testFieldComponentType() {
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.FIELD,
                model.getComponent(name("Service.owner")).orElseThrow().componentType());
    }

    @Test
    public void testConstructorComponentType() {
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.CONSTRUCTOR,
                model.getComponent(name(ctorSignaturePath())).orElseThrow().componentType());
    }

    @Test
    public void testConstructorParamComponentType() {
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.CONSTRUCTOR_PARAMETER_COMPONENT,
                model.getComponent(name(ctorSignaturePath() + ".user")).orElseThrow().componentType());
    }

    @Test
    public void testMethodComponentType() {
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.METHOD,
                model.getComponent(name(methodSignaturePath())).orElseThrow().componentType());
    }

    @Test
    public void testMethodParamComponentType() {
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.METHOD_PARAMETER_COMPONENT,
                model.getComponent(name(methodSignaturePath() + ".user")).orElseThrow().componentType());
    }

    @Test
    public void testFunctionComponentType() {
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.FUNCTION,
                model.getComponent(name(functionSignature())).orElseThrow().componentType());
    }

    @Test
    public void testFunctionParamComponentType() {
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.METHOD_PARAMETER_COMPONENT,
                model.getComponent(name(functionSignature() + ".user")).orElseThrow().componentType());
    }

    @Test
    public void testModuleFieldComponentType() {
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.MODULE_FIELD,
                model.getComponent(name("DEFAULT_USER")).orElseThrow().componentType());
    }

    @Test
    public void testFunctionReturnReferenceResolvesInternal() {
        Component function = model.getComponent(name(functionSignature())).orElseThrow();
        Assert.assertTrue(hasSimpleRef(function, userTypeName()));
    }

    @Test
    public void testModuleFieldReferenceResolvesInternal() {
        Component field = model.getComponent(name("DEFAULT_USER")).orElseThrow();
        Assert.assertTrue(hasSimpleRef(field, userTypeName()));
    }

    private static String name(final String symbolPath) {
        return PythonTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }

    private static String userTypeName() {
        return PythonTestUtil.uniqueName(PACKAGE_PATH, "types", "User");
    }

    private static String ctorSignaturePath() {
        return "Service." + PythonTestUtil.signature("__init__", "None", "user: User");
    }

    private static String methodSignaturePath() {
        return "Service." + PythonTestUtil.signature("update", "User", "user: User");
    }

    private static String functionSignature() {
        return PythonTestUtil.signature("build", "User", "user: User");
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
