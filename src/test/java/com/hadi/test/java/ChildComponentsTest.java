package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Test;

import java.util.Arrays;

import static junit.framework.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ChildComponentsTest {

    @Test
    public void testClassHasMethodChild() throws Exception {
        final String code = "class Test { void method(){} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("Test.method()",
                     generatedSourceModel.getComponent("Test").get().children().toArray()[0]);
    }

    @Test
    public void testClassHasFieldVarChild() throws Exception {
        final String code = "class Test { String fieldVar; }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("Test.fieldVar",
                     generatedSourceModel.getComponent("Test").get().children().toArray()[0]);
    }

    @Test
    public void ignoreClassDeclaredWithinMethods() throws Exception {
        final String code = "class Test { void method() { class Tester {} } }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertFalse(generatedSourceModel.getComponent("Test.method().Tester").isPresent());
        assertEquals(2, generatedSourceModel.size());
    }

    @Test
    public void testIntefaceHasMethodChild() throws Exception {
        final String code = "interface Test { void method(); }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("Test.method()",
                     generatedSourceModel.getComponent("Test").get().children().toArray()[0]);
    }

    @Test
    public void testMethodHasMethodParamChild() throws Exception {
        final String code = "class Test { void method(String str); }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("Test.method(String).str", generatedSourceModel.getComponent("Test.method" +
                                                                                      "(String)").get().children().toArray()[0]);
    }

    @Test
    public void testInterfaceHasConstantFieldChild() throws Exception {
        final String code = "interface Test { String NEAR_TO_QUERY; }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("Test.NEAR_TO_QUERY",
                     generatedSourceModel.getComponent("Test").get().children().toArray()[0]);
    }

    @Test
    public void testClassHasNestedIntefaceChild() throws Exception {
        final String code = "class TestA { interface TestB { }}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("TestA.TestB",
                     generatedSourceModel.getComponent("TestA").get().children().toArray()[0]);
    }

    @Test
    public void testClassHasNestedEnumChild() throws Exception {
        final String code = "class TestA { enum TestB { }}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("TestA.TestB",
                     generatedSourceModel.getComponent("TestA").get().children().toArray()[0]);
    }

    @Test
    public void testEnumHasNestedConstantsChild() throws Exception {
        final String code = " enum TestA { A,B,C; }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("TestA").get().children().contains("TestA.A"));
        assertTrue(generatedSourceModel.getComponent("TestA").get().children().contains("TestA.B"));
        assertTrue(generatedSourceModel.getComponent("TestA").get().children().contains("TestA.C"));
    }

    @Test
    public void testFieldVarWildCardImportParent() throws Exception {
        final String codeA = "package com; \n import org.*; \n class Test { ClassB fieldVar; }";
        final String codeB = "package org; \n class ClassB { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/src/main/com/Test.java", codeA));
        rawData.insertFile(new ProjectFile("/src/main/org/ClassB.java", codeB));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("com.Test", generatedSourceModel.getComponent("com.Test.fieldVar")
                                                     .get().parentUniqueName());
    }

    @Test
    public void testClassWithMultipleChildren() throws Exception {
        final String code = " class TestA { String fieldVar; String method(){} interface TestB {}}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(Arrays.asList(generatedSourceModel.getComponent("TestA").get().children()
                                                     .toArray()).contains("TestA.fieldVar"));
        assertTrue(Arrays.asList(generatedSourceModel.getComponent("TestA").get().children()
                                                     .toArray()).contains("TestA.method()"));
        assertTrue(Arrays.asList(generatedSourceModel.getComponent("TestA").get().children()
                                                     .toArray()).contains("TestA.TestB"));
    }
}
