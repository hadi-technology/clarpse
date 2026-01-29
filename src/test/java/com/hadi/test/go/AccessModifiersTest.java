package com.hadi.test.go;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class AccessModifiersTest extends GoTestBase {

    @Test
    public void testParseGoStructPrivateVisibility() throws Exception {
        final String code = "package main\n import \"fmt\"\n /*test*/ type person struct {} type Teacher struct{}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.person").get().modifiers().contains("private"));
    }

    @Test
    public void testGoStructFieldVarPrivateVisibility() throws Exception {
        final String code = "package main\nimport \"test/math\"\ntype person struct {mathObj math.Person}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.person.mathObj").get().modifiers().contains("private"));
    }

    @Test
    public void testParseGoStructPublicVisibility() throws Exception {
        final String code = "package main\n import \"fmt\"\n /*test*/ \n type person struct {} type Teacher struct{}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.Teacher").get().modifiers().contains("public"));
    }

    @Test
    public void testGoStructFieldVarPublicVisibility() throws Exception {
        final String code = "package main\nimport \"test/math\"\ntype person struct {MathObj math.Person}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.person.MathObj").get().modifiers().contains("public"));
    }

    @Test
    public void testParseGoInterfacePublicVisibility() throws Exception {
        final String code = "package main\n import \"fmt\"\n /*test*/ type Person interface {} type Teacher struct{}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.Person").get().modifiers().contains("public"));
    }

    @Test
    public void testParseGoInterfacePrivateVisibility() throws Exception {
        final String code = "package main\n import \"fmt\"\n /*test*/ type person interface {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.person").get().modifiers().contains("private"));
    }

    @Test
    public void testParsePrivateStructMethod() throws Exception {
        final String code = "package main\ntype person struct {} \n func (p person) x() int {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.person.x() : (int)").get().modifiers().contains("private"));
    }

    @Test
    public void testParsePublicStructMethod() throws Exception {
        final String code = "package main\ntype person struct {} \n func (p person) X() int {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.person.X() : (int)").get().modifiers().contains("public"));
    }
}
