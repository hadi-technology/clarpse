package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.CompileResult;
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

    @Test
    public void attributesOnTypeParametersDoNotHideTheMembersThatFollow() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(new ProjectFile("/Cache.cs", """
                namespace Demo;
                public static class Extensions
                {
                    private static class Cache<
                        [Dyn(Kind.Fields)] T>
                        where T : System.Enum
                    {
                        public static readonly int Values = 1;
                    }

                    public static string AsString<[Dyn(Kind.Fields)] T>(this T value)
                        where T : System.Enum
                    {
                        return Cache<T>.Values.ToString();
                    }

                    public static int Later() { return 1; }
                }
                public class Neighbour { }
                """)).model();
        assertTrue(model.containsComponent("Demo.Extensions.Cache"));
        assertTrue(model.containsComponent("Demo.Extensions.Cache.Values"));
        assertTrue(model.containsComponent("Demo.Extensions.Later()"));
        assertTrue(model.containsComponent("Demo.Neighbour"));
        assertTrue(model.copyOfComponent("Demo.Extensions").orElseThrow().references().stream()
                .anyMatch(r -> r.invokedComponent().equals("Demo.Extensions.Cache")));
    }

    @Test
    public void conditionalAlternativesThatEachOpenABlockDoNotFailTheFile() throws Exception {
        final CompileResult model = CSharpTestUtil.compileInline(new ProjectFile("/Axis.cs", """
                namespace Demo;
                public abstract class CoreAxis<TText, TLine>
                {
                    public double? MinZoomDelta { get; set; }

                    private string Label(System.Func<double, string> labeler, double value)
                    {
                        try
                        {
                            return labeler(value);
                        }
                #if DEBUG
                        catch (System.Exception e)
                        {
                            System.Console.WriteLine(e.Message);
                #else
                        catch
                        {
                #endif
                            return string.Empty;
                        }
                    }
                }
                public class Axis : CoreAxis<int, int> { }
                """));
        final OOPSourceCodeModel parsed = model.model();
        assertTrue(model.failures().toString(), model.failures().isEmpty());
        assertTrue(parsed.containsComponent("Demo.CoreAxis.MinZoomDelta"));
        assertTrue(parsed.copyOfComponent("Demo.Axis").orElseThrow().references().stream()
                .anyMatch(r -> r.invokedComponent().equals("Demo.CoreAxis")));
    }
}
