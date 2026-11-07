package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Members written after a construct the syntax parser cannot place still belong to the type that
 * encloses them, and so do the types that follow it.
 */
public class CSharpEarlyClosedTypeTest {

    private static final String SOURCE = """
            namespace Demo;
            public partial struct Shade
            {
                public string Show(string text, object provider)
                {
                    return (text, provider) switch
                    {
                        (null or { Length: 0 }, null) => Plain(),
                        ( ['g'], null) => Plain(),
                        _ => "",
                    };
                }

                [Pure]
                public static bool After(string text) { return true; }

                public int Later { get; set; }
            }
            public class Neighbour { }
            """;

    @Test
    public void membersAfterAnUnplaceableConstructStayInTheirType() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(new ProjectFile("/Shade.cs", SOURCE)).model();
        assertTrue(model.containsComponent("Demo.Shade.Show(string, object)"));
        assertTrue(model.containsComponent("Demo.Shade.After(string)"));
        assertTrue(model.containsComponent("Demo.Shade.Later"));
        assertTrue(model.copyOfComponent("Demo.Shade").orElseThrow().children().contains("Demo.Shade.After(string)"));
    }

    @Test
    public void typesAfterAnUnplaceableConstructKeepTheirNamespace() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(new ProjectFile("/Shade.cs", SOURCE)).model();
        assertTrue(model.containsComponent("Demo.Neighbour"));
    }

    @Test
    public void membersOfTheSecondPartialFileAreMerged() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/Shade.Core.cs", "namespace Demo; public partial struct Shade { public int Core; }"),
                new ProjectFile("/Shade.Parse.cs", SOURCE)).model();
        assertTrue(model.containsComponent("Demo.Shade.Core"));
        assertTrue(model.containsComponent("Demo.Shade.After(string)"));
    }
}
