package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * An applicable instance method always binds before an extension method, so a call is credited to an
 * extension class only when the receiver's own members are fully known: its type and every ancestor of
 * it are parsed. A receiver of a type the parse does not hold is matched by its simple name alone.
 */
public class CSharpExtensionAncestryTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Types.cs", """
                        namespace App {
                          public class Plain { }
                          public class Mid : Plain { }
                          public class Foo : SomeExternalBase { }
                          public interface IKnown { }
                          public class Mixed : IKnown, IExternal { }
                          public static class CloneExt { public static T Clone<T>(this T s) { return s; } }
                          public static class FooExt { public static void Ext(this Foo f) { } }
                          public static class MixedExt { public static void Tag(this IKnown k) { } }
                          public static class StringExt { public static bool IsValidEmailAddress(this string s) { return true; } }
                        }
                        """),
                new ProjectFile("/Uses.cs", """
                        namespace App {
                          public class Uses
                          {
                            public object Copy(byte[] data) => data.Clone();
                            public void Text(string text) { text.Clone(); }
                            public void OnPlain(Plain p) { p.Clone(); }
                            public void OnMid(Mid m) { m.Clone(); }
                            public void OnForeignBase(Foo f) { f.Clone(); }
                            public void OnForeignBaseExact(Foo f) { f.Ext(); }
                            public void OnMixed(Mixed m) { m.Tag(); }
                            public void Email(string email) { email.IsValidEmailAddress(); }
                          }
                        }
                        """)).model();
    }

    private static boolean references(final String from, final String to) {
        return model.copyOfComponent(from).orElseThrow(() -> new AssertionError("no component " + from))
                .references().stream().map(ComponentReference::invokedComponent).anyMatch(to::equals);
    }

    @Test
    public void genericExtensionOnAFullyParsedReceiverIsCredited() {
        assertTrue(references("App.Uses.OnPlain(Plain)", "App.CloneExt"));
        assertTrue(references("App.Uses.OnMid(Mid)", "App.CloneExt"));
    }

    @Test
    public void genericExtensionOnAReceiverTheParseDoesNotHoldIsNotCredited() {
        assertFalse(references("App.Uses.Text(string)", "App.CloneExt"));
    }

    @Test
    public void genericExtensionOnAParsedTypeWithAnUnparsedAncestorIsNotCredited() {
        assertFalse(references("App.Uses.OnForeignBase(Foo)", "App.CloneExt"));
    }

    @Test
    public void exactExtensionOnAParsedTypeWithAnUnparsedAncestorIsNotCredited() {
        assertFalse(references("App.Uses.OnForeignBaseExact(Foo)", "App.FooExt"));
        assertFalse(references("App.Uses.OnMixed(Mixed)", "App.MixedExt"));
    }

    @Test
    public void unparsedReceiverIsStillMatchedByItsExactSimpleName() {
        assertTrue(references("App.Uses.Email(string)", "App.StringExt"));
    }
}
