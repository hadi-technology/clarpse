package com.hadi.test.go;


import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Ensure components are displaying the correct associated source ProjectFile path.
 */
public class ComponentProjectFilePathTest extends GoTestBase {

    @Test
    public void testGoStructHasCorrectSourceFileAttr() throws Exception {
        final String code = "package main\ntype person struct {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.person").get().sourceFile().equals("/person.go"));
    }

    @Test
    public void testGoStructMethodCorrectSourceFileAttr() throws Exception {
        final String code = "package main\ntype person struct {} \n func (p person) x() int {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.person.x() : (int)").get().sourceFile().equals("/person.go"));
    }

    @Test
    public void testGoInterfaceMethodSourceFileAttr() throws Exception {
        final String code = "package main\n type person interface {\n area() float64 \n} type teacher struct{}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.person.area() : (float64)")
                .get().sourceFile().equals("/person.go"));
    }

    @Test
    public void testGoInterfaceSourceFileAttr() throws Exception {
        final String code = "package main\n type person interface {\n area() float64 \n} type teacher struct{}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.person")
                .get().sourceFile().equals("/person.go"));
    }
}
