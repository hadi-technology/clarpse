package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A local declared with {@code var} and initialised from a member of another type has the type that
 * member is declared with, so the method that holds it depends on that type.
 */
public class CSharpInferredMemberTypeReferenceTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Types.cs", """
                        namespace App {
                          public class ReqBase { public int Timeout; }
                          public class Req : ReqBase { public Detail Info { get; set; } }
                          public class Detail { }
                          public class Resp { public void Add() { } }
                          public class Ctx : CtxBase { public Req Request; public Resp Response { get; } }
                          public class CtxBase { public Extra Inherited; public Extra Build(int n) { return null; } }
                          public class Extra { }
                          public class Maker { public Thing Make() { return null; } public Other Make(int n) { return null; } }
                          public class Thing { }
                          public class Other { }
                          public static class Registry { public static Stored Current; }
                          public class Stored { }
                        }
                        """),
                new ProjectFile("/Uses.cs", """
                        namespace App {
                          public class FieldRead { public void Apply(Ctx context) { var rq = context.Request; } }
                          public class NullConditional { public void Apply(Ctx context) { var rq = context?.Request; } }
                          public class PropertyRead { public void Apply(Ctx context) { var r = context.Response; } }
                          public class CallRead { public void Apply(Ctx context) { var e = context.Build(1); } }
                          public class InheritedRead { public void Apply(Ctx context) { var e = context.Inherited; } }
                          public class Chained { public void Apply(Ctx context) { var d = context.Request.Info; } }
                          public class ChainedLocals { public void Apply(Ctx context) { var rq = context.Request; var d = rq.Info; } }
                          public class FieldReceiver { private Ctx ctx; public void Apply() { var rq = ctx.Request; } }
                          public class ThisReceiver { private Ctx ctx; public void Apply() { var rq = this.ctx.Request; } }
                          public class StaticRead { public void Apply() { var s = Registry.Current; } }
                          public class AmbiguousOverload { public void Apply(Maker m) { var t = m.Make(); } }
                          public class UnknownReceiver { public void Apply(Elsewhere x) { var v = x.Member; } }
                          public class Shadowed {
                            public void A(Ctx context) { var rq = context.Request; }
                            public void B(Resp context) { var rq = context.Add(); }
                          }
                          public class Duplicated {
                            public void Apply(Ctx a) { { Ctx x = a; var v = x.Request; } { Resp x = null; var w = x.Response; } }
                          }
                          public class Typed { public void Apply(Ctx context) { Req rq = context.Request; } }
                        }
                        """)).model();
    }

    private static boolean references(final String from, final String to) {
        return model.copyOfComponent(from).orElseThrow(() -> new AssertionError("no component " + from))
                .references().stream().map(ComponentReference::invokedComponent).anyMatch(to::equals);
    }

    @Test
    public void fieldReadNamesTheFieldType() {
        assertTrue(references("App.FieldRead.Apply(Ctx)", "App.Req"));
        assertTrue(references("App.FieldRead.Apply(Ctx).rq", "App.Req"));
        assertTrue(references("App.FieldRead", "App.Req"));
    }

    @Test
    public void nullConditionalReadNamesTheFieldType() {
        assertTrue(references("App.NullConditional.Apply(Ctx)", "App.Req"));
    }

    @Test
    public void propertyReadNamesThePropertyType() {
        assertTrue(references("App.PropertyRead.Apply(Ctx)", "App.Resp"));
    }

    @Test
    public void methodCallNamesTheReturnType() {
        assertTrue(references("App.CallRead.Apply(Ctx)", "App.Extra"));
    }

    @Test
    public void memberInheritedFromAParsedBaseIsFound() {
        assertTrue(references("App.InheritedRead.Apply(Ctx)", "App.Extra"));
    }

    @Test
    public void chainsAndChainedLocalsAreFollowed() {
        assertTrue(references("App.Chained.Apply(Ctx)", "App.Detail"));
        assertTrue(references("App.ChainedLocals.Apply(Ctx)", "App.Detail"));
    }

    @Test
    public void fieldAndThisReceiversAreFollowed() {
        assertTrue(references("App.FieldReceiver.Apply()", "App.Req"));
        assertTrue(references("App.ThisReceiver.Apply()", "App.Req"));
    }

    @Test
    public void staticMemberOfATypeIsNotFollowed() {
        assertFalse(references("App.StaticRead.Apply()", "App.Stored"));
    }

    @Test
    public void overloadsWithDifferentReturnTypesAreNotGuessed() {
        assertFalse(references("App.AmbiguousOverload.Apply(Maker)", "App.Thing"));
        assertFalse(references("App.AmbiguousOverload.Apply(Maker)", "App.Other"));
    }

    @Test
    public void receiverOfAnUnknownTypeNamesNothing() {
        assertFalse(references("App.UnknownReceiver.Apply(Elsewhere)", "App.Req"));
    }

    @Test
    public void aNameDeclaredTwiceIsNotGuessed() {
        assertFalse(references("App.Duplicated.Apply(Ctx)", "App.Req"));
    }

    @Test
    public void methodsKeepTheirOwnParameters() {
        assertTrue(references("App.Shadowed.A(Ctx)", "App.Req"));
        assertFalse(references("App.Shadowed.B(Resp)", "App.Req"));
    }
}
