package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CommentsParsingTest {

    @Test
    public void testClassLevelComment() throws Exception {
        final String code = "package test; /** Licensing */ import lol; /**\n*A comment \n */ public class Test { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("/**\n" +
                " * A comment\n" +
                " */\n", generatedSourceModel.getComponent("test.Test").get().comment());
    }

    @Test
    public void testClassLevelNoComment() throws Exception {
        final String code = "package test; import lol; public class Test { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("", generatedSourceModel.getComponent("test.Test").get().comment());
    }

    @Test
    public void testInterfaceLevelComment() throws Exception {
        final String code = "package test;  import lol; /**A \n comment*/ public class Test { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("/**\n" +
                " * A\n" +
                " *  comment\n" +
                " */\n", generatedSourceModel.getComponent("test.Test").get().comment());
    }

    @Test
    public void testEnumLevelComment() throws Exception {
        final String code = "package test;  import lol; /**A \n comment*/ public enum Test { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("/**\n" +
                " * A\n" +
                " *  comment\n" +
                " */\n", generatedSourceModel.getComponent("test.Test").get().comment());
    }

    @Test
    public void testNestedClassLevelComment() throws Exception {
        final String code = "package test; /** Licensing */ import lol; public class Test { /**A \n comment*/  class Base{} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("/**\n" +
                " * A\n" +
                " *  comment\n" +
                " */\n", generatedSourceModel.getComponent("test.Test.Base").get().comment());
    }

    @Test
    public void testMethodLevelComment() throws Exception {
        final String code = "public class Test { String fieldVar;\n /**\nlolcakes\n*/\n void test() {} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("/**\n" +
                " * lolcakes\n" +
                " */\n", generatedSourceModel.getComponent("Test.test()").get().comment());
    }

    @Test
    public void testInterfaceMethodLevelComment() throws Exception {
        final String code = "public interface Test { /**lol cakes */ void test();}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("/**\n" +
                " * lol cakes\n" +
                " */\n", generatedSourceModel.getComponent("Test.test()").get().comment());
    }

    @Test
    public void testFieldVarLevelComment() throws Exception {
        final String code = "/*lolcakesv2*/ public class Test { /**lolcakes*/ String fieldVar;}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("/**\n" +
                " * lolcakes\n" +
                " */\n", generatedSourceModel.getComponent("Test.fieldVar").get().comment());
    }

    @Test
    public void testMethodParamLevelComment() throws Exception {
        final String code = "public class Test { void aMethod(/**lolcakes*/ String methodParam){}}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("/**\n" +
                " * lolcakes\n" +
                " */\n", generatedSourceModel.getComponent("Test.aMethod(String).methodParam").get().comment());
    }
}
