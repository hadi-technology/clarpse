package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TypeScriptAccessModifiersTest {

    private static final String FIXTURE = "access-modifiers";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "AccessModifiers";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    @Test
    public void testClassLevelModifier() {
        assertTrue(model.getComponent(name("Test")).orElseThrow().modifiers().contains("export"));
    }

    @Test
    public void testInterfaceLevelModifier() {
        assertTrue(model.getComponent(name("TestInterface")).orElseThrow().modifiers().contains("export"));
    }

    @Test
    public void testEnumLevelModifier() {
        assertTrue(model.getComponent(name("TestEnum")).orElseThrow().modifiers().contains("export"));
    }

    @Test
    public void testClassMethodLevelModifier() {
        String methodName = name("Tester." + TypeScriptTestUtil.signature("lolcakes"));
        assertTrue(model.getComponent(methodName).orElseThrow().modifiers().contains("private"));
        assertTrue(model.getComponent(methodName).orElseThrow().modifiers().contains("static"));
    }

    @Test
    public void testClassConstructorLevelModifier() {
        String ctorName = name("PrivateCtor." + TypeScriptTestUtil.signature("constructor"));
        assertTrue(model.getComponent(ctorName).orElseThrow().modifiers().contains("private"));
    }

    @Test
    public void testAbstractMethodLevelModifier() {
        String methodName = name("AbstractTester." + TypeScriptTestUtil.signature("doWork"));
        assertTrue(model.getComponent(methodName).orElseThrow().modifiers().contains("abstract"));
    }

    @Test
    public void testFieldVarLevelModifier() {
        String fieldName = name("Tester.fieldVar");
        assertTrue(model.getComponent(fieldName).orElseThrow().modifiers().contains("public"));
        assertTrue(model.getComponent(fieldName).orElseThrow().modifiers().contains("static"));
    }

    @Test
    public void testMethodParamLevelModifier() {
        String paramName = name("ParamTest." + TypeScriptTestUtil.signature("constructor", "string") + ".str");
        assertTrue(model.getComponent(paramName).orElseThrow().modifiers().contains("public"));
        assertTrue(model.getComponent(paramName).orElseThrow().modifiers().contains("readonly"));
    }

    @Test
    public void testMethodLocalVarLevelModifier() {
        String localName = name("Tester." + TypeScriptTestUtil.signature("method") + ".localConst");
        assertEquals(1, model.getComponent(localName).orElseThrow().modifiers().size());
        assertTrue(model.getComponent(localName).orElseThrow().modifiers().contains("const"));
    }

    @Test
    public void testMethodLocalVarLevelNoModifier() {
        String localName = name("Tester." + TypeScriptTestUtil.signature("method") + ".localVar");
        assertTrue(model.getComponent(localName).orElseThrow().modifiers().isEmpty());
    }

    private static String name(final String symbolPath) {
        return TypeScriptTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
