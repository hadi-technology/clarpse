package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class TypeScriptComponentProjectFilePathTest {

    private static final String FIXTURE = "component-project-file-path";
    private static final String PACKAGE_A = "src/com/foo";
    private static final String PACKAGE_B = "src/com/foo/lol";
    private static final String MODULE_A = "TestA";
    private static final String MODULE_C = "TestC";
    private static OOPSourceCodeModel model;
    private static Path fixtureRoot;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
        fixtureRoot = TypeScriptTestUtil.fixturePath(FIXTURE);
    }

    @Test
    public void testClassAComponentHasCorrectSourceFilePath() {
        Component component = model.getComponent(name(PACKAGE_A, MODULE_A, "TestA")).orElseThrow();
        assertEquals(filePath("src/com/foo/TestA.ts"), component.sourceFile());
    }

    @Test
    public void testClassAMethodAComponentHasCorrectSourceFilePath() {
        String symbol = "TestA." + TypeScriptTestUtil.signature("methodA");
        Component component = model.getComponent(name(PACKAGE_A, MODULE_A, symbol)).orElseThrow();
        assertEquals(filePath("src/com/foo/TestA.ts"), component.sourceFile());
    }

    @Test
    public void testAbstractClassBComponentHasCorrectSourceFilePath() {
        Component component = model.getComponent(name(PACKAGE_A, MODULE_A, "TestB")).orElseThrow();
        assertEquals(filePath("src/com/foo/TestA.ts"), component.sourceFile());
    }

    @Test
    public void testAbstractClassBMethodBComponentHasCorrectSourceFilePath() {
        String symbol = "TestB." + TypeScriptTestUtil.signature("methodB");
        Component component = model.getComponent(name(PACKAGE_A, MODULE_A, symbol)).orElseThrow();
        assertEquals(filePath("src/com/foo/TestA.ts"), component.sourceFile());
    }

    @Test
    public void testClassCComponentHasCorrectSourceFilePath() {
        Component component = model.getComponent(name(PACKAGE_B, MODULE_C, "TestC")).orElseThrow();
        assertEquals(filePath("src/com/foo/lol/TestC.ts"), component.sourceFile());
    }

    @Test
    public void testClassCMethodCComponentHasCorrectSourceFilePath() {
        String symbol = "TestC." + TypeScriptTestUtil.signature("methodC");
        Component component = model.getComponent(name(PACKAGE_B, MODULE_C, symbol)).orElseThrow();
        assertEquals(filePath("src/com/foo/lol/TestC.ts"), component.sourceFile());
    }

    @Test
    public void testClassDComponentHasCorrectSourceFilePath() {
        Component component = model.getComponent(name(PACKAGE_B, MODULE_C, "TestD")).orElseThrow();
        assertEquals(filePath("src/com/foo/lol/TestC.ts"), component.sourceFile());
    }

    @Test
    public void testClassDMethodDComponentHasCorrectSourceFilePath() {
        String symbol = "TestD." + TypeScriptTestUtil.signature("methodD");
        Component component = model.getComponent(name(PACKAGE_B, MODULE_C, symbol)).orElseThrow();
        assertEquals(filePath("src/com/foo/lol/TestC.ts"), component.sourceFile());
    }

    private static String name(final String packagePath, final String moduleName, final String symbolPath) {
        return TypeScriptTestUtil.uniqueName(packagePath, moduleName, symbolPath);
    }

    private static String filePath(final String relativePath) {
        return fixtureRoot.resolve(relativePath).toAbsolutePath().toString();
    }
}
