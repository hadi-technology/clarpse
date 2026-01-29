package com.hadi.test.go;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class CodeFragmentTest extends GoTestBase {

    @Test
    public void testInterfaceMethodSpecCodeFragment() throws Exception {
        final String code = "package main\ntype person interface { testMethod() int}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.person.testMethod() : (int)").get().codeFragment().equals("testMethod() : (int)"));
    }

    @Test
    public void testGoReturnStructMethodComplexCodeFragment() throws Exception {
        final String code = "package main\ntype Person struct {}";
        final String codeB = "package main\n import \"main\" \n func (p main.Person)" +
            " x(args []value, x,y map[value]value, v, u value) (j,i []value, map[value]value.test)  {}";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/cherry.go", code));
        projectFiles.insertFile(new ProjectFile("/src/com/cakes/test.go", codeB));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        System.out.println(generatedSourceModel.getComponent("main.Person.x([]value, " +
                                                                 "map[value]value, map[value]value, value, value) : ([]value, []value, map[value]value.test)").get().codeFragment());
        assertTrue(generatedSourceModel.getComponent("main.Person.x([]value, map[value]value, map[value]value, value, value) : ([]value, []value, map[value]value.test)").get().codeFragment().equals("x([]value, map[value]value, map[value]value, value, value) : ([]value, []value, map[value]value.test)"));
        assertTrue(generatedSourceModel.getComponent("main.Person.x([]value, map[value]value, map[value]value, value, value) : ([]value, []value, map[value]value.test).u")
                .get().parentUniqueName().equals("main.Person.x([]value, map[value]value, map[value]value, value, value) : ([]value, []value, map[value]value.test)"));
        assertTrue(generatedSourceModel.getComponent("main.Person.x([]value, map[value]value, map[value]value, value, value) : ([]value, []value, map[value]value.test)")
                .get().parentUniqueName().equals("main.Person"));
    }

    @Test
    public void testInterfaceComplexMethodSpecParamsExist() throws Exception {
        final String code = "package go\n import \"game/org\" \n type person interface { testMethod() org.Cake}";
        final String codeB = "package org\n type Cake struct {}";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/github/go/person.go", code));
        projectFiles.insertFile(new ProjectFile("/src/github/game/org/cakes.go", codeB));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("github.go.person.testMethod() : (org.Cake)").get().codeFragment()
                .equals("testMethod() : (org.Cake)"));
    }

    @Test
    public void fieldVarCodeFragment() throws Exception {

        final String code = "package main\n import\"fmt\"\n type person struct {SuggestFor []value}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.person.SuggestFor").get().codeFragment()
                .equals("SuggestFor : []value"));
    }

    @Test
    public void structMethodCodeFragment() throws Exception {

        final String code = "package main\n import\"fmt\"\n type Command struct {} func (c *Command) SetHelpCommand(cmd *Command) {}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        System.out.println(generatedSourceModel.getComponent("main.Command.SetHelpCommand(*Command)")
                .get().codeFragment());
        assertTrue(generatedSourceModel.getComponent("main.Command.SetHelpCommand(*Command)")
                .get().codeFragment().equals("SetHelpCommand(*Command)"));
    }


    @Test
    public void fieldVarCodeFragmentWithComment() throws Exception {

        final String code = "package main\n import\"fmt\"\n type person struct {usageFunc func(*Command) error" +
                " // Usage can be defined by application\n}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.person.usageFunc").get().codeFragment()
                .equals("usageFunc : func(*Command) error"));
    }

    @Test
    public void fieldVarFuncTypeCodeFragment() throws Exception {

        final String code = "package main\n type person struct { PersistentPreRun func(cmd *Command, args []value) }";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        System.out.println(generatedSourceModel.getComponent("main.person.PersistentPreRun").get()
                .codeFragment());
        assertTrue(generatedSourceModel.getComponent("main.person.PersistentPreRun").get()
                .codeFragment().equals("PersistentPreRun : func(cmd *Command, args []value)"));
    }

    @Test
    public void fieldVarFuncTypeCodeFragmentWithComment() throws Exception {

        final String code = "package main\n type person struct { PersistentPreRun func(cmd *Command, args []value)//test \n}";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        System.out.println(generatedSourceModel.getComponent("main.person.PersistentPreRun")
                .get().codeFragment());
        assertTrue(generatedSourceModel.getComponent("main.person.PersistentPreRun").get()
                .codeFragment().equals("PersistentPreRun : func(cmd *Command, args []value)"));
    }

    @Test
    public void testInterfaceAnonymousTypeMethods() throws Exception {
        final String code = "package main \n type plain interface \n{ testMethodv2() (value, uintptr) {} }";
        final String codeB = "package main\n type Person struct {}";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/plain.go", code));
        projectFiles.insertFile(new ProjectFile("/src/main/test.go", codeB));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.plain.testMethodv2() : (value, uintptr)").get().codeFragment().equals("testMethodv2() : (value, uintptr)"));
    }

    @Test
    public void fieldVarFuncTypeCodeFragmentv2() throws Exception {

        final String code = "package main\n type person struct { globNormFunc func(f *flag.FlagSet, name value)" +
                " flag.NormalizedName }";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        System.out.println(generatedSourceModel.getComponent("main.person.globNormFunc").get()
                .codeFragment());
        assertTrue(generatedSourceModel.getComponent("main.person.globNormFunc").get().codeFragment()
                .equals("globNormFunc : func(f *flag.FlagSet, name value) flag.NormalizedName"));
    }

    @Test
    public void testGoStructMethodCodeFragment() throws Exception {
        final String code = "package main\ntype Person struct {}";
        final String codeB = "package main\n import tester \"main\" \n func (p tester.Person) x() int {}";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/cherry.go", code));
        projectFiles.insertFile(new ProjectFile("/src/com/cakes/test.go", codeB));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        System.out.println(generatedSourceModel.getComponent("main.Person.x() : (int)").get().codeFragment());
        assertTrue(generatedSourceModel.getComponent("main.Person.x() : (int)").get().codeFragment()
                .equals("x() : (int)"));
    }

    @Test
    public void testGoStructMethodSpecCodeFragment() throws Exception {
        final String code = "package main\ntype Person interface {  Get(key interface{}) (value interface{}, ok bool) }";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/cherry.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        System.out.println(generatedSourceModel.getComponent("main.Person.Get(interface{}) : (interface{}, bool)").get().codeFragment());
        assertTrue(generatedSourceModel.getComponent("main.Person.Get(interface{}) : (interface{}, bool)")
                .get().codeFragment().equals("Get(interface{}) : (interface{}, bool)"));
    }

    @Test
    public void testGoStructMethodWithFuncAsParamCodeFragment() throws Exception {
        final String code = "package main\ntype Person struct {  func (c *Person) SetGlobalNormalizationFunc(n func(f " +
                "*flag.FlagSet, name value)) {} }";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/cherry.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        System.out.println(generatedSourceModel.getComponent("main.Person.SetGlobalNormalizationFunc(func(f *flag.FlagSet, name value))").get().codeFragment());
        assertTrue(generatedSourceModel.getComponent("main.Person.SetGlobalNormalizationFunc(func(f *flag.FlagSet, name value))").get().codeFragment()
                .equals("SetGlobalNormalizationFunc(func(f *flag.FlagSet, name value))"));
    }

    @Test
    public void testGoInterfaceMethodWithFuncAsParamCodeFragment() throws Exception {
        final String code = "package main\ntype Person interface {  func SetGlobalNormalizationFunc(n func(f *flag.FlagSet, name value)) }";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/cherry.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        System.out.println(generatedSourceModel.getComponent("main.Person.SetGlobalNormalizationFunc(func(f *flag.FlagSet, name value))").get().codeFragment());
        assertTrue(generatedSourceModel.getComponent("main.Person.SetGlobalNormalizationFunc(func(f *flag.FlagSet, name value))").get().codeFragment()
                .equals("SetGlobalNormalizationFunc(func(f *flag.FlagSet, name value))"));

        assertTrue(generatedSourceModel.getComponent("main.Person.SetGlobalNormalizationFunc(func(f *flag.FlagSet, name value))").get().parentUniqueName()
                .equals("main.Person"));
    }

    @Test
    public void testGoStructMethodWithFuncAsReturnCodeFragment() throws Exception {
        final String code = "package main\ntype Person struct {  func (c *Person) SetGlobalNormalizationFunc(n int) (func(f *flag.FlagSet, name value)) {} }";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/cherry.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.Person.SetGlobalNormalizationFunc(int) : (func(f *flag.FlagSet, name value))").get().codeFragment().equals(
                "SetGlobalNormalizationFunc(int) : (func(f *flag.FlagSet, name value))"));
    }

    @Test
    public void testGoStructMethodWithFuncAsPartOfReturnCodeFragment() throws Exception {
        final String code = "package main\ntype Person struct {  func (c *Person) SetGlobalNormalizationFunc(n int) (x int, func(f *flag.FlagSet, name value)) {} }";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/cherry.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.Person.SetGlobalNormalizationFunc(int) : (int, func(f *flag.FlagSet, name value))").get().codeFragment().equals(
                "SetGlobalNormalizationFunc(int) : (int, func(f *flag.FlagSet, name value))"));
    }

    @Test
    public void testGoStructMethodMultipleDeclarationReturnCodeFragment() throws Exception {
        final String code = "package main\ntype Person struct {}";
        final String codeB = "package main\n import tester \"main\" \n func (p tester.Person) x() (x,y int) {}";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/cherry.go", code));
        projectFiles.insertFile(new ProjectFile("/src/com/cakes/test.go", codeB));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.Person.x() : (int, int)").get().codeFragment().equals("x() : (int, int)"));
    }

    @Test
    public void testGoStructMethodMultipleIndividualDeclarationReturnCodeFragment() throws Exception {
        final String code = "package main\ntype Person struct {}";
        final String codeB = "package main\n import tester \"main\" \n func (p tester.Person) x() (x uint8,y int) {}";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/cherry.go", code));
        projectFiles.insertFile(new ProjectFile("/src/com/cakes/test.go", codeB));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        System.out.println(generatedSourceModel.getComponent("main.Person.x() : (uint8, int)").get().codeFragment());
        assertTrue(generatedSourceModel.getComponent("main.Person.x() : (uint8, int)").get().codeFragment().equals("x() : (uint8, int)"));
    }

    @Test
    public void testGoStructMethodMultipleComplexDeclarationReturnCodeFragment() throws Exception {
        final String code = "package main\ntype Person struct {}";
        final String codeB = "package main\n import tester \"main\" \n func (p tester.Person) x() (x,z uint8, y int) {}";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/cherry.go", code));
        projectFiles.insertFile(new ProjectFile("/src/com/cakes/test.go", codeB));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.Person.x() : (uint8, uint8, int)").get().codeFragment().equals("x() : (uint8, uint8, int)"));
    }

    @Test
    public void testGoStructMethodMultipleReturnCodeFragment() throws Exception {
        final String code = "package main\ntype Person struct {}";
        final String codeB = "package main\n import tester \"main\" \n func (p tester.Person) x() (value, int) {}";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/cherry.go", code));
        projectFiles.insertFile(new ProjectFile("/src/com/cakes/test.go", codeB));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("main.Person.x() : (value, int)").get().codeFragment().equals("x() : (value, int)"));
    }

    @Test
    public void testGoNoReturnStructMethodCodeFragmentIsNull() throws Exception {
        final String code = "package main\ntype Person struct {}";
        final String codeB = "package main\n import tester \"main\" \n func (p main.Person) x() {}";
        projectFiles = goLangProjectFilesFixture("/src");
        projectFiles.insertFile(new ProjectFile("/src/main/cherry.go", code));
        projectFiles.insertFile(new ProjectFile("/src/com/cakes/test.go", codeB));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        System.out.println(generatedSourceModel.getComponent("main.Person.x()").get().codeFragment());
        assertTrue(generatedSourceModel.getComponent("main.Person.x()").get().codeFragment().equals("x()"));
    }
}
