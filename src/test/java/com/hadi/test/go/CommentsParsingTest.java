package com.hadi.test.go;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CommentsParsingTest extends GoTestBase {

    @Test
    public void testParsedSingleLineStructDoc() throws Exception {
        final String code = "package main\n //test struct doc\n type person struct {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles,
                Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("test struct doc", generatedSourceModel.getComponent("main.person").get().comment());
    }

    @Test
    public void testParsMultiLineStructDoc() throws Exception {
        final String code = "package main\n //test struct\n// doc\n type person struct {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("test struct doc", generatedSourceModel.getComponent("main.person").get().comment());
    }

    @Test
    public void testParseMultiLineInterfaceDoc() throws Exception {
        final String code = "package main\n //test interface\n// doc\n type person interface {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("test interface doc", generatedSourceModel.getComponent("main.person").get().comment());
    }

    @Test
    public void testGoStructMethodComment() throws Exception {
        final String code = "package main\ntype person struct {}\n\n //test \n //test\n\nfunc (p person) x() int {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("test test", generatedSourceModel.getComponent("main.person.x() : (int)").get().comment());
    }

    @Test
    public void testGoStructMethodDocComment() throws Exception {
        final String code = "package main\ntype person struct {}\n\n //test \n //test\n\nfunc (p person) x() int {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("test test", generatedSourceModel.getComponent("main.person.x() : (int)").get().comment());
    }

    @Test
    public void testParseSingleLineStructDocSeparatedByEmptyLines() throws Exception {
        final String code = "package main\n//test struct doc\n\n\n\ntype person struct {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("test struct doc", generatedSourceModel.getComponent("main.person").get().comment());
    }

    @Test
    public void testParseMultiLineStructDocAfterAnotherStruct() throws Exception {
        final String code = "package main\n type animal struct {}\n//test struct\n// doc\n type person struct {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("test struct doc", generatedSourceModel.getComponent("main.person").get().comment());
    }

    @Test
    public void testParseMultiLineStructDocSeparatedByEmptyLines() throws Exception {
        final String code = "package main\n//test struct\n// doc\n\n\ntype person struct {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("test struct doc", generatedSourceModel.getComponent("main.person").get().comment());
    }

    @Test
    public void testParseMultiLineStructDocForInterfaceMethodSpece() throws Exception {
        final String code = "package main\ntype person interface { \n//test\n testMethod() int}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("test", generatedSourceModel.getComponent("main.person.testMethod() : (int)").get().comment());
    }
}
