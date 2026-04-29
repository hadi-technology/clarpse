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

public class CSharpSpotCheckReferencesAndCommentsTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/src/app/User.cs", """
                        namespace Demo.App;
                        using Demo.Common;
                        using AliasRepo = Demo.Common.Repo;
                        /// <summary>Main user aggregate.</summary>
                        public partial class User : Entity, IRunner {
                          /// <summary>Persists data.</summary>
                          public AliasRepo Repo { get; set; }
                          public event SavedHandler Saved;
                          public delegate void SavedHandler(string value);
                          public User(Repo repo) { this.Repo = repo; }
                          public Helper Save(string message) {
                            // Build helper from repo {}
                            Helper helper = new Helper();
                            if (message != null && message.Length > 0) {
                              for (var i = 0; i < 1; i++) {}
                            }
                            return helper;
                          }
                        }
                        public abstract class Entity {}
                        public interface IRunner {}
                        """),
                new ProjectFile("/src/common/Common.cs", """
                        namespace Demo.Common;
                        public class Repo {}
                        public class Helper {}
                        """)
        ).model();
    }

    @Test
    public void classCommentIsCaptured() {
        assertTrue(model.getComponent("Demo.App.User").get().comment().contains("Main user aggregate"));
    }

    @Test
    public void methodCommentIsCaptured() {
        assertTrue(model.getComponent("Demo.App.User.Repo").get().comment().contains("Persists data"));
    }

    @Test
    public void propertyTypeReferenceResolvesThroughAliasUsing() {
        assertEquals("Demo.Common.Repo", model.getComponent("Demo.App.User.Repo").get()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0).invokedComponent());
    }

    @Test
    public void eventTypeReferenceResolvesToNestedDelegate() {
        assertEquals("Demo.App.User.SavedHandler", model.getComponent("Demo.App.User.Saved").get()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0).invokedComponent());
    }

    @Test
    public void constructorParamTypeReferenceResolves() {
        assertEquals("Demo.Common.Repo", model.getComponent("Demo.App.User.User(Repo).repo").get()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0).invokedComponent());
    }

    @Test
    public void methodParamBuiltinTypeReferenceResolves() {
        assertEquals("System.String", model.getComponent("Demo.App.User.Save(string).message").get()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0).invokedComponent());
    }

    @Test
    public void localTypeReferenceResolves() {
        assertEquals("Demo.Common.Helper", model.getComponent("Demo.App.User.Save(string).helper").get()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0).invokedComponent());
    }

    @Test
    public void methodCapturesObjectCreationReference() {
        assertTrue(model.getComponent("Demo.App.User.Save(string)").get()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE).stream()
                .anyMatch(ref -> ref.invokedComponent().equals("Demo.Common.Helper")));
    }

    @Test
    public void delegateParamBuiltinTypeResolves() {
        assertEquals("System.String", model.getComponent("Demo.App.User.SavedHandler.value").get()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0).invokedComponent());
    }

    @Test
    public void constructorCapturesAssignedPropertyReference() {
        assertTrue(model.getComponent("Demo.App.User.User(Repo)").get()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE).stream()
                .anyMatch(ref -> ref.invokedComponent().equals("Demo.App.User.Repo")));
    }

    @Test
    public void extensionReferenceResolves() {
        assertTrue(model.getComponent("Demo.App.User").get()
                .references(OOPSourceModelConstants.TypeReferences.EXTENSION)
                .contains(new TypeExtensionReference("Demo.App.Entity")));
    }

    @Test
    public void implementationReferenceResolves() {
        assertTrue(model.getComponent("Demo.App.User").get()
                .references(OOPSourceModelConstants.TypeReferences.IMPLEMENTATION)
                .contains(new TypeImplementationReference("Demo.App.IRunner")));
    }

    @Test
    public void methodCycloIncludesIfAndLoop() {
        assertEquals(4, model.getComponent("Demo.App.User.Save(string)").get().cyclo());
    }

    @Test
    public void methodCodeFragmentIsCaptured() {
        assertEquals("public Helper Save(string message)",
                model.getComponent("Demo.App.User.Save(string)").get().codeFragment());
    }

    @Test
    public void capitalizedPropertyReceiverIsNotTreatedAsTypeReference() {
        assertTrue(model.getComponent("Demo.App.User.User(Repo)").get()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE).stream()
                .noneMatch(ref -> ref.invokedComponent().equals("Repo")));
    }
}
