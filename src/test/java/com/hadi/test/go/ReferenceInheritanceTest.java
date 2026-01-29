package com.hadi.test.go;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Ensure component references are inherited by its parents.
 */
public class ReferenceInheritanceTest extends GoTestBase {

    @Test
    public void StructInheritsFieldVarReferences() throws Exception {
        final String code = "package main\nimport \"test/math\"\ntype person struct {mathObj math.Person}";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/plain.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("test.math.Person", generatedSourceModel.getComponent("main.person")
                .get().references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0)
                .invokedComponent());
    }

    @Test
    public void StructMethodInheritsLocalVarReferences() throws Exception {
        final String code = "package main\nimport \"test/math\"\ntype person struct {} func (p person) x() int {var mathObj math.Person}";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/plain.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("test.math.Person", generatedSourceModel.getComponent("main.person.x() : (int)")
                .get().references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0)
                .invokedComponent());
    }

    @Test
    public void StructMethodInheritsMethodParamReferences() throws Exception {
        final String code = "package main\nimport \"test/math\"\ntype person struct {} func (p person) x(mathObj math.Person) int {}";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/plain.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("test.math.Person", generatedSourceModel.getComponent("main.person.x(math.Person) : (int)")
                .get().references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0)
                .invokedComponent());
    }

    @Test
    public void StructInheritsLocalVarReferences() throws Exception {
        final String code = "package main\nimport \"test/math\"\ntype person struct {} func (p person) x() int {var mathObj math.Person}";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/plain.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("test.math.Person", generatedSourceModel.getComponent("main.person")
                .get().references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0)
                .invokedComponent());
    }

    @Test
    public void StructInheritsMethodParamReferences() throws Exception {
        final String code = "package main\nimport \"test/math\"\ntype person struct {} func (p person) x(mathObj math.Person) int {}";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/plain.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("test.math.Person", generatedSourceModel.getComponent("main.person")
                .get().references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0)
                .invokedComponent());
    }
}
