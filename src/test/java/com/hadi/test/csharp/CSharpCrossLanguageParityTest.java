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

    // ---- ambiguity follows visibility ----

    private static ProjectFile dup(final String namespace) {
        return new ProjectFile("/" + namespace + "/Dup.cs",
                "namespace " + namespace + " { public class Dup { } }");
    }

    private static ProjectFile holder(final String usings) {
        return new ProjectFile("/Holder.cs", usings + "\nnamespace App { public class Holder { public Dup d; } }");
    }

    /** Two usings that both supply the name leave it ambiguous: no binding, the name as written. */
    @Test
    public void twoUsingsSupplyingTheNameAreAmbiguous() throws Exception {
        final OOPSourceCodeModel model = model(dup("One.Place"), dup("Two.Place"),
                holder("using One.Place; using Two.Place;"));
        assertEquals(ResolutionKind.AMBIGUOUS, kindOf(model, "App.Holder.d", "Dup"));
        assertFalse(references(model, "App.Holder.d", "One.Place.Dup"));
        assertFalse(references(model, "App.Holder.d", "Two.Place.Dup"));
    }

    /** One using supplying the name binds it, however many other namespaces declare one. */
    @Test
    public void oneUsingSupplyingTheNameIsExact() throws Exception {
        final OOPSourceCodeModel model = model(dup("One.Place"), dup("Two.Place"), dup("Three.Place"),
                holder("using Two.Place;"));
        assertEquals(ResolutionKind.EXACT, kindOf(model, "App.Holder.d", "Two.Place.Dup"));
        assertFalse(references(model, "App.Holder.d", "Dup"));
    }

    /** Several types of the name, none visible: unresolved, as written. */
    @Test
    public void severalTypesOfTheNameNoneVisibleAreUnresolved() throws Exception {
        final OOPSourceCodeModel model = model(dup("One.Place"), dup("Two.Place"), holder("using System;"));
        assertEquals(ResolutionKind.UNRESOLVED, kindOf(model, "App.Holder.d", "Dup"));
    }

    /** A type of the file's own namespace is found before any using is consulted. */
    @Test
    public void aTypeOfTheOwnNamespaceOutranksTwoUsings() throws Exception {
        final OOPSourceCodeModel model = model(dup("One.Place"), dup("Two.Place"), dup("App"),
                holder("using One.Place; using Two.Place;"));
        assertEquals(ResolutionKind.EXACT, kindOf(model, "App.Holder.d", "App.Dup"));
    }

    /** A repository type called {@code Decimal} is not the keyword type, and stays visible by scope. */
    @Test
    public void aRepositoryTypeNamedLikeAFrameworkTypeIsNotTheFrameworkType() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/Decimal.cs", "namespace App { public class Decimal { } }"),
                new ProjectFile("/Use.cs", "namespace App { public class Use { public Decimal a; public decimal b; } }"));
        assertEquals(ResolutionKind.EXACT, kindOf(model, "App.Use.a", "App.Decimal"));
        assertEquals(ResolutionKind.EXACT, kindOf(model, "App.Use.b", "System.Decimal"));
    }

    // ---- type tests, casts and patterns name a type ----

    private static final ProjectFile PATTERN_TYPES = new ProjectFile("/Types.cs", """
            namespace App {
              public class Tst { }
              public class Asx { }
              public class Cst { }
              public class Nmo { }
              public class Dft { }
              public class Pat { }
              public class Arm { }
              public class Gen { }
              public class Neg { }
              public enum Color { Red }
            }
            """);

    private static OOPSourceCodeModel withBody(final String members) throws Exception {
        return model(PATTERN_TYPES, new ProjectFile("/Use.cs",
                "namespace App { public class Use { " + members + " } }"));
    }

    @Test
    public void anIsTestNamesItsType() throws Exception {
        final OOPSourceCodeModel model = withBody("public bool A(object o) { return o is Tst; }"
                + "public bool B(object o) { return o is Pat p && p != null; }"
                + "public bool C(object o) { return o is not Neg; }");
        assertTrue(references(model, "App.Use.A(object)", "App.Tst"));
        assertTrue(references(model, "App.Use.B(object)", "App.Pat"));
        assertTrue(references(model, "App.Use.C(object)", "App.Neg"));
    }

    @Test
    public void anAsCastNamesItsType() throws Exception {
        final OOPSourceCodeModel model = withBody("public object A(object o) { return o as Asx; }");
        assertTrue(references(model, "App.Use.A(object)", "App.Asx"));
    }

    @Test
    public void aCastNamesItsType() throws Exception {
        final OOPSourceCodeModel model = withBody("public object A(object o) { return (Cst)o; }"
                + "public object B(object o) { var x = (App.Tst) o; return x; }"
                + "public object C(object o) { return (System.Collections.Generic.List<Gen>)o; }");
        assertTrue(references(model, "App.Use.A(object)", "App.Cst"));
        assertTrue(references(model, "App.Use.B(object)", "App.Tst"));
        assertTrue(references(model, "App.Use.C(object)", "App.Gen"));
    }

    @Test
    public void nameofAndDefaultNameTheirType() throws Exception {
        final OOPSourceCodeModel model = withBody("public string A() { return nameof(Nmo); }"
                + "public object B() { return default(Dft); }");
        assertTrue(references(model, "App.Use.A()", "App.Nmo"));
        assertTrue(references(model, "App.Use.B()", "App.Dft"));
    }

    @Test
    public void caseAndSwitchArmPatternsNameTheirType() throws Exception {
        final OOPSourceCodeModel model = withBody(
                "public int A(object o) { switch (o) { case Pat p: return 1; default: return 0; } }"
                + "public int B(object o) => o switch { Arm a => 1, _ => 0 };"
                + "public int C(object o) { switch (o) { case Tst: return 1; default: return 0; } }");
        assertTrue(references(model, "App.Use.A(object)", "App.Pat"));
        assertTrue(references(model, "App.Use.B(object)", "App.Arm"));
        assertTrue(references(model, "App.Use.C(object)", "App.Tst"));
    }

    /** A pattern naming an enum constant or a null names no type. */
    @Test
    public void aConstantPatternNamesNoType() throws Exception {
        final OOPSourceCodeModel model = withBody("public bool A(object o) { return o is null || o is Color.Red; }");
        assertFalse(mentions(model, "App.Use.A(object)", "App.Color.Red"));
        assertFalse(mentions(model, "App.Use.A(object)", "Color.Red"));
        assertFalse(mentions(model, "App.Use.A(object)", "null"));
    }

    /** {@code (a) - b} is a subtraction and {@code (flag) return} a condition: a parenthesised value is no cast. */
    @Test
    public void aParenthesisedValueIsNoCast() throws Exception {
        final OOPSourceCodeModel model = withBody(
                "public object A(int b) { return (Cst) - b; }"
                + "public object B(int b) { if (Tst) return null; return (Nmo) + b; }"
                + "public object C(int b) { return (Dft) ? 1 : 2; }"
                + "public object D(object o) { return M(Pat) as object; }"
                + "public object M(object o) { return o; }");
        assertFalse(mentions(model, "App.Use.A(int)", "App.Cst"));
        assertFalse(mentions(model, "App.Use.B(int)", "App.Tst"));
        assertFalse(mentions(model, "App.Use.B(int)", "App.Nmo"));
        assertFalse(mentions(model, "App.Use.C(int)", "App.Dft"));
        assertFalse(mentions(model, "App.Use.D(object)", "App.Pat"));
    }

    /** {@code (Foo)(bar)} is a cast of {@code bar} when {@code Foo} is a type, and a call when it is a variable. */
    @Test
    public void aParenthesisedNameBeforeAParenthesisIsACastOnlyForATypeInScope() throws Exception {
        final OOPSourceCodeModel model = withBody(
                "public object A(object bar) { return (Cst)(bar); }"
                + "public object B(object bar, System.Func<object, object> Tst) { return (Tst)(bar); }"
                + "public object C(object bar) { var Pat = new System.Func<object, object>(x => x); return (Pat)(bar); }");
        assertTrue(references(model, "App.Use.A(object)", "App.Cst"));
        assertFalse(mentions(model, "App.Use.B(object, Func<object, object>)", "App.Tst"));
        assertFalse(mentions(model, "App.Use.C(object)", "App.Pat"));
    }

    /** A name that resolves to no type of the scope is not recorded when it might be a value. */
    @Test
    public void aCastOrPatternNameThatIsNoVisibleTypeIsNotRecorded() throws Exception {
        final OOPSourceCodeModel model = withBody("public object A(object o) { return (Unknown)o; }"
                + "public bool B(object o) { return o is Missing; }"
                + "public string C() { return nameof(Absent); }");
        assertFalse(mentions(model, "App.Use.A(object)", "Unknown"));
        assertFalse(mentions(model, "App.Use.B(object)", "Missing"));
        assertFalse(mentions(model, "App.Use.C()", "Absent"));
    }

    /** {@code nameof(x)} of a parameter or a local names that variable, not a type of the same name. */
    @Test
    public void nameofAVariableIsNotAType() throws Exception {
        final OOPSourceCodeModel model = withBody(
                "public string A(object Tst) { return nameof(Tst); }"
                + "public string B() { var Cst = 1; return nameof(Cst) + (Cst); }");
        assertFalse(mentions(model, "App.Use.A(object)", "App.Tst"));
        assertFalse(mentions(model, "App.Use.B()", "App.Cst"));
    }

    /** {@code nameof(User)} in a controller may name the inherited property: no bare candidate is read as a type. */
    @Test
    public void aCandidateNameInAClassWithAnUnparsedBaseIsNotReadAsAType() throws Exception {
        final OOPSourceCodeModel model = model(USER_TYPE, new ProjectFile("/C.cs", """
                namespace App {
                  public class UsersController : Framework.ControllerBase {
                    public string Get(object o) { return nameof(User) + (o is User ? "a" : "b"); }
                    public object Cast(object o) { return (User)o; }
                  }
                }
                """));
        assertFalse(mentions(model, "App.UsersController.Get(object)", "App.User"));
        assertTrue("a cast cannot be a property read, so it still names the type",
                references(model, "App.UsersController.Cast(object)", "App.User"));
    }

    // ---- [Mark] means MarkAttribute ----

    private static OOPSourceCodeModel attributed(final String types, final String use) throws Exception {
        return model(new ProjectFile("/Attrs.cs", "namespace App { " + types + " }"),
                new ProjectFile("/Use.cs", "namespace App { " + use + " }"));
    }

    /** {@code [Mark]} binds to {@code MarkAttribute} when no type called {@code Mark} is visible. */
    @Test
    public void anAttributeNameResolvesToItsAttributeSuffixedType() throws Exception {
        final OOPSourceCodeModel model = attributed("public class MarkAttribute : System.Attribute { }",
                "[Mark] public class A { [Mark] public void M() { } }");
        assertEquals(ResolutionKind.EXACT, kindOf(model, "App.A", "App.MarkAttribute"));
        assertEquals(ResolutionKind.EXACT, kindOf(model, "App.A.M()", "App.MarkAttribute"));
        assertFalse(mentions(model, "App.A", "Mark"));
    }

    /** The name with the suffix written, and a type with the plain name, are read as before. */
    @Test
    public void anAttributeWrittenWithItsSuffixOrOnlyWithThePlainNameIsUnchanged() throws Exception {
        final OOPSourceCodeModel model = attributed(
                "public class TagAttribute : System.Attribute { } public class Plain : System.Attribute { }",
                "[TagAttribute] public class A { } [Plain] public class B { }");
        assertEquals(ResolutionKind.EXACT, kindOf(model, "App.A", "App.TagAttribute"));
        assertEquals(ResolutionKind.EXACT, kindOf(model, "App.B", "App.Plain"));
    }

    /** With both {@code Mark} and {@code MarkAttribute} visible the name is ambiguous, and binds to neither. */
    @Test
    public void anAttributeNameBothTypesCarryBindsToNeither() throws Exception {
        final OOPSourceCodeModel model = attributed(
                "public class MarkAttribute : System.Attribute { } public class Mark : System.Attribute { }",
                "[Mark] public class A { }");
        assertFalse(mentions(model, "App.A", "App.MarkAttribute"));
        assertFalse(mentions(model, "App.A", "App.Mark"));
    }

    /** A suffixed type in a namespace the file cannot see is not a candidate. */
    @Test
    public void anAttributeSuffixedTypeOutOfScopeIsNotBound() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/Attrs.cs", "namespace Elsewhere { public class MarkAttribute : System.Attribute { } }"),
                new ProjectFile("/Use.cs", "namespace App { [Mark] public class A { } }"));
        assertFalse(mentions(model, "App.A", "Elsewhere.MarkAttribute"));
        assertEquals(ResolutionKind.UNRESOLVED, kindOf(model, "App.A", "Mark"));
    }

    // ---- typeof inside attribute arguments ----

    private static final ProjectFile ATTRIBUTE_TARGETS = new ProjectFile("/Targets.cs", """
            namespace App {
              public class FooAttribute : System.Attribute {
                public FooAttribute(System.Type t) { }
                public System.Type Kind { get; set; }
              }
              public class Bar { }
              public class Baz { }
              public class Qux { }
            }
            """);

    @Test
    public void aTypeofInAClassAttributeIsReferencedFromTheClass() throws Exception {
        final OOPSourceCodeModel model = model(ATTRIBUTE_TARGETS, new ProjectFile("/Use.cs", """
                namespace App {
                  [Foo(typeof(Bar), Kind = typeof(Baz))]
                  public class Use { }
                }
                """));
        assertTrue(references(model, "App.Use", "App.Bar"));
        assertTrue(references(model, "App.Use", "App.Baz"));
        assertTrue(references(model, "App.Use", "App.FooAttribute"));
    }

    @Test
    public void aTypeofInAMethodAttributeIsReferencedFromTheMethod() throws Exception {
        final OOPSourceCodeModel model = model(ATTRIBUTE_TARGETS, new ProjectFile("/Use.cs", """
                namespace App {
                  public class Use {
                    [Foo(typeof(Qux))]
                    public void Run() { }
                  }
                }
                """));
        assertTrue(references(model, "App.Use.Run()", "App.Qux"));
        assertTrue(references(model, "App.Use", "App.Qux"));
    }

    /** A type named only in a string argument, or a typeof out of scope, is not a repository reference. */
    @Test
    public void aStringArgumentNamesNoType() throws Exception {
        final OOPSourceCodeModel model = model(ATTRIBUTE_TARGETS, new ProjectFile("/Use.cs", """
                namespace App {
                  [Foo(typeof(Bar), Kind = null, Name = "typeof(Baz)")]
                  public class Use { }
                }
                """));
        assertTrue(references(model, "App.Use", "App.Bar"));
        assertFalse(mentions(model, "App.Use", "App.Baz"));
    }
}
