package com.hadi.test.go;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GoLangParseTest extends GoTestBase {

    @Test
    public void assertNoMethodParameters() throws Exception {
        final String code = "package main\n import\"flag\"\n type Command struct {}\n func (c *Command) LocalFlags() *flag.FlagSet {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals(0, generatedSourceModel.getComponent("main.Command.LocalFlags() : (*flag.FlagSet)").get().children().size());
    }

    @Test
    public void testStructWithinMethodIgnored() throws Exception {
        final String code = "package main\n import\"fmt\"\n func SomeFunc(b []byte) error {\n" +
                "  var inside struct {\n" +
                "    Foo value`json:\"foo\"`\n" +
                "  }" +
                "}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertFalse(generatedSourceModel.getComponent("main.SomeFunc.inside").isPresent());
        assertEquals(0, generatedSourceModel.size());
    }



    @Test
    public void testParseGoStruct() throws Exception {
        final String code = "package main\n import \"fmt\"\n /*test*/ type person struct {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("main.person"));
    }

    @Test
    public void testParseGoStructs() throws Exception {
        final String code = "package main\n import \"fmt\"\n /*test*/ type person struct {} type teacher struct{}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("main.person"));
        assertTrue(generatedSourceModel.containsComponent("main.teacher"));
    }

    @Test
    public void testParseGoInterface() throws Exception {
        final String code = "package main\n import \"fmt\"\n /*test*/ type person interface {} type teacher struct{}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("main.person"));
        assertTrue(generatedSourceModel.containsComponent("main.teacher"));
    }
    @Test
    public void localVarWithoutTypeDoesNotExist() throws Exception {
        final String code = "package main \n type plain struct \n{ func (t plain) testMethodv2(x value, h int) (value, uintptr) {\n a:=\"test\"} }";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/plain.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertFalse(generatedSourceModel.getComponent("main.plain.testMethodv2.a").isPresent());
    }

    @Test
    public void localVarExists() throws Exception {
        final String code = "package main \n type plain struct \n{} \n func (t plain) testMethodv2 () {\n var i int  = 2;\n}";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/plain.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("main.plain.testMethodv2().i"));
    }

    @Test
    public void localVarName() throws Exception {
        final String code = "package main \n type plain struct \n{} \n func (t plain) testMethodv2 () {\n var i int  = 2;\n}";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/plain.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.plain.testMethodv2().i").get().name().equals("i"));
    }

    @Test
    public void localVarUniqueName() throws Exception {
        final String code = "package main \n type plain struct \n{} \n func (t plain) testMethodv2 () {\n var i int  = 2;\n}";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/plain.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.plain.testMethodv2().i").get().uniqueName()
                .equals("main.plain.testMethodv2().i"));
    }


    @Test
    public void testInterfaceMethodSpecExists() throws Exception {
        final String code = "package main\ntype person interface { \n//test\n testMethod() int}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("main.person.testMethod() : (int)"));
    }
    @Test
    public void testGoStructFieldVarExists() throws Exception {
        final String code = "package main\nimport \"test/math\"\ntype person struct {mathObj math.Person}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("main.person.mathObj"));
    }

    @Test
    public void testGoStructFieldVarComponentName() throws Exception {
        final String code = "package main\nimport \"test/math\"\ntype person struct {mathObj math.Person}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.person.mathObj").get().componentName().equals("person.mathObj"));
    }

    @Test
    public void testGoStructFieldVarName() throws Exception {
        final String code = "package main\nimport \"test/math\"\ntype person struct {mathObj math.Person}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.person.mathObj").get().name().equals("mathObj"));
    }

    @Test
    public void testGoStructSideBySideFieldVars() throws Exception {
        final String code = "package main\nimport \"test/math\"\ntype person struct {mathObj , secondObj math.Person}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("main.person.mathObj"));
        assertTrue(generatedSourceModel.containsComponent("main.person.secondObj"));
    }

    @Test
    public void testGoStructSideBySideFieldVarsInvocations() throws Exception {
        final String code = "package main\nimport \"test/math\"\ntype person struct {mathObj , secondObj math.Person}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("main.person.mathObj"));
        assertTrue(generatedSourceModel.containsComponent("main.person.secondObj"));
    }

    @Test
    public void testGoStructMethodExists() throws Exception {
        final String code = "package main\ntype person struct {} \n func (p person) x() int {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("main.person.x() : (int)"));
    }

    @Test
    public void testGoStructMethodComponentName() throws Exception {
        final String code = "package main\ntype person struct {} \n func (p person) x() int {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.person.x() : (int)").get().componentName().equals("person.x() : (int)"));
    }

    @Test
    public void testGoStructMethodName() throws Exception {
        final String code = "package main\ntype person struct {} \n func (p person) x() int {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.person.x() : (int)").get().name().equals("x"));
    }

    @Test
    public void testGoStructMethodSingleParamExists() throws Exception {
        final String code = "package main\ntype person struct {} \n func (p person) lol(x,y int) {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("main.person.lol(int, int).x"));
        assertTrue(generatedSourceModel.containsComponent("main.person.lol(int, int).y"));
    }

    @Test
    public void testGoStructMethodExistsInAnotherSourceFile() throws Exception {
        final String code = "package main\ntype Person struct {}";
        final String codeB = "package cakes\n import \"main\" \n func (p main.Person) x() int {}";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/person.go", code));
        projectFiles.insertFile(new ProjectFile("/src/com/cakes/test.go", codeB));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("main.Person.x() : (int)"));
    }

    @Test
    public void testGoStructMethodExistsInAnotherSourceFilev2() throws Exception {
        final String code = "package main\ntype Person struct {}";
        final String codeB = "package cakes\n import main \"main\" \n func (p main.Person) x() int {}";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/cherry.go", code));
        projectFiles.insertFile(new ProjectFile("/src/com/cakes/test.go", codeB));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("main.Person.x() : (int)"));
        assertTrue(generatedSourceModel.containsComponent("main.Person"));
    }

    @Test
    public void structMethodInDifferentSourceFileInSamePackage() throws Exception {
        final String code = "package main\ntype Person struct {}";
        final String codeB = "package main\n func (p *Person) x(y value) []value {}";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/test.go", codeB));
        projectFiles.insertFile(new ProjectFile("/src/main/cherry.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("main.Person.x(value) : ([]value)"));
        assertTrue(generatedSourceModel.containsComponent("main.Person.x(value) : ([]value).y"));
    }
}
