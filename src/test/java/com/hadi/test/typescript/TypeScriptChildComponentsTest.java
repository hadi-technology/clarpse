package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TypeScriptChildComponentsTest {

    private static final String FIXTURE = "child-components";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "ChildComponents";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    @Test
    public void testClassHasMethodChild() {
        String className = name("Test");
        String methodName = name("Test." + TypeScriptTestUtil.signature("method", "string"));
        Component parent = model.getComponent(className).orElseThrow();
        assertTrue(parent.children().contains(methodName));
    }

    @Test
    public void testClassHasFieldVarChild() {
        String className = name("Test");
        String fieldName = name("Test.fieldVar");
        Component parent = model.getComponent(className).orElseThrow();
        assertTrue(parent.children().contains(fieldName));
    }

    @Test
    public void ignoreClassDeclaredWithinMethods() {
        String nested = name("Test." + TypeScriptTestUtil.signature("methodWithLocal") + ".LocalClass");
        assertFalse(model.getComponent(nested).isPresent());
    }

    @Test
    public void testInterfaceHasMethodChild() {
        String ifaceName = name("TestInterface");
        String methodName = name("TestInterface." + TypeScriptTestUtil.signature("method"));
        Component parent = model.getComponent(ifaceName).orElseThrow();
        assertTrue(parent.children().contains(methodName));
    }

    @Test
    public void testMethodHasMethodParamChild() {
        String methodName = name("Test." + TypeScriptTestUtil.signature("method", "string"));
        Component method = model.getComponent(methodName).orElseThrow();
        assertTrue(method.children().contains(methodName + ".str"));
    }

    @Test
    public void testInterfaceHasFieldChild() {
        String ifaceName = name("TestInterface");
        String fieldName = name("TestInterface.fieldVar");
        Component parent = model.getComponent(ifaceName).orElseThrow();
        assertTrue(parent.children().contains(fieldName));
    }

    @Test
    public void testClassHasGetterChild() {
        String className = name("Test");
        String getterName = name("Test." + TypeScriptTestUtil.signature("value"));
        assertTrue(model.getComponent(className).orElseThrow().children().contains(getterName));
    }

    @Test
    public void testClassHasSetterChild() {
        String className = name("Test");
        String setterName = name("Test." + TypeScriptTestUtil.signature("value", "number"));
        assertTrue(model.getComponent(className).orElseThrow().children().contains(setterName));
    }

    @Test
    public void testEnumHasNestedConstantsChild() {
        String enumName = name("TestEnum");
        Component parent = model.getComponent(enumName).orElseThrow();
        assertTrue(parent.children().contains(name("TestEnum.A")));
        assertTrue(parent.children().contains(name("TestEnum.B")));
        assertTrue(parent.children().contains(name("TestEnum.C")));
    }

    @Test
    public void testFieldVarParent() {
        String fieldName = name("Test.fieldVar");
        assertEquals(name("Test"), model.getComponent(fieldName).orElseThrow().parentUniqueName());
    }

    @Test
    public void testClassWithMultipleChildren() {
        Component parent = model.getComponent(name("Test")).orElseThrow();
        assertTrue(parent.children().contains(name("Test.fieldVar")));
        assertTrue(parent.children().contains(name("Test." + TypeScriptTestUtil.signature("method", "string"))));
        assertTrue(parent.children().contains(name("Test." + TypeScriptTestUtil.signature("value"))));
    }

    private static String name(final String symbolPath) {
        return TypeScriptTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
