package com.hadi.test.go;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Test;

import java.util.ArrayList;

import static junit.framework.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ChildComponentsTest extends GoTestBase {

    @Test
    public void testGoStructMethodSingleParamComponentIsChildOfMethod() throws Exception {
        final String code = "package main\ntype person struct {} \n func (p person) lol(x int) {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("main.person.lol(int).x", new ArrayList<>(generatedSourceModel.getComponent(
            "main.person.lol(int)").get().children()).get(0));
    }


    @Test
    public void noGoFilesParsedTest() throws Exception {
        projectFiles = new ProjectFiles();
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals(0, generatedSourceModel.components().count());
    }

    @Test
    public void emptyGoModuleTest() throws Exception {
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals(0, generatedSourceModel.components().count());
    }

    @Test
    public void testInterfaceAnonymousTypeMethodParamsIsChildOfMethod() throws Exception {
        final String code = "package main \n type plain interface \n{ testMethodv2(x value, h int) (value, uintptr) {} }";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/plain.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals(2, generatedSourceModel.getComponent("main.plain.testMethodv2(value, int) : " +
                                                              "(value, uintptr)")
                                            .get().children().size());
        assertSame(generatedSourceModel.getComponent("main.plain.testMethodv2(value, int) : " +
                                                         "(value, uintptr).x")
                                       .get().componentType(),
                   OOPSourceModelConstants.ComponentType.METHOD_PARAMETER_COMPONENT);
    }

    @Test
    public void testGoStructMethodIsChildofStruct() throws Exception {
        final String code = "package main\ntype person struct {} \n func (p person) x() int {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.person").get().children().contains("main.person.x() : (int)"));
    }

    @Test
    public void testParseGoStructMethodWithUnnamedParameters() throws Exception {
        final String code = "package main\ntype person struct {} \n func (p person) x(string) {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals(1,
                     generatedSourceModel.getComponent("main.person.x(string)").get().children().size());
    }

    @Test
    public void testParseGoStructMethodWithEmptyReturnParenthesis() throws Exception {
        final String code = "package main\ntype person struct {} \n func (p person) x() () {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("main.person.x() : ()"));
    }

    @Test
    public void testParseGoStructWithMultipleFields() throws Exception {
        final String code = "package main\n type accountFile struct {\n" +
                "        PrivateKeyId string `json:\"private_key_id\"`\n" +
                "        PrivateKey   string `json:\"private_key\"`\n" +
                "        ClientEmail  string `json:\"client_email\"`\n" +
                "        ClientId     string `json:\"client_id\"`";
        projectFiles.insertFile(new ProjectFile("/accountFile.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals(4,
                     generatedSourceModel.getComponent("main.accountFile").get().children().size());
    }


    @Test
    public void testGoStructFIeldVarIsChildOfStruct() throws Exception {
        final String code = "package main\nimport \"test/math\"\ntype person struct {mathObj math.Person}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("main.person.mathObj", new ArrayList<>(generatedSourceModel.getComponent(
            "main.person").get().children())
            .get(0));
    }

    @Test
    public void testGoStructLocalVarIsChildOfMethod() throws Exception {
        final String code = "package main\ntype person struct {} \n func (p person) x() {\nvar b, c int = 1, 2\n}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals(2,
                     generatedSourceModel.getComponent("main.person.x()").get().children().size());
        assertTrue(generatedSourceModel.getComponent("main.person.x()").get().children().contains(
                "main.person.x().b"));
        assertTrue(generatedSourceModel.getComponent("main.person.x()").get().children().contains(
                "main.person.x().c"));
    }

    @Test
    public void testInterfaceMethodSpecComponentIsChildOfParentInterface() throws Exception {
        final String code = "package main\ntype person interface { \n//test\n testMethod() int}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("main.person.testMethod() : (int)",
                     new ArrayList<>(generatedSourceModel.getComponent("main.person").get().children())
            .get(0));
    }
}
