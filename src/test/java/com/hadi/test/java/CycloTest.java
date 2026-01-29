package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.Component;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

/**
 * Tests accuracy of Java Component cyclomatic complexity attribute. See {@link Component}.
 */
public class CycloTest {

    @Test
    public void simpleCycloTest() throws Exception {
        final String code = "public class Test {\n" +
                "    Test() {\n" +
                "        if (2 > 4 || (5 < 7 && 5 < 7)) {\n" +
                "            return true;\n" +
                "        } else {\n" +
                "            for (String s : t) {\n" +
                "                throw new Exception();\n" +
                "                return false;\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals(6, generatedSourceModel.getComponent("Test.Test()").get().cyclo());
    }


    @Test
    public void switchStmtCycloTest() throws Exception {
        final String code = "public class Test {\n" +
                "    public Test() {\n" +
                "        switch (s) {\n" +
                "            case \"a\": System.out.println(); break;\n" +
                "            case \"b\": System.out.println(); break;\n" +
                "            default: System.out.println(); break; " +
                "        } \n" +
                "    }\n" +
                "}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals(3, generatedSourceModel.getComponent("Test.Test()").get().cyclo());
    }

    @Test
    public void complexCycloTest() throws Exception {
        final String code = "public class test {\n" +
                "    boolean aMethod() {\n" +
                "        while (2 > 4) {\n" +
                "            for (int i = 0; i < 3 && 2 == 3; i++) {\n" +
                "                if (i = 3); \n" +
                "                   try {return false; } catch (Exception e) {}\n" +
                "            }\n" +
                "        }\n" +
                "        return true;" +
                "    }\n" +
                "}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals(6, generatedSourceModel.getComponent("test.aMethod()").get().cyclo());
    }

    @Test
    public void ignoreOperatorsInComments() throws Exception {
        final String code = "public class test {\n" +
                "    /** test comment  && || */\n" +
                "    boolean aMethod() {\n" +
                "        while (2 > 4) {\n" +
                "        }\n" +
                "        return true;" +
                "    }\n" +
                "}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals(2, generatedSourceModel.getComponent("test.aMethod()").get().cyclo());
    }


    @Test
    public void ignoreInterfaceMethods() throws Exception {
        final String code = "public interface test {\n" +
                "    boolean aMethod();\n" +
                "}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals(0, generatedSourceModel.getComponent("test.aMethod()").get().cyclo());
    }


    @Test
    public void classCycloTest() throws Exception {
        final String code = "public class test {\n" +
                "    public String tester = \"test\";       \n" +
                "    boolean aMethod() {\n" +
                "        while (2 > 4) {\n" +
                "            for (int i = 0; i < 3 && 2 == 3; i++) {\n" +
                "                if (i = 3); \n" +
                "                   try {return false; } catch (Exception e) {}\n" +
                "            }\n" +
                "        }\n" +
                "        return true;" +
                "    }\n" +
                "    boolean bMethod() {\n" +
                "        while (2 > 4) {\n" +
                "            for (int i = 0; i < 3 && 2 == 3; i++) {\n" +
                "            }\n" +
                "        }\n" +
                "        return true;" +
                "    }\n" +
                "}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals(5, generatedSourceModel.getComponent("test").get().cyclo());
    }

    @Test
    public void emptyClassCycloTest() throws Exception {
        final String code = "public class test {\n" +
                "    public String tester = \"test\";       \n" +
                "}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals(0, generatedSourceModel.getComponent("test").get().cyclo());
    }
}
