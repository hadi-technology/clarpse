package com.hadi.test.go;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests to ensure package name attribute of parsed components are correct.
 */
public class PackageAttributeTest extends GoTestBase{

    @Test
    public void testGoStructMethodPackageName() throws Exception {
        final String code = "package main\ntype person struct {} \n func (p person) x() int {}";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals(generatedSourceModel.getComponent("main.person").get().pkg().path(),
                     generatedSourceModel.getComponent("main.person.x() : (int)").get().pkg().path());
    }

    @Test
    public void testGoStructMethodParamPackageName() throws Exception {
        final String code = "package main\ntype person struct {} \n func (p person) x(z int) int {}";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("/main",
                     generatedSourceModel.getComponent("main.person.x(int) : (int).z").get().pkg().path());
    }

    @Test
    public void testGoStructMethodLocalVarPackageName() throws Exception {
        final String code = "package main\ntype person struct {} \n func (p person) x() int {var z int}";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("/main",
                     generatedSourceModel.getComponent("main.person.x() : (int).z").get().pkg().path());
    }

    @Test
    public void testGoInterfacePackageName() throws Exception {
        final String code = "package main\ntype person interface {}";
        projectFiles.insertFile(new ProjectFile("/src/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("main",
                     generatedSourceModel.getComponent("src.person").get().pkg().name());
        assertEquals("/src",
                     generatedSourceModel.getComponent("src.person").get().pkg().path());
        assertEquals("src",
                     generatedSourceModel.getComponent("src.person").get().pkg().ellipsisSeparatedPkgPath());
    }

    @Test
    public void testGoStructPackageName() throws Exception {
        final String code = "package main\ntype person struct {}";
        projectFiles.insertFile(new ProjectFile("/src/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("main", generatedSourceModel.getComponent("src.person").get().pkg().name());
    }

    @Test
    public void testGoStructPackageNameWhenItDoesNotMatchDir() throws Exception {
        final String code = "package strawbs\ntype person struct {}";
        projectFiles = goLangProjectFilesFixture("/pkg");
        projectFiles.insertFile(new ProjectFile("/pkg/cukpcakes/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("/cukpcakes",
                     generatedSourceModel.getComponent("cukpcakes.person").get().pkg().path());
        assertEquals("strawbs",
                     generatedSourceModel.getComponent("cukpcakes.person").get().pkg().name());
    }

    @Test
    public void testGoStructPackageNameInRootDir() throws Exception {
        final String code = "package strawbs\ntype person struct {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("",
                     generatedSourceModel.getComponent("strawbs.person").get().pkg().path());
        assertEquals("strawbs",
                     generatedSourceModel.getComponent("strawbs.person").get().pkg().name());
    }
}
