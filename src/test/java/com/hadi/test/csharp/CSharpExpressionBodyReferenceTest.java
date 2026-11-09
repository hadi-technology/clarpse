package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The expression of an expression-bodied method or constructor is read like a block body. */
public class CSharpExpressionBodyReferenceTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Targets.cs", """
                        namespace Demo.Core;
                        public class RouterCore { }
                        public class Product { }
                        public class Marker { }
                        public class InText { }
                        public class InNote { }
                        public class Held { }
                        public static class AppBuilder { public static int Use(int b) => b; }
                        public static class Helper { public static T Make<T>() => default; }
                        """),
                new ProjectFile("/Uses.cs", """
                        using Demo.Core;
                        namespace Demo.App;
                        internal static class Probe
                        {
                            internal static System.Type Marker() => typeof(RouterCore);
                            internal static int Apply(int b) => AppBuilder.Use(b);
                            internal static object Build() => new Product();
                            internal static object Generic() => Helper.Make<Held>();
                            internal static string InString() => "typeof(InText) AppBuilder.Use(1)";
                            // typeof(InNote)
                            internal static int InComment() => 1 /* typeof(InNote) */;
                        }
                        internal class WithConstructor
                        {
                            private readonly object value;
                            internal WithConstructor() => value = typeof(Marker);
                        }
                        """)).model();
    }

    private static boolean references(final String from, final String to) {
        return model.copyOfComponent(from).orElseThrow(() -> new AssertionError("no component " + from))
                .references().stream().map(ComponentReference::invokedComponent).anyMatch(to::equals);
    }

    @Test
    public void typeofInAnExpressionBodyIsReferenced() {
        assertTrue(references("Demo.App.Probe.Marker()", "Demo.Core.RouterCore"));
        assertTrue(references("Demo.App.Probe", "Demo.Core.RouterCore"));
    }

    @Test
    public void staticCallThroughATypeNameIsReferenced() {
        assertTrue(references("Demo.App.Probe.Apply(int)", "Demo.Core.AppBuilder"));
    }

    @Test
    public void newExpressionIsReferenced() {
        assertTrue(references("Demo.App.Probe.Build()", "Demo.Core.Product"));
    }

    @Test
    public void typeArgumentOfAGenericCallIsReferenced() {
        assertTrue(references("Demo.App.Probe.Generic()", "Demo.Core.Held"));
        assertTrue(references("Demo.App.Probe.Generic()", "Demo.Core.Helper"));
    }

    @Test
    public void constructorExpressionBodyIsRead() {
        assertTrue(references("Demo.App.WithConstructor.WithConstructor()", "Demo.Core.Marker"));
    }

    @Test
    public void textInAStringOrCommentIsNotRead() {
        assertFalse(references("Demo.App.Probe.InString()", "Demo.Core.InText"));
        assertFalse(references("Demo.App.Probe.InString()", "Demo.Core.AppBuilder"));
        assertFalse(references("Demo.App.Probe.InComment()", "Demo.Core.InNote"));
        assertFalse(references("Demo.App.Probe", "Demo.Core.InNote"));
        assertFalse(references("Demo.App.Probe", "Demo.Core.InText"));
    }

    @Test
    public void signatureIsNotTreatedAsBody() {
        assertFalse(references("Demo.App.Probe.Apply(int)", "Demo.Core.RouterCore"));
    }
}
