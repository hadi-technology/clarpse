package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TypeScriptCycloTest {

    private static final String FIXTURE = "cyclo";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "Cyclo";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    @Test
    public void simpleCycloTest() {
        String ctorName = name("Test." + TypeScriptTestUtil.signature("constructor"));
        assertEquals(6, model.getComponent(ctorName).orElseThrow().cyclo());
    }

    @Test
    public void switchStmtCycloTest() {
        String methodName = name("Test." + TypeScriptTestUtil.signature("switcher", "string"));
        assertEquals(3, model.getComponent(methodName).orElseThrow().cyclo());
    }

    @Test
    public void complexCycloTest() {
        String methodName = name("Test." + TypeScriptTestUtil.signature("complex"));
        assertEquals(6, model.getComponent(methodName).orElseThrow().cyclo());
    }

    @Test
    public void ignoreOperatorsInComments() {
        String methodName = name("Test." + TypeScriptTestUtil.signature("withComment"));
        assertEquals(2, model.getComponent(methodName).orElseThrow().cyclo());
    }

    @Test
    public void ignoreInterfaceMethods() {
        String methodName = name("ITest." + TypeScriptTestUtil.signature("aMethod"));
        assertEquals(0, model.getComponent(methodName).orElseThrow().cyclo());
    }

    @Test
    public void classCycloTest() {
        assertEquals(5, model.getComponent(name("ClassCyclo")).orElseThrow().cyclo());
    }

    @Test
    public void emptyClassCycloTest() {
        assertEquals(0, model.getComponent(name("EmptyClass")).orElseThrow().cyclo());
    }

    private static String name(final String symbolPath) {
        return TypeScriptTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
