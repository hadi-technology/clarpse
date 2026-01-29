package com.hadi.test.go;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Tests to ensure component type attribute of parsed components are accurate.
 */
public class ComponentTypeTest extends GoTestBase {
    @Test
    public void testInterfaceAnonymousTypeMethodParamType() throws Exception {
        final String code = "package main \n type plain interface \n{ testMethodv2(x value, h int) (value, uintptr) {} }";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/plain.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertSame(generatedSourceModel.getComponent("main.plain.testMethodv2(value, int) : (value, uintptr).x")
                .get().componentType(), OOPSourceModelConstants.ComponentType.METHOD_PARAMETER_COMPONENT);
    }

    @Test
    public void testGoStructMethodSingleParamComponentType() throws Exception {
        final String code = "package main\ntype person struct {} \n func (p person) lol(x int) {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertSame(generatedSourceModel.getComponent("main.person.lol(int).x")
                .get().componentType(), OOPSourceModelConstants.ComponentType.METHOD_PARAMETER_COMPONENT);
    }

    @Test
    public void testGoInterfaceComponentType() throws Exception {
        final String code = "package main\n type person interface {\n area() float64 \n} type teacher struct{}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertSame(generatedSourceModel.getComponent("main.person").get()
                .componentType(), OOPSourceModelConstants.ComponentType.INTERFACE);
    }

    @Test
    public void testGoStructMethodComponentType() throws Exception {
        final String code = "package main\ntype person struct {} \n func (p person) x() int {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertSame(generatedSourceModel.getComponent("main.person.x() : (int)").get()
                .componentType(), OOPSourceModelConstants.ComponentType.METHOD);
    }

    @Test
    public void testGoStructFieldVarComponenType() throws Exception {
        final String code = "package main\nimport \"test/math\"\ntype person struct {mathObj math.Person}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertSame(generatedSourceModel.getComponent("main.person.mathObj").get()
                .componentType(), OOPSourceModelConstants.ComponentType.FIELD);
    }

    @Test
    public void testGoStructHasCorrectComponentType() throws Exception {
        final String code = "package main\ntype person struct {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertSame(generatedSourceModel.getComponent("main.person").get()
                .componentType(), OOPSourceModelConstants.ComponentType.STRUCT);
    }


    @Test
    public void testInterfaceMethodSpecComponentType() throws Exception {

        final String code = "package main\ntype person interface { \n//test\n testMethod() int}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertSame(generatedSourceModel.getComponent("main.person.testMethod() : (int)").get()
                .componentType(), OOPSourceModelConstants.ComponentType.METHOD);
    }

    @Test
    public void localVarComponentType() throws Exception {

        final String code = "package main \n type plain struct \n{} \n func (t plain) testMethodv2 () {\n var i int  = 2;\n}";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/plain.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertSame(generatedSourceModel.getComponent("main.plain.testMethodv2().i").get()
                .componentType(), OOPSourceModelConstants.ComponentType.LOCAL);
    }
}
