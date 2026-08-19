package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TypeScriptCodeFragmentTest {

    private static final String FIXTURE = "code-fragment";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "CodeFragment";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    @Test
    public void classGenericsCodeFragmentTest() {
        assertEquals("<T>", model.copyOfComponent(name("GenericTest")).orElseThrow().codeFragment());
    }

    @Test
    public void classGenericsCodeFragmentTestv2() {
        assertEquals("<T extends List>",
                model.copyOfComponent(name("GenericTest2")).orElseThrow().codeFragment());
    }

    @Test
    public void fieldVarCodeFragmentTest() {
        assertEquals("fieldVar : List", model.copyOfComponent(name("FieldTest.fieldVar")).orElseThrow().codeFragment());
        assertEquals("x : List", model.copyOfComponent(name("FieldTest.x")).orElseThrow().codeFragment());
    }

    @Test
    public void fieldVarCodeFragmentTestComplex() {
        assertEquals("complexField : Map<string, List>",
                model.copyOfComponent(name("FieldTest.complexField")).orElseThrow().codeFragment());
    }

    @Test
    public void simpleMethodCodeFragmentTest() {
        String methodName = name("MethodTest." + TypeScriptTestUtil.signature("sMethod"));
        assertEquals("sMethod() : Map<string, List>", model.copyOfComponent(methodName).orElseThrow().codeFragment());
    }

    @Test
    public void interfaceMethodCodeFragmentTest() {
        String methodName = name("InterfaceTest." + TypeScriptTestUtil.signature("sMethod"));
        assertEquals("sMethod() : Map<string, List>", model.copyOfComponent(methodName).orElseThrow().codeFragment());
    }

    @Test
    public void complexMethodCodeFragmentTest() {
        String methodName = name("MethodTest." + TypeScriptTestUtil.signature("complexMethod", "string", "number"));
        assertEquals("complexMethod(string, number) : Map<List, string[]>",
                model.copyOfComponent(methodName).orElseThrow().codeFragment());
    }

    @Test
    public void literalStringReturnTypeIsNormalized() {
        String methodName = name("MethodTest." + TypeScriptTestUtil.signature("literalText"));
        assertEquals("literalText() : string", model.copyOfComponent(methodName).orElseThrow().codeFragment());
    }

    @Test
    public void literalNumberReturnTypeIsNormalized() {
        String methodName = name("MethodTest." + TypeScriptTestUtil.signature("literalCount"));
        assertEquals("literalCount() : number", model.copyOfComponent(methodName).orElseThrow().codeFragment());
    }

    @Test
    public void literalUnionReturnTypeIsNormalized() {
        String methodName = name("MethodTest." + TypeScriptTestUtil.signature("literalSwitch", "boolean"));
        assertEquals("literalSwitch(boolean) : string", model.copyOfComponent(methodName).orElseThrow().codeFragment());
    }

    private static String name(final String symbolPath) {
        return TypeScriptTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
