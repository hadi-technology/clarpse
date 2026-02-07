package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class TypeScriptReferenceInheritanceTest {

    private static final String FIXTURE = "reference-inheritance";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "ReferenceInheritance";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    @Test
    public void testClassInheritsFieldReferences() {
        assertTrue(model.getComponent(name("Test")).orElseThrow().references().contains(new SimpleTypeReference("string")));
    }

    @Test
    public void testClassInheritsMethodReferences() {
        assertTrue(model.getComponent(name("Test")).orElseThrow().references().contains(new SimpleTypeReference("string")));
    }

    @Test
    public void testClassInheritsLocalVarsReferences() {
        assertTrue(model.getComponent(name("Test")).orElseThrow().references().contains(new SimpleTypeReference("string")));
    }

    @Test
    public void testClassInheritsMethodParamsReferences() {
        assertTrue(model.getComponent(name("Test")).orElseThrow().references().contains(new SimpleTypeReference("string")));
    }

    @Test
    public void testInterfaceInheritsFieldReferences() {
        assertTrue(model.getComponent(name("ITest")).orElseThrow().references().contains(new SimpleTypeReference("string")));
    }

    @Test
    public void testInterfaceInheritsMethodReferences() {
        assertTrue(model.getComponent(name("ITest")).orElseThrow().references().contains(new SimpleTypeReference("string")));
    }

    @Test
    public void testInterfaceInheritsMethodParamsReferences() {
        assertTrue(model.getComponent(name("ITest")).orElseThrow().references().contains(new SimpleTypeReference("string")));
    }

    @Test
    public void testMethodInheritsLocalVarsReferences() {
        String methodName = name("Test." + TypeScriptTestUtil.signature("methodWithLocal"));
        assertTrue(model.getComponent(methodName).orElseThrow().references().contains(new SimpleTypeReference("string")));
    }

    @Test
    public void testMethodInheritsMethodParamsReferences() {
        String methodName = name("Test." + TypeScriptTestUtil.signature("methodWithParam", "string"));
        assertTrue(model.getComponent(methodName).orElseThrow().references().contains(new SimpleTypeReference("string")));
    }

    private static String name(final String symbolPath) {
        return TypeScriptTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
