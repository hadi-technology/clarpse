package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A receiver name that the member's body may declare again -- a local function's parameter, a pattern
 * variable, a loop or an out variable -- may not be the parameter or local the member declares.
 */
public class CSharpRedeclaredReceiverNameTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Types.cs", """
                        namespace App {
                          public class Foo { public Segment Inner; }
                          public class Bar { }
                          public class Segment { }
                          public static class FooExt { public static void Ext(this Foo f) { } }
                          public static class BarExt { public static void Ext(this Bar b) { } }
                        }
                        """),
                new ProjectFile("/Uses.cs", """
                        namespace App {
                          public class Uses
                          {
                            public void Plain(Foo x) { x.Ext(); }
                            public void LocalFunction(Foo x) { void L(Bar x) { x.Ext(); } }
                            public void Pattern(Foo x, object o) { void L() { if (o is Bar x) { x.Ext(); } } }
                            public void OutVariable(Foo x) { void L() { Try(out Bar x); x.Ext(); } }
                            public void Loop(Foo x, Bar[] items) { void L() { foreach (Bar x in items) { x.Ext(); } } }
                            public void LocalOnly() { Foo x = null; x.Ext(); }
                            public void LocalRedeclaredInFunction() { Foo x = null; void L(Bar x) { x.Ext(); } }
                            public void Reads(Foo x) { var s = x.Inner; }
                            public void ReadsRedeclared(Foo x) { void L(Bar x) { var s = x.Inner; } }
                            private void Try(out Bar b) { b = null; }
                          }
                        }
                        """)).model();
    }

    private static boolean references(final String from, final String to) {
        return model.copyOfComponent(from).orElseThrow(() -> new AssertionError("no component " + from))
                .references().stream().map(ComponentReference::invokedComponent).anyMatch(to::equals);
    }

    @Test
    public void parameterAndLocalDeclaredOnceAreStillReceivers() {
        assertTrue(references("App.Uses.Plain(Foo)", "App.FooExt"));
        assertTrue(references("App.Uses.LocalOnly()", "App.FooExt"));
    }

    @Test
    public void localFunctionParameterIsNotTheOuterParameter() {
        assertFalse(references("App.Uses.LocalFunction(Foo)", "App.FooExt"));
        assertFalse(references("App.Uses.LocalRedeclaredInFunction()", "App.FooExt"));
    }

    @Test
    public void patternOutAndLoopVariablesAreNotTheOuterParameter() {
        assertFalse(references("App.Uses.Pattern(Foo, object)", "App.FooExt"));
        assertFalse(references("App.Uses.OutVariable(Foo)", "App.FooExt"));
        assertFalse(references("App.Uses.Loop(Foo, Bar[])", "App.FooExt"));
    }

    @Test
    public void memberTypeInferenceHonoursTheSameRule() {
        assertTrue(references("App.Uses.Reads(Foo)", "App.Segment"));
        assertFalse(references("App.Uses.ReadsRedeclared(Foo)", "App.Segment"));
    }
}
