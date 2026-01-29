package com.hadi.test.go;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ResolveImportsTest extends GoTestBase {
    @Test
    public void testShortImportType() throws Exception {
        final String code = "package main\n import\"fmt\"\n type person struct {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("fmt",
                     new ArrayList<>(generatedSourceModel.getComponent("main.person").get().imports())
                         .get(0));
    }

    @Test
    public void testLongImportType() throws Exception {
        final String code = "package main\n import m \"fmt\"\n type person struct {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("fmt",
                     new ArrayList<>(generatedSourceModel.getComponent("main.person").get().imports())
                         .get(0));
    }

    @Test
    public void testImportUsesFullUniquePathIfPossible() throws Exception {
        final String code = "package main\n import g \"http/cakes/github\"\n type person struct {}";
        final String codeB = "package github";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/main.go", code));
        projectFiles.insertFile(new ProjectFile("/src/http/cakes/github/person.go", codeB));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("http.cakes.github", new ArrayList<>(generatedSourceModel.getComponent("main.person").get().imports())
            .get(0));
    }

    @Test
    public void testDotImportType() throws Exception {
        final String code = "package main\n import . \"fmt\"\n type person struct {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("fmt",
                     new ArrayList<>(generatedSourceModel.getComponent("main.person").get().imports())
                         .get(0));
    }
}
