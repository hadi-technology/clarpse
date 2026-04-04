package com.hadi.test;

import org.junit.Assert;
import org.junit.Test;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileException;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tests for incremental parsing functionality in ClarpseProject.
 */
public class IncrementalParsingTest {

    @Test
    public void testUpdateModelNoChanges() throws CompileException {
        ProjectFiles pf = new ProjectFiles();
        pf.insertFile(new ProjectFile("src/Test.java",
            "package test; public class Test { }"));

        CompileResult original = new ClarpseProject(pf, Lang.JAVA).result();
        OOPSourceCodeModel baseModel = original.model();

        // Incremental update with no changes
        CompileResult result = ClarpseProject.updateModel(
            baseModel, new HashMap<String, String>(), new HashSet<String>(), Lang.JAVA);

        Assert.assertEquals("Model size should be unchanged", baseModel.size(), result.model().size());
        Assert.assertTrue("Should have no failures", result.failures().isEmpty());
    }

    @Test
    public void testUpdateModelAddFile() throws CompileException {
        ProjectFiles pf = new ProjectFiles();
        pf.insertFile(new ProjectFile("src/Original.java",
            "package test; public class Original { }"));

        CompileResult original = new ClarpseProject(pf, Lang.JAVA).result();
        OOPSourceCodeModel baseModel = original.model();
        int originalSize = baseModel.size();

        // Add a new file
        Map<String, String> added = new HashMap<String, String>();
        added.put(
            "src/NewClass.java",
            "package test; public class NewClass { public void newMethod() { } }"
        );

        CompileResult result = ClarpseProject.updateModel(
            baseModel, added, new HashSet<String>(), Lang.JAVA);

        Assert.assertTrue("Model should be larger after adding a file", result.model().size() > originalSize);
        Assert.assertTrue("Model should contain the new class", result.model().containsComponent("test.NewClass"));
    }

    @Test
    public void testUpdateModelDeleteFile() throws CompileException {
        ProjectFiles pf = new ProjectFiles();
        pf.insertFile(new ProjectFile("src/ClassA.java",
            "package test; public class ClassA { }"));
        pf.insertFile(new ProjectFile("src/ClassB.java",
            "package test; public class ClassB { }"));

        CompileResult original = new ClarpseProject(pf, Lang.JAVA).result();
        OOPSourceCodeModel baseModel = original.model();
        int originalSize = baseModel.size();

        // Get the actual source file path from the component
        String actualPath = baseModel.getComponent("test.ClassA")
            .map(cmp -> cmp.sourceFile())
            .orElse("/test/ClassA.java");

        // Delete ClassA.java
        Set<String> deletedFiles = new HashSet<String>();
        deletedFiles.add(actualPath);

        CompileResult result = ClarpseProject.updateModel(
            baseModel, new HashMap<String, String>(), deletedFiles, Lang.JAVA);

        Assert.assertTrue("Model should be smaller after deletion", result.model().size() < originalSize);
        Assert.assertFalse("Deleted class should be removed", result.model().containsComponent("test.ClassA"));
        Assert.assertTrue("Other class should still be present", result.model().containsComponent("test.ClassB"));
    }

    @Test
    public void testUpdateModelModifyFile() throws CompileException {
        ProjectFiles pf = new ProjectFiles();
        pf.insertFile(new ProjectFile("src/Foo.java",
            "package test; public class Foo { }"));

        CompileResult original = new ClarpseProject(pf, Lang.JAVA).result();
        OOPSourceCodeModel baseModel = original.model();

        // Get the actual source file path from the component
        String actualPath = baseModel.getComponent("test.Foo")
            .map(cmp -> cmp.sourceFile())
            .orElse("/test/Foo.java");

        // Modify Foo.java by adding a method
        Map<String, String> modified = new HashMap<String, String>();
        modified.put(
            actualPath,
            "package test; public class Foo { public void newMethod() { } }"
        );

        CompileResult result = ClarpseProject.updateModel(
            baseModel, modified, new HashSet<String>(), Lang.JAVA);

        // Adding a method increases component count by 1 (original: 1 class, modified: 1 class + 1 method)
        Assert.assertEquals("Model size should increase by 1 (added method)", baseModel.size() + 1, result.model().size());
        // The component should still exist
        Assert.assertTrue("Modified class should still be present", result.model().containsComponent("test.Foo"));
    }

    @Test
    public void testGetComponentNamesForFile() throws CompileException {
        ProjectFiles pf = new ProjectFiles();
        pf.insertFile(new ProjectFile("src/MultiFile.java",
            "package test; " +
            "public class MultiFile { " +
            "  public void method1() { } " +
            "  public void method2() { } " +
            "}"
        ));

        CompileResult result = new ClarpseProject(pf, Lang.JAVA).result();
        OOPSourceCodeModel model = result.model();

        // Get the actual source file path from the component
        String actualPath = model.getComponent("test.MultiFile")
            .map(cmp -> cmp.sourceFile())
            .orElse("/test/MultiFile.java");

        Set<String> componentNames = model.getComponentNamesForFile(actualPath);

        Assert.assertNotNull("Component names should not be null", componentNames);
        Assert.assertFalse("Should have components", componentNames.isEmpty());
        Assert.assertTrue("Should contain the class component", componentNames.contains("test.MultiFile"));
    }

    @Test
    public void testRemoveComponentsForFile() throws CompileException {
        ProjectFiles pf = new ProjectFiles();
        pf.insertFile(new ProjectFile("src/Temp.java",
            "package test; public class Temp { public void method() { } }"));

        CompileResult result = new ClarpseProject(pf, Lang.JAVA).result();
        OOPSourceCodeModel model = result.model();
        int originalSize = model.size();

        // Get the actual source file path from the component
        String actualPath = model.getComponent("test.Temp")
            .map(cmp -> cmp.sourceFile())
            .orElse("/test/Temp.java");

        // Remove all components from the file
        Set<String> removed = model.removeComponentsForFile(actualPath);

        Assert.assertFalse("Should have removed components", removed.isEmpty());
        Assert.assertEquals("All components from file should be removed", originalSize, removed.size());
        Assert.assertEquals("Model should be empty after removal", 0, model.size());
        Assert.assertFalse("Removed component should not be in model", model.containsComponent("test.Temp"));
    }

    @Test
    public void testSourceFiles() throws CompileException {
        ProjectFiles pf = new ProjectFiles();
        pf.insertFile(new ProjectFile("src/A.java", "package test; public class A { }"));
        pf.insertFile(new ProjectFile("src/B.java", "package test; public class B { }"));

        CompileResult result = new ClarpseProject(pf, Lang.JAVA).result();
        OOPSourceCodeModel model = result.model();

        Set<String> sourceFiles = model.sourceFiles();

        Assert.assertNotNull("Source files should not be null", sourceFiles);
        Assert.assertEquals("Should have 2 source files", 2, sourceFiles.size());
        // Verify we can look up components by their actual source file paths
        model.getComponent("test.A").ifPresent(cmp -> {
            Assert.assertTrue("Should contain A's source file", sourceFiles.contains(cmp.sourceFile()));
        });
        model.getComponent("test.B").ifPresent(cmp -> {
            Assert.assertTrue("Should contain B's source file", sourceFiles.contains(cmp.sourceFile()));
        });
    }

    @Test
    public void testGetComponentDirect() throws CompileException {
        ProjectFiles pf = new ProjectFiles();
        pf.insertFile(new ProjectFile("src/DirectTest.java",
            "package test; public class DirectTest { }"));

        CompileResult result = new ClarpseProject(pf, Lang.JAVA).result();
        OOPSourceCodeModel model = result.model();

        // getComponentDirect returns the actual component (no copy)
        Assert.assertTrue("getComponentDirect should find the component", model.getComponentDirect("test.DirectTest").isPresent());

        // Verify that getting the component directly and through getComponent
        // return the same data (but different instances for getComponent)
        com.hadi.clarpse.sourcemodel.Component directCmp = model.getComponentDirect("test.DirectTest").get();
        com.hadi.clarpse.sourcemodel.Component copiedCmp = model.getComponent("test.DirectTest").get();

        Assert.assertEquals("Unique names should match", directCmp.uniqueName(), copiedCmp.uniqueName());
        // Direct access returns the actual component
        Assert.assertSame("getComponentDirect should return the actual component", directCmp, model.getComponentDirect("test.DirectTest").get());
        // getComponent returns a copy
        Assert.assertNotSame("getComponent should return a copy", copiedCmp, model.getComponentDirect("test.DirectTest").get());
    }

    @Test
    public void testCrossFileReferenceReclassification() throws CompileException {
        // Create a model with ClassA that references ClassB
        ProjectFiles pf = new ProjectFiles();
        pf.insertFile(new ProjectFile("src/A.java",
            "package test; public class A { private B b = new B(); public void method() { b.call(); } }"));
        pf.insertFile(new ProjectFile("src/B.java",
            "package test; public class B { public void call() { } }"));

        CompileResult original = new ClarpseProject(pf, Lang.JAVA).result();
        OOPSourceCodeModel baseModel = original.model();

        // Modify B by adding a new method
        Map<String, String> modifiedB = new HashMap<String, String>();
        modifiedB.put("src/B.java",
            "package test; public class B { public void call() { } public void newMethod() { } }");

        CompileResult updated = ClarpseProject.updateModel(
            baseModel, modifiedB, new HashSet<String>(), Lang.JAVA);

        // Verify that A's reference to B is still marked as internal
        com.hadi.clarpse.sourcemodel.Component componentA =
            updated.model().getComponent("test.A").orElse(null);
        Assert.assertNotNull("Component A should exist", componentA);

        // The reference to B should be internal
        boolean hasInternalRefToB = false;
        for (com.hadi.clarpse.reference.ComponentReference ref : componentA.references()) {
            if (ref.invokedComponent().equals("test.B") && !ref.isExternal()) {
                hasInternalRefToB = true;
                break;
            }
        }

        Assert.assertTrue("A's reference to B should still be internal after B is re-parsed", hasInternalRefToB);
    }

    @Test
    public void testIncrementalParsingPerformance() throws CompileException {
        // Create a base model with multiple files
        ProjectFiles pf = new ProjectFiles();
        for (int i = 0; i < 10; i++) {
            pf.insertFile(new ProjectFile("src/Class" + i + ".java",
                "package test; public class Class" + i + " { }"));
        }

        CompileResult original = new ClarpseProject(pf, Lang.JAVA).result();
        OOPSourceCodeModel baseModel = original.model();
        int originalSize = baseModel.size();

        // Get the actual source file path from the component
        String actualPath = baseModel.getComponent("test.Class5")
            .map(cmp -> cmp.sourceFile())
            .orElse("/test/Class5.java");

        // Modify just one file
        Map<String, String> modified = new HashMap<String, String>();
        modified.put(actualPath,
            "package test; public class Class5 { public void newMethod() { } }");

        long startTime = System.currentTimeMillis();
        CompileResult result = ClarpseProject.updateModel(
            baseModel, modified, new HashSet<String>(), Lang.JAVA);
        long incrementalTime = System.currentTimeMillis() - startTime;

        // Adding a method increases component count by 1
        Assert.assertEquals("Model size should increase by 1 (added method)", originalSize + 1, result.model().size());
        Assert.assertTrue("Modified class should exist", result.model().containsComponent("test.Class5"));

        // Performance: incremental update should complete in reasonable time
        // This is a sanity check, not a strict performance requirement
        Assert.assertTrue("Incremental update should complete quickly", incrementalTime < 5000);
    }

    @Test
    public void testMultipleFileModifications() throws CompileException {
        ProjectFiles pf = new ProjectFiles();
        pf.insertFile(new ProjectFile("src/A.java", "package test; public class A { }"));
        pf.insertFile(new ProjectFile("src/B.java", "package test; public class B { }"));
        pf.insertFile(new ProjectFile("src/C.java", "package test; public class C { }"));

        CompileResult original = new ClarpseProject(pf, Lang.JAVA).result();
        OOPSourceCodeModel baseModel = original.model();

        // Get the actual source file paths
        String actualPathA = baseModel.getComponent("test.A")
            .map(cmp -> cmp.sourceFile())
            .orElse("/test/A.java");
        String actualPathB = baseModel.getComponent("test.B")
            .map(cmp -> cmp.sourceFile())
            .orElse("/test/B.java");

        // Modify multiple files
        Map<String, String> modified = new HashMap<String, String>();
        modified.put(actualPathA, "package test; public class A { public void a() { } }");
        modified.put(actualPathB, "package test; public class B { public void b() { } }");

        CompileResult result = ClarpseProject.updateModel(
            baseModel, modified, new HashSet<String>(), Lang.JAVA);

        Assert.assertTrue("Class A should still exist", result.model().containsComponent("test.A"));
        Assert.assertTrue("Class B should still exist", result.model().containsComponent("test.B"));
        Assert.assertTrue("Class C should still exist (not modified)", result.model().containsComponent("test.C"));
    }

    @Test
    public void testAddAndDeleteInSameUpdate() throws CompileException {
        ProjectFiles pf = new ProjectFiles();
        pf.insertFile(new ProjectFile("src/A.java", "package test; public class A { }"));
        pf.insertFile(new ProjectFile("src/B.java", "package test; public class B { }"));

        CompileResult original = new ClarpseProject(pf, Lang.JAVA).result();
        OOPSourceCodeModel baseModel = original.model();
        int originalSize = baseModel.size();

        // Get the actual source file path from the component to delete
        String actualPathB = baseModel.getComponent("test.B")
            .map(cmp -> cmp.sourceFile())
            .orElse("/test/B.java");

        // Add a new file and delete an existing one
        Map<String, String> added = new HashMap<String, String>();
        added.put(actualPathB, "package test; public class C { }"); // Use B's path for C (replacement)

        Set<String> deleted = new HashSet<String>();
        deleted.add(actualPathB);

        CompileResult result = ClarpseProject.updateModel(
            baseModel, added, deleted, Lang.JAVA);

        Assert.assertEquals("Model size should remain same (one added, one deleted)", originalSize, result.model().size());
        Assert.assertTrue("Class A should still exist", result.model().containsComponent("test.A"));
        Assert.assertTrue("Class C should be added", result.model().containsComponent("test.C"));
        Assert.assertFalse("Class B should be deleted", result.model().containsComponent("test.B"));
    }
}
