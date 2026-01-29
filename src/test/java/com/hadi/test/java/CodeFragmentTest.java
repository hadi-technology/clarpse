package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CodeFragmentTest {

    @Test
    public void classGenericsCodeFragmentTest() throws Exception {
        final String code = "class Test<List> {}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("<List>", generatedSourceModel.getComponent("Test").get().codeFragment());
    }

    @Test
    public void classGenericsCodeFragmentTestv2() throws Exception {
        final String code = "class Test<T extends List> {}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("<T extends List>",
                     generatedSourceModel.getComponent("Test").get().codeFragment());
    }

    @Test
    public void fieldVarCodeFragmentTest() throws Exception {
        final String code = "import java.util.Map;import java.util.List; \n  class Test {List<Integer> fieldVar, x;}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("fieldVar : List<Integer>", generatedSourceModel.getComponent("Test.fieldVar"
        ).get().codeFragment());
        assertEquals("x : List<Integer>",
                     generatedSourceModel.getComponent("Test.x").get().codeFragment());
    }

    @Test
    public void fieldVarCodeFragmentTestComplex() throws Exception {
        final String code = "import java.util.Map;import java.util.List; \n class Test {Map<String, List<String>> fieldVar, x;}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("fieldVar : Map<String, List<String>>", generatedSourceModel.getComponent(
            "Test.fieldVar").get().codeFragment());
        assertEquals("x : Map<String, List<String>>",
                     generatedSourceModel.getComponent("Test.x").get().codeFragment());
    }

    @Test
    public void simpleMethodCodeFragmentTest() throws Exception {
        final String code = "import java.util.Map;import java.util.List; \n  class Test {Map<String, List<Integer>> sMethod() {}}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("sMethod() : Map<String, List<Integer>>", generatedSourceModel.getComponent(
            "Test.sMethod()").get().codeFragment());
    }

    @Test
    public void interfaceMethodCodeFragmentTest() throws Exception {
        final String code = "import java.util.Map;import java.util.List; \n  interface Test { Map<String, List<Integer>> sMethod();}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("sMethod() : Map<String, List<Integer>>", generatedSourceModel.getComponent(
            "Test.sMethod()").get().codeFragment());
    }

    @Test
    public void complexMethodCodeFragmentTest() throws Exception {
        final String code = "import java.util.Map;import java.util.List; \n  class Test {Map<List<String>, String[]> sMethod(String s, int t) {}}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        System.out.println(generatedSourceModel.getComponent("Test.sMethod(String, int)").get().codeFragment());
        assertEquals("sMethod(String, int) : Map<List<String>, String[]>",
                     generatedSourceModel.getComponent("Test.sMethod(String, int)").get().codeFragment());
    }
}
