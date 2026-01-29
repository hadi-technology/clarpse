package com.hadi.test.java;


import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
/**
 * Ensure components are displaying the correct associated source ProjectFile path.
 */
public class ComponentProjectFilePathTest {

    private static final String SOURCEFILE1PACKAGE = "com.foo.test";
    private static final String SOURCEFILE1NAME = "/com/foo/test/SourceFile1.java";
    private static final String SOURCEFILE1CODESTRING = "package " + SOURCEFILE1PACKAGE + ";"
            + "import java.lang.String; "
            + "public class TestA { "
            + "public void methodA () { } "
            + "}"
            + " public abstract class TestB {"
            + " private String methodB();"
            + " }";

    private static final String SOURCEFILE2PACKAGE = "com.foo.test.lol";
    private static final String SOURCEFILE2NAME = "/com/foo/test/SourceFile2.java";
    private static final String SOURCEFILE2CODESTRING = "package " + SOURCEFILE2PACKAGE + "; "
            + "import java.lang.String; "
            + "public class TestC { "
            + "public void methodC () { } "
            + " public abstract class TestD {"
            + " private String methodD();"
            + " }"
            + "}";

    private static OOPSourceCodeModel sourceCodeModel;

    @BeforeClass
    public static void setup() throws Exception {
        final ProjectFile file1 = new ProjectFile(SOURCEFILE1NAME, SOURCEFILE1CODESTRING);
        final ProjectFile file2 = new ProjectFile(SOURCEFILE2NAME, SOURCEFILE2CODESTRING);
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(file1);
        rawData.insertFile(file2);
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        sourceCodeModel = parseService.result().model();
    }

    @Test
    public void testClassAComponentHasCorrectSourceFilePath() {
        final Component component = sourceCodeModel.getComponent(SOURCEFILE1PACKAGE + ".TestA").get();
        assertEquals(SOURCEFILE1NAME, component.sourceFile());
    }

    @Test
    public void testClassAMethodAComponentHasCorrectSourceFilePath() {
        final Component component = sourceCodeModel.getComponent(SOURCEFILE1PACKAGE + ".TestA.methodA()").get();
        assertEquals(SOURCEFILE1NAME, component.sourceFile());
    }

    @Test
    public void testAbstractClassBComponentHasCorrectSourceFilePath() {
        final Component component = sourceCodeModel.getComponent(SOURCEFILE1PACKAGE + ".TestB").get();
        assertEquals(SOURCEFILE1NAME, component.sourceFile());
    }

    @Test
    public void testAbstractClassBMethodBComponentHasCorrectSourceFilePath() {
        final Component component = sourceCodeModel.getComponent(SOURCEFILE1PACKAGE
                + ".TestB.methodB()").get();
        assertEquals(SOURCEFILE1NAME, component.sourceFile());
    }

    @Test
    public void testClassCComponentHasCorrectSourceFilePath() {
        final Component component = sourceCodeModel.getComponent(SOURCEFILE2PACKAGE + ".TestC").get();
        assertEquals(SOURCEFILE2NAME, component.sourceFile());
    }

    @Test
    public void testClassCMethodCComponentHasCorrectSourceFilePath() {
        final Component component = sourceCodeModel.getComponent(SOURCEFILE2PACKAGE + ".TestC.methodC()").get();
        assertEquals(SOURCEFILE2NAME, component.sourceFile());
    }

    @Test
    public void testClassCAbstractClassDComponentHasCorrectSourceFilePath() {
        final Component component = sourceCodeModel.getComponent(SOURCEFILE2PACKAGE + ".TestC.TestD").get();
        assertEquals(SOURCEFILE2NAME, component.sourceFile());
    }

    @Test
    public void testClassCAbstractClassDMethodDComponentHasCorrectSourceFilePath() {
        final Component component = sourceCodeModel.getComponent(SOURCEFILE2PACKAGE
                + ".TestC.TestD.methodD()").get();
        assertEquals(SOURCEFILE2NAME, component.sourceFile());
    }
}
