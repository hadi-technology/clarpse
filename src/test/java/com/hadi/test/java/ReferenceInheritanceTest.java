package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Ensure component references are inherited by its parents.
 */
public class ReferenceInheritanceTest {

    @Test
    public void testClassInheritsFieldReferences() throws Exception {
        final String code = "class Test { String fieldVar; }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("java.lang.String", ((ComponentReference) generatedSourceModel.getComponent("Test").get().references().toArray()[0])
                .invokedComponent());
    }

    @Test
    public void testClassInheritsMethodReferences() throws Exception {
        final String code = "class Test { public String aMethod() { return \"\"; } }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/Test.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("java.lang.String", ((ComponentReference) generatedSourceModel.getComponent("Test").get().references().toArray()[0])
                .invokedComponent());
    }

    @Test
    public void testClassInheritsLocalVarsReferences() throws Exception {
        final String code = "class Test { public void fieldVar() { String test; } }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("java.lang.String", ((ComponentReference) generatedSourceModel.getComponent("Test").get().references().toArray()[0])
                .invokedComponent());
    }

    @Test
    public void testClassInheritsMethodParamsReferences() throws Exception {
        final String code = "class Test { public void fieldVar(String test) { } }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("java.lang.String", ((ComponentReference) generatedSourceModel.getComponent("Test").get().references().toArray()[0])
                .invokedComponent());
    }

    @Test
    public void testClassInheritsNestedClassReferences() throws Exception {
        final String code = "class Test { class NestedClass { public void fieldVar(String test) { } } }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("java.lang.String", ((ComponentReference) generatedSourceModel.getComponent("Test").get().references().toArray()[0])
                .invokedComponent());
    }

    @Test
    public void testClassDoesNotInheritExtendsAndImplementsReferences() throws Exception {
        final String code = "class Test { class NestedClass extends String implements Integer { } }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Test").get().references().isEmpty());
    }

    @Test
    public void testInterfaceInheritsFieldReferences() throws Exception {
        final String code = "interface Test { String localVar; }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("java.lang.String", ((ComponentReference) generatedSourceModel.getComponent(
            "Test").get().references().toArray()[0])
            .invokedComponent());
    }

    @Test
    public void testInterfaceInheritsMethodReferences() throws Exception {
        final String code = "interface Test { abstract String aMethod(); }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("java.lang.String", ((ComponentReference) generatedSourceModel.getComponent("Test").get().references().toArray()[0])
                .invokedComponent());
    }

    @Test
    public void testInterfaceInheritsMethodParamsReferences() throws Exception {
        final String code = "interface Test { abstract void aMethod(String test); }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("java.lang.String", ((ComponentReference) generatedSourceModel.getComponent("Test").get().references().toArray()[0])
                .invokedComponent());
    }

    @Test
    public void testMethodInheritsLocalVarsReferences() throws Exception {
        final String code = "class Test { public void aMethod(){String test;} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("java.lang.String", ((ComponentReference) generatedSourceModel.getComponent("Test.aMethod()").get().references().toArray()[0])
                .invokedComponent());
    }

    @Test
    public void testMethodInheritsMethodParamsReferences() throws Exception {
        final String code = "class Test { public void aMethod(String test){} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("java.lang.String", ((ComponentReference) generatedSourceModel.getComponent("Test.aMethod(String)")
                .get().references().toArray()[0]).invokedComponent());
    }
}
