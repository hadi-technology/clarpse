package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A call of an extension method references the one static class that can declare it. */
public class CSharpExtensionMethodCallReferenceTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Hosting.cs", """
                        namespace Demo.Hosting;
                        public interface IBuilder { }
                        public class Builder : IBuilder { }
                        public static class HostExtensions
                        {
                            public static T AddWidgets<T>(this T b, string name) where T : IBuilder => b;
                            public static void AddPair(this IBuilder b, string a, string c) { }
                        }
                        public static class SpareExtensions
                        {
                            public static void AddPair(this IBuilder b, string a) { }
                        }
                        public class WithOwn : IBuilder { public void AddWidgets(string name) { } }
                        public class Unrelated { }
                        public static class UnrelatedExtensions
                        {
                            public static void Shout(this Unrelated u) { }
                        }
                        """),
                new ProjectFile("/Other.cs", """
                        namespace Demo.Elsewhere;
                        public static class TwinOne { public static void Twin(this Demo.Hosting.IBuilder b) { } }
                        public static class TwinTwo { public static void Twin(this Demo.Hosting.IBuilder b) { } }
                        public static class Hidden { public static void Secret(this Demo.Hosting.IBuilder b) { } }
                        """),
                new ProjectFile("/Use.cs", """
                        using Demo.Hosting;
                        using Demo.Elsewhere;
                        namespace Demo.App;
                        internal static class Use
                        {
                            internal static void Block(IBuilder builder) { builder.AddWidgets("x"); }
                            internal static void Expression(IBuilder builder) => builder.AddWidgets("x");
                            internal static void Local() { var builder = new Builder(); builder.AddWidgets("y"); }
                            internal static void Static(IBuilder builder) => HostExtensions.AddWidgets(builder, "x");
                            internal static void Twins(IBuilder builder) { builder.Twin(); }
                            internal static void Own(WithOwn own) { own.AddWidgets("x"); }
                            internal static void Arity(IBuilder builder) { builder.AddPair("a"); }
                            internal static void Wrong(Unrelated u) { u.AddPair("a", "b"); }
                            internal static void InString(IBuilder builder) { var s = "builder.AddWidgets(1)"; }
                            internal static void Chain() { new Builder().AddWidgets("x"); }
                            internal static void Lambda(System.Action<IBuilder> a) { a(null); System.Action<IBuilder> f = (IBuilder b) => b.AddWidgets("x"); }
                        }
                        """),
                new ProjectFile("/NotImported.cs", """
                        namespace Demo.Far;
                        internal static class NotImported
                        {
                            internal static void Run() { Demo.Hosting.IBuilder builder = null; builder.AddWidgets("x"); }
                        }
                        """),
                new ProjectFile("/Receivers.cs", """
                        using System.Text;
                        namespace Demo.Hosting;
                        public class Gadget { }
                        public class Bar { }
                        public class Holder { public Gadget Inner; }
                        public static class GadgetExtensions { public static void Tune(this Gadget g) { } }
                        public static class BarExtensions { public static void OnlyBar(this Bar b) { } }
                        public static class TextExtensions { public static void Squeeze(this StringBuilder sb) { } }
                        internal static class Receivers
                        {
                            internal static Gadget Make() => null;
                            internal static void Same(Gadget g) { g.Tune(); }
                            internal static void SameExternal(StringBuilder sb) { sb.Squeeze(); }
                            internal static void CallResult() { Make().Tune(); }
                            internal static void VarFromCall() { var g = Make(); g.Tune(); }
                            internal static void VarFromConditional(bool flag) { var g = flag ? new Gadget() : null; g.Tune(); }
                            internal static void Chained(Holder h) { h.Inner.Tune(); }
                            internal static void ExternalParameter(Foo f) { f.OnlyBar(); }
                        }
                        """),
                new ProjectFile("/Sibling.cs", """
                        namespace Demo.Hosting;
                        internal static class Sibling
                        {
                            internal static void Run(IBuilder builder) { builder.Secret2(); builder.AddWidgets("x"); }
                        }
                        """)).model();
    }

    private static boolean references(final String from, final String to) {
        return model.copyOfComponent(from).orElseThrow(() -> new AssertionError("no component " + from))
                .references().stream().map(ComponentReference::invokedComponent).anyMatch(to::equals);
    }

    private static final String HOST = "Demo.Hosting.HostExtensions";

    @Test
    public void blockBodiedCallReferencesTheDeclaringClass() {
        assertTrue(references("Demo.App.Use.Block(IBuilder)", HOST));
    }

    @Test
    public void expressionBodiedCallReferencesTheDeclaringClass() {
        assertTrue(references("Demo.App.Use.Expression(IBuilder)", HOST));
    }

    @Test
    public void localOfAnInferredTypeIsNotAnObstacle() {
        assertTrue(references("Demo.App.Use.Local()", HOST));
    }

    @Test
    public void staticFormStillReferencesTheClass() {
        assertTrue(references("Demo.App.Use.Static(IBuilder)", HOST));
    }

    @Test
    public void twoClassesDeclaringTheNameGiveNoEdge() {
        assertFalse(references("Demo.App.Use.Twins(IBuilder)", "Demo.Elsewhere.TwinOne"));
        assertFalse(references("Demo.App.Use.Twins(IBuilder)", "Demo.Elsewhere.TwinTwo"));
    }

    @Test
    public void instanceMethodOfTheSameNameWins() {
        assertFalse(references("Demo.App.Use.Own(WithOwn)", HOST));
    }

    @Test
    public void argumentCountPicksTheClassThatCanTakeTheCall() {
        assertTrue(references("Demo.App.Use.Arity(IBuilder)", "Demo.Hosting.SpareExtensions"));
        assertFalse(references("Demo.App.Use.Arity(IBuilder)", HOST));
    }

    @Test
    public void receiverOfAnUnrelatedParsedTypeGivesNoEdge() {
        assertFalse(references("Demo.App.Use.Wrong(Unrelated)", HOST));
    }

    @Test
    public void callInAStringGivesNoEdge() {
        assertFalse(references("Demo.App.Use.InString(IBuilder)", HOST));
    }

    @Test
    public void receiverThatIsANewExpressionResultGivesNoEdge() {
        assertFalse(references("Demo.App.Use.Chain()", HOST));
    }

    @Test
    public void explicitlyTypedLambdaParameterIsAReceiver() {
        assertTrue(references("Demo.App.Use.Lambda(Action<IBuilder>)", HOST));
    }

    @Test
    public void classInANamespaceThatIsNotImportedGivesNoEdge() {
        assertFalse(references("Demo.Far.NotImported.Run()", HOST));
    }

    @Test
    public void classInTheSameNamespaceIsVisible() {
        assertTrue(references("Demo.Hosting.Sibling.Run(IBuilder)", HOST));
    }

    private static final String RECEIVERS = "Demo.Hosting.Receivers.";

    @Test
    public void parameterOfTheExtendedTypeReferencesTheDeclaringClass() {
        assertTrue(references(RECEIVERS + "Same(Gadget)", "Demo.Hosting.GadgetExtensions"));
    }

    @Test
    public void externalParameterOfTheExtendedTypeReferencesTheDeclaringClass() {
        assertTrue(references(RECEIVERS + "SameExternal(StringBuilder)", "Demo.Hosting.TextExtensions"));
    }

    @Test
    public void methodCallResultAsReceiverGivesNoEdge() {
        assertFalse(references(RECEIVERS + "CallResult()", "Demo.Hosting.GadgetExtensions"));
    }

    @Test
    public void varLocalInitialisedFromACallGivesNoEdge() {
        assertFalse(references(RECEIVERS + "VarFromCall()", "Demo.Hosting.GadgetExtensions"));
    }

    @Test
    public void varLocalInitialisedFromAConditionalGivesNoEdge() {
        assertFalse(references(RECEIVERS + "VarFromConditional(bool)", "Demo.Hosting.GadgetExtensions"));
    }

    @Test
    public void chainedMemberReceiverGivesNoEdge() {
        assertFalse(references(RECEIVERS + "Chained(Holder)", "Demo.Hosting.GadgetExtensions"));
    }

    @Test
    public void externalParameterWhoseTypeIsNotTheExtendedTypeGivesNoEdge() {
        assertFalse(references(RECEIVERS + "ExternalParameter(Foo)", "Demo.Hosting.BarExtensions"));
    }
}
