package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CSharpImportExternalAndRecordVariantsTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/src/shared/Usings.cs", """
                        global using Demo.Shared;
                        global using GlobalRepo = Demo.Shared.Repo;
                        global using static System.Math;
                        """),
                new ProjectFile("/src/shared/Types.cs", """
                        namespace Demo.Shared;
                        public class Repo {}
                        public class SharedThing {}
                        """),
                new ProjectFile("/src/app/Imports.cs", """
                        namespace Demo.App;
                        using System.Collections.Generic;
                        using System.Text;
                        using static System.String;
                        public class ImportConsumer {
                          public SharedThing Thing { get; set; }
                          public GlobalRepo Repo { get; set; }
                          public List<string> Names { get; set; }
                          public StringBuilder Builder { get; set; }
                          public double Measure() { return Math.Sqrt(4); }
                        }
                        """),
                new ProjectFile("/src/app/Records.cs", """
                        namespace Demo.App;
                        public record class AuditRecord(string Id, int Version);
                        public record struct AuditPoint(int X, int Y);
                        """)
        ).model();
    }

    @Test
    public void globalUsingResolvesCrossFileType() {
        assertEquals("Demo.Shared.SharedThing", model.copyOfComponent("Demo.App.ImportConsumer.Thing").get()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0).invokedComponent());
    }

    @Test
    public void globalAliasUsingResolvesCrossFileType() {
        assertEquals("Demo.Shared.Repo", model.copyOfComponent("Demo.App.ImportConsumer.Repo").get()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0).invokedComponent());
    }

    @Test
    public void normalUsingNamespaceImportIsRecorded() {
        assertTrue(model.copyOfComponent("Demo.App.ImportConsumer").get().imports().contains("System.Collections.Generic"));
    }

    @Test
    public void normalUsingTypeImportIsRecorded() {
        assertTrue(model.copyOfComponent("Demo.App.ImportConsumer").get().imports().contains("System.Text"));
    }

    @Test
    public void staticUsingImportIsRecorded() {
        assertTrue(model.copyOfComponent("Demo.App.ImportConsumer").get().imports().contains("System.String"));
    }

    @Test
    public void globalStaticUsingImportIsRecorded() {
        assertTrue(model.copyOfComponent("Demo.App.ImportConsumer").get().imports().contains("System.Math"));
    }

    @Test
    public void importedGenericCollectionTypeIsExternal() {
        final Component component = model.copyOfComponent("Demo.App.ImportConsumer.Names").get();
        assertTrue(component.externalDependencies().stream()
                .anyMatch(ref -> ref.invokedComponent().equals("List")));
    }

    @Test
    public void importedFrameworkBuilderTypeIsExternal() {
        final Component component = model.copyOfComponent("Demo.App.ImportConsumer.Builder").get();
        assertTrue(component.externalDependencies().stream()
                .anyMatch(ref -> ref.invokedComponent().equals("StringBuilder")));
    }

    @Test
    public void builtinStringTypeIsExternal() {
        final Component component = model.copyOfComponent("Demo.App.ImportConsumer.Names").get();
        assertTrue(component.externalDependencies().stream()
                .anyMatch(ref -> ref.invokedComponent().equals("System.String")));
    }

    @Test
    public void externalStaticMathTypeReferenceIsCaptured() {
        final Component component = model.copyOfComponent("Demo.App.ImportConsumer.Measure()").get();
        assertTrue(component.externalDependencies().stream()
                .anyMatch(ref -> ref.invokedComponent().equals("Math")));
    }

    @Test
    public void externalDoubleReturnTypeIsCaptured() {
        final Component component = model.copyOfComponent("Demo.App.ImportConsumer.Measure()").get();
        assertTrue(component.externalDependencies().stream()
                .anyMatch(ref -> ref.invokedComponent().equals("System.Double")));
    }

    @Test
    public void externalReferencesAreNotClassifiedInternal() {
        final Component component = model.copyOfComponent("Demo.App.ImportConsumer.Builder").get();
        assertTrue(component.internalDependencies().stream()
                .noneMatch(ref -> ref.invokedComponent().equals("StringBuilder")));
    }

    @Test
    public void recordClassMapsToClass() {
        assertEquals(OOPSourceModelConstants.ComponentType.CLASS,
                model.copyOfComponent("Demo.App.AuditRecord").get().componentType());
    }

    @Test
    public void recordStructMapsToStruct() {
        assertEquals(OOPSourceModelConstants.ComponentType.STRUCT,
                model.copyOfComponent("Demo.App.AuditPoint").get().componentType());
    }

    @Test
    public void recordClassPositionalMembersExist() {
        assertTrue(model.containsComponent("Demo.App.AuditRecord.Id"));
        assertTrue(model.containsComponent("Demo.App.AuditRecord.Version"));
    }

    @Test
    public void recordStructPositionalMembersExist() {
        assertTrue(model.containsComponent("Demo.App.AuditPoint.X"));
        assertTrue(model.containsComponent("Demo.App.AuditPoint.Y"));
    }

    @Test
    public void recordStructChildrenContainPositionalMembers() {
        assertTrue(model.copyOfComponent("Demo.App.AuditPoint").get().children().contains("Demo.App.AuditPoint.X"));
        assertTrue(model.copyOfComponent("Demo.App.AuditPoint").get().children().contains("Demo.App.AuditPoint.Y"));
    }

    @Test
    public void delegateLikeChildrenRemainParentedCorrectly() {
        assertTrue(model.copyOfComponent("Demo.App.ImportConsumer").get().children().contains("Demo.App.ImportConsumer.Measure()"));
    }

    @Test
    public void propertyChildrenRemainParentedCorrectly() {
        assertTrue(model.copyOfComponent("Demo.App.ImportConsumer").get().children().contains("Demo.App.ImportConsumer.Repo"));
        assertTrue(model.copyOfComponent("Demo.App.ImportConsumer").get().children().contains("Demo.App.ImportConsumer.Thing"));
    }

    @Test
    public void recordClassComponentNamesAreStable() {
        assertEquals("AuditRecord", model.copyOfComponent("Demo.App.AuditRecord").get().name());
        assertEquals("AuditPoint", model.copyOfComponent("Demo.App.AuditPoint").get().name());
    }
}
