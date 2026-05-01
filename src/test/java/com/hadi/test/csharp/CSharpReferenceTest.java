package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.TypeExtensionReference;
import com.hadi.clarpse.reference.TypeImplementationReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CSharpReferenceTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Refs.cs", """
                        namespace Demo;
                        using Demo.Tools;
                        using AliasRepo = Demo.Tools.Repo;
                        public interface IRunner {}
                        public class BaseUser {}
                        public class User : BaseUser, IRunner {
                          public Repo Repo { get; set; }
                          public AliasRepo AliasRepo { get; set; }
                          public User(Repo repo) { this.Repo = repo; }
                          public void Save(string message) { var helper = new Helper(); }
                        }
                        namespace Demo.Tools;
                        public class Repo {}
                        public class Helper {}
                        """)
        ).model();
    }

    @Test
    public void extensionReferenceIsResolved() {
        assertTrue(model.getComponent("Demo.User").get()
                .references(OOPSourceModelConstants.TypeReferences.EXTENSION)
                .contains(new TypeExtensionReference("Demo.BaseUser")));
    }

    @Test
    public void implementationReferenceIsResolved() {
        assertTrue(model.getComponent("Demo.User").get()
                .references(OOPSourceModelConstants.TypeReferences.IMPLEMENTATION)
                .contains(new TypeImplementationReference("Demo.IRunner")));
    }

    @Test
    public void propertyTypeReferenceIsResolved() {
        assertEquals("Demo.Tools.Repo",
                model.getComponent("Demo.User.Repo").get()
                        .references(OOPSourceModelConstants.TypeReferences.SIMPLE)
                        .get(0).invokedComponent());
    }

    @Test
    public void aliasUsingTypeReferenceIsResolved() {
        assertEquals("Demo.Tools.Repo",
                model.getComponent("Demo.User.AliasRepo").get()
                        .references(OOPSourceModelConstants.TypeReferences.SIMPLE)
                        .get(0).invokedComponent());
    }

    @Test
    public void objectCreationReferenceIsResolved() {
        assertTrue(model.getComponent("Demo.User.Save(string)").get()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE)
                .stream()
                .anyMatch(ref -> ref.invokedComponent().equals("Demo.Tools.Helper")));
    }

    @Test
    public void capitalizedMemberReceiverIsNotMisclassifiedAsStaticType() throws Exception {
        final OOPSourceCodeModel memberAccessModel = CSharpTestUtil.compileInline(
                new ProjectFile("/Store.cs", """
                        namespace Demo;
                        public interface IStore { void Clear(); }
                        public class Store : IStore { public void Clear() {} }
                        public class Tracker {
                          public IStore Store { get; set; }
                          public void Reset() { Store.Clear(); }
                        }
                        """)
        ).model();

        assertTrue(memberAccessModel.getComponent("Demo.Tracker.Reset()").get()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE)
                .stream()
                .noneMatch(ref -> ref.invokedComponent().equals("Store")));
        assertTrue(memberAccessModel.getComponent("Demo.Tracker.Reset()").get()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE)
                .stream()
                .noneMatch(ref -> ref.invokedComponent().equals("Demo.Tracker.Store")));
    }
}
