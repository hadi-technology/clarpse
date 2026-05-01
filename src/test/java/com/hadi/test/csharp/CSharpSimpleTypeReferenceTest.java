package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CSharpSimpleTypeReferenceTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Refs.cs", """
                        namespace Demo;
                        using Demo.Tools;
                        public class User {
                          public Repo Repo { get; set; }
                          public event LogHandler Saved;
                          public User(Repo repo) { this.Repo = repo; }
                          public void Save(string message) { Helper helper = new Helper(); }
                          public delegate void LogHandler(string value);
                        }
                        namespace Demo.Tools;
                        public class Repo {}
                        public class Helper {}
                        """)
        ).model();
    }

    @Test
    public void fieldLikePropertyTypeResolves() {
        assertEquals("Demo.Tools.Repo", model.getComponent("Demo.User.Repo").get()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0).invokedComponent());
    }

    @Test
    public void eventTypeResolves() {
        assertEquals("Demo.User.LogHandler", model.getComponent("Demo.User.Saved").get()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0).invokedComponent());
    }

    @Test
    public void constructorParamTypeResolves() {
        assertEquals("Demo.Tools.Repo", model.getComponent("Demo.User.User(Repo).repo").get()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0).invokedComponent());
    }

    @Test
    public void methodParamBuiltinTypeResolves() {
        assertEquals("System.String", model.getComponent("Demo.User.Save(string).message").get()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0).invokedComponent());
    }

    @Test
    public void localTypedReferenceResolves() {
        assertEquals("Demo.Tools.Helper", model.getComponent("Demo.User.Save(string).helper").get()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0).invokedComponent());
    }

    @Test
    public void methodCapturesObjectCreationReference() {
        assertTrue(model.getComponent("Demo.User.Save(string)").get()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE).stream()
                .anyMatch(ref -> ref.invokedComponent().equals("Demo.Tools.Helper")));
    }

    @Test
    public void delegateParamBuiltinTypeResolves() {
        assertEquals("System.String", model.getComponent("Demo.User.LogHandler.value").get()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0).invokedComponent());
    }

    @Test
    public void constructorCapturesFieldAssignmentReference() {
        assertTrue(model.getComponent("Demo.User.User(Repo)").get()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE).stream()
                .anyMatch(ref -> ref.invokedComponent().equals("Demo.User.Repo")));
    }
}
