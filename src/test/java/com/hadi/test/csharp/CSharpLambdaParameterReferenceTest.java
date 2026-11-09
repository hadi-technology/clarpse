package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The type a lambda or anonymous method writes for a parameter is a reference of the member around it. */
public class CSharpLambdaParameterReferenceTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Rag.cs", """
                        namespace Demo.Rag;
                        public class KnowledgeBase { public bool Has(string t) => true; }
                        public class Cache { }
                        public class Session { }
                        public class Legacy { }
                        public class Implicit { }
                        public class Initial { }
                        public class OwnParam { }
                        """),
                new ProjectFile("/Api.cs", """
                        using System;
                        using Demo.Rag;
                        namespace Demo.Api;
                        public class Router { public void MapGet(string p, Delegate d) { } }
                        public class Endpoints
                        {
                            public static void Map(Router app)
                            {
                                app.MapGet("/has", (KnowledgeBase kb, string title) => kb.Has(title));
                            }
                            public static void MapBlock(Router app)
                            {
                                app.MapGet("/b", (Cache c) => { return 1; });
                            }
                            public static void MapExpression(Router app) => app.MapGet("/e", (Session s) => s);
                            public static void MapAnonymous(Router app)
                            {
                                app.MapGet("/a", delegate (Legacy l) { return 1; });
                            }
                            public static void MapImplicit(Router app)
                            {
                                app.MapGet("/i", (kb, title) => kb.Has(title));
                            }
                            public Func<Initial, int> Handler = (Initial i) => 1;
                            public static void Own(OwnParam p) { }
                        }
                        """)).model();
    }

    private static boolean references(final String from, final String to) {
        return model.copyOfComponent(from).orElseThrow(() -> new AssertionError("no component " + from))
                .references().stream().map(ComponentReference::invokedComponent).anyMatch(to::equals);
    }

    @Test
    public void explicitlyTypedParameterInABlockBodyIsReferenced() {
        assertTrue(references("Demo.Api.Endpoints.Map(Router)", "Demo.Rag.KnowledgeBase"));
        assertTrue(references("Demo.Api.Endpoints", "Demo.Rag.KnowledgeBase"));
    }

    @Test
    public void parameterOfALambdaWithABlockIsReferenced() {
        assertTrue(references("Demo.Api.Endpoints.MapBlock(Router)", "Demo.Rag.Cache"));
    }

    @Test
    public void parameterInAnExpressionBodiedMemberIsReferenced() {
        assertTrue(references("Demo.Api.Endpoints.MapExpression(Router)", "Demo.Rag.Session"));
    }

    @Test
    public void parameterOfAnAnonymousMethodIsReferenced() {
        assertTrue(references("Demo.Api.Endpoints.MapAnonymous(Router)", "Demo.Rag.Legacy"));
    }

    @Test
    public void parameterInAFieldInitializerIsReferenced() {
        assertTrue(references("Demo.Api.Endpoints.Handler", "Demo.Rag.Initial"));
    }

    @Test
    public void implicitlyTypedParametersNameNoType() {
        assertFalse(references("Demo.Api.Endpoints.MapImplicit(Router)", "Demo.Rag.KnowledgeBase"));
        assertFalse(references("Demo.Api.Endpoints.MapImplicit(Router)", "Demo.Rag.Implicit"));
    }

    @Test
    public void lambdaParametersAreNotParametersOfTheMember() {
        assertTrue(model.copyOfComponent("Demo.Api.Endpoints.Map(Router)").isPresent());
        assertFalse(model.copyOfComponent("Demo.Api.Endpoints.Map(Router,KnowledgeBase)").isPresent());
    }
}
