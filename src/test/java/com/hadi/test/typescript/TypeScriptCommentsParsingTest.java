package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TypeScriptCommentsParsingTest {

    private static final String FIXTURE = "comments-parsing";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "Comments";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    @Test
    public void testClassLevelComment() {
        assertTrue(model.getComponent(name("Test")).orElseThrow().comment().contains("Class doc"));
    }

    @Test
    public void testClassLevelNoComment() {
        assertEquals("", model.getComponent(name("NoComment")).orElseThrow().comment());
    }

    @Test
    public void testInterfaceLevelComment() {
        assertTrue(model.getComponent(name("TestInterface")).orElseThrow().comment().contains("Interface doc"));
    }

    @Test
    public void testEnumLevelComment() {
        assertTrue(model.getComponent(name("TestEnum")).orElseThrow().comment().contains("Enum doc"));
    }

    @Test
    public void testMethodLevelComment() {
        String methodName = name("Test." + TypeScriptTestUtil.signature("test", "string"));
        assertTrue(model.getComponent(methodName).orElseThrow().comment().contains("method doc"));
    }

    @Test
    public void testInterfaceMethodLevelComment() {
        String methodName = name("TestInterface." + TypeScriptTestUtil.signature("method"));
        assertTrue(model.getComponent(methodName).orElseThrow().comment().contains("interface method doc"));
    }

    @Test
    public void testFieldVarLevelComment() {
        assertTrue(model.getComponent(name("Test.fieldVar")).orElseThrow().comment().contains("field doc"));
    }

    @Test
    public void testMethodParamLevelComment() {
        String paramName = name("Test." + TypeScriptTestUtil.signature("test", "string") + ".methodParam");
        assertTrue(model.getComponent(paramName).orElseThrow().comment().contains("param doc"));
    }

    private static String name(final String symbolPath) {
        return TypeScriptTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
