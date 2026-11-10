package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.ResolutionKind;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * References that follow the rules the other languages already keep: a name binds to a type only
 * where the language's scoping lets it, a namespace is never a type, and what a construct names is
 * recorded once it is provable.
 */
public class CSharpCrossLanguageParityTest {

    private static OOPSourceCodeModel model(final ProjectFile... files) throws Exception {
        return CSharpTestUtil.compileInline(files).model();
    }

    private static Set<ComponentReference> references(final OOPSourceCodeModel model, final String component) {
        final Component found = model.copyOfComponent(component).orElseThrow(
                () -> new AssertionError("no component " + component + " in "
                        + model.components().map(Component::uniqueName).collect(Collectors.toList())));
        return found.references();
    }

    private static boolean references(final OOPSourceCodeModel model, final String from, final String to) {
        return references(model, from).stream().anyMatch(r -> to.equals(r.invokedComponent()));
    }

    private static ResolutionKind kindOf(final OOPSourceCodeModel model, final String from, final String to) {
        return references(model, from).stream().filter(r -> to.equals(r.invokedComponent()))
                .map(ComponentReference::resolutionKind).findFirst()
                .orElseThrow(() -> new AssertionError(from + " has no reference to " + to));
    }

    // ---- an inherited member is not a type ----

    private static final ProjectFile USER_TYPE = new ProjectFile("/User.cs",
            "namespace App { public class User { public static object FindFirst(string s) { return null; } } }");

    /**
     * {@code User.FindFirst()} in a controller is the inherited {@code ControllerBase.User} property.
     * Nothing in the parse shows whether the unparsed base declares it, so the bare name binds to no type.
     */
    @Test
    public void aBareReceiverInAClassWithAnUnparsedBaseIsNotReadAsAType() throws Exception {
        final OOPSourceCodeModel model = model(USER_TYPE, new ProjectFile("/C.cs", """
                using Microsoft.AspNetCore.Mvc;
                namespace App {
                  public class UsersController : ControllerBase {
                    public object Get() { return User.FindFirst("sub"); }
                  }
                }
                """));
        assertFalse(references(model, "App.UsersController.Get()", "App.User"));
        assertFalse(references(model, "App.UsersController", "App.User"));
    }

    /** The unparsed base may be several steps up: the guard reads the whole ancestry. */
    @Test
    public void anUnparsedBaseHigherUpTheAncestryAlsoGuards() throws Exception {
        final OOPSourceCodeModel model = model(USER_TYPE,
                new ProjectFile("/B.cs", "namespace App { public class Middle : Framework.Base { } }"),
                new ProjectFile("/C.cs", """
                        namespace App {
                          public class Leaf : Middle {
                            public object Get() { return User.FindFirst("sub"); }
                          }
                        }
                        """));
        assertFalse(references(model, "App.Leaf.Get()", "App.User"));
    }

    /** A type nested in the class hides an inherited member of its name, so the guard has nothing to protect. */
    @Test
    public void aNestedTypeOfTheClassIsStillRead() throws Exception {
        final OOPSourceCodeModel model = model(new ProjectFile("/C.cs", """
                namespace App {
                  public class Holder : Framework.Base {
                    public class Helper { public static void Go() { } }
                    public void Run() { Helper.Go(); }
                  }
                }
                """));
        assertTrue(references(model, "App.Holder.Run()", "App.Holder.Helper"));
    }

    /** A receiver written with its namespace, or through an alias, names a type whatever the base declares. */
    @Test
    public void aQualifiedOrAliasedReceiverIsStillRead() throws Exception {
        final OOPSourceCodeModel model = model(USER_TYPE, new ProjectFile("/C.cs", """
                using Account = App.User;
                namespace Web {
                  public class UsersController : Microsoft.AspNetCore.Mvc.ControllerBase {
                    public object Qualified() { return App.User.FindFirst("sub"); }
                    public object Aliased() { return Account.FindFirst("sub"); }
                  }
                }
                """));
        assertTrue(references(model, "Web.UsersController.Qualified()", "App.User"));
        assertTrue(references(model, "Web.UsersController.Aliased()", "App.User"));
    }

    /** With every ancestor parsed, the members a class can have are known, and a static call resolves. */
    @Test
    public void aClassWithAFullyParsedAncestryStillResolvesStaticCalls() throws Exception {
        final OOPSourceCodeModel model = model(USER_TYPE,
                new ProjectFile("/B.cs", "namespace App { public class Base { } public interface IThing { } }"),
                new ProjectFile("/C.cs", """
                        namespace App {
                          public class Service : Base, IThing {
                            public object Get() { return User.FindFirst("sub"); }
                          }
                        }
                        """));
        assertEquals(ResolutionKind.EXACT, kindOf(model, "App.Service.Get()", "App.User"));
    }

    // ---- a namespace is never a type ----

    private static final ProjectFile LIB = new ProjectFile("/Lib.cs", """
            namespace App.Lib {
              public static class Util { public static void Run() { } }
              public class Thing { }
            }
            """);

    private static boolean mentions(final OOPSourceCodeModel model, final String from, final String name) {
        return references(model, from).stream().anyMatch(r -> r.invokedComponent().equals(name));
    }

    /** {@code new App.Lib.Thing()} constructs the type; {@code App.Lib} is the namespace it is in. */
    @Test
    public void aNamespaceBeforeAConstructedTypeIsNotReferenced() throws Exception {
        final OOPSourceCodeModel model = model(LIB, new ProjectFile("/Use.cs", """
                namespace Web {
                  public class Use { public object Make() { return new App.Lib.Thing(); } }
                }
                """));
        assertTrue(references(model, "Web.Use.Make()", "App.Lib.Thing"));
        assertFalse(mentions(model, "Web.Use.Make()", "App.Lib"));
        assertFalse(mentions(model, "Web.Use", "App.Lib"));
    }

    /** A namespace the repository does not hold is just as much a namespace when a using names it. */
    @Test
    public void anExternalNamespaceBeforeAConstructedTypeIsNotReferenced() throws Exception {
        final OOPSourceCodeModel model = model(new ProjectFile("/A.cs", """
                using Ext.Http;
                namespace App {
                  public class A { public object G() { return new Ext.Http.Client(); } }
                }
                """));
        assertTrue(mentions(model, "App.A.G()", "Ext.Http.Client"));
        assertFalse(mentions(model, "App.A.G()", "Ext.Http"));
        assertFalse(mentions(model, "App.A", "Ext.Http"));
    }

    /** The namespace before a declared type is no receiver either, with or without a using. */
    @Test
    public void aNamespaceBeforeADeclaredTypeIsNotReferenced() throws Exception {
        final OOPSourceCodeModel model = model(LIB, new ProjectFile("/Use.cs", """
                namespace Web {
                  public class Use {
                    public void F() { App.Lib.Thing t = null; Vendor.Pkg.Widget w = null; }
                  }
                }
                """));
        assertTrue(references(model, "Web.Use.F()", "App.Lib.Thing"));
        assertFalse(mentions(model, "Web.Use.F()", "App.Lib"));
        assertFalse(mentions(model, "Web.Use.F()", "Vendor.Pkg"));
    }

    /** A static call through a qualified type name still names the type, in the repository or not. */
    @Test
    public void aQualifiedStaticCallStillNamesItsType() throws Exception {
        final OOPSourceCodeModel model = model(LIB, new ProjectFile("/Use.cs", """
                namespace Web {
                  public class Use {
                    public void Call() { App.Lib.Util.Run(); }
                    public void Ext() { Vendor.Pkg.Tool.Go(); }
                  }
                }
                """));
        assertEquals(ResolutionKind.EXACT, kindOf(model, "Web.Use.Call()", "App.Lib.Util"));
        assertEquals(ResolutionKind.UNRESOLVED, kindOf(model, "Web.Use.Ext()", "Vendor.Pkg.Tool"));
        assertFalse(mentions(model, "Web.Use.Call()", "App.Lib"));
    }

    // ---- an extension class depends on the type it extends ----

    /**
     * {@code E} references {@code T} through the {@code this} parameter. {@code T} does not reference
     * {@code E}: that would close a cycle between the two in every dependency graph.
     */
    @Test
    public void anExtendedTypeDoesNotReferenceItsExtensionClass() throws Exception {
        final OOPSourceCodeModel model = model(new ProjectFile("/E.cs", """
                namespace App {
                  public class T { }
                  public static class E { public static void M(this T t) { } }
                }
                """));
        assertTrue(references(model, "App.E", "App.T"));
        assertTrue(references(model, "App.E.M(T)", "App.T"));
        assertFalse(references(model, "App.T").stream()
                .anyMatch(r -> r.invokedComponent().startsWith("App.E")));
    }

    /** A caller of the extension method still depends on the class that declares it. */
    @Test
    public void aCallerOfAnExtensionMethodStillReferencesItsClass() throws Exception {
        final OOPSourceCodeModel model = model(new ProjectFile("/E.cs", """
                namespace App {
                  public class T { }
                  public static class E { public static void M(this T t) { } }
                  public class Caller { public void Go(T t) { t.M(); } }
                }
                """));
        assertTrue(references(model, "App.Caller.Go(T)", "App.E"));
        assertFalse(references(model, "App.T").stream()
                .anyMatch(r -> r.invokedComponent().startsWith("App.E")));
    }
}
