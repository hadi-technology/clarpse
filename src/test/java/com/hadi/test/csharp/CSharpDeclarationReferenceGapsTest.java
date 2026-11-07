package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A type named inside a property accessor body is a reference of that property and of its type. */

/**
 * References a declaration carries beyond its written type: class primary-constructor parameters,
 * generic method type arguments and field initializers.
 */
public class CSharpDeclarationReferenceGapsTest {

    private static OOPSourceCodeModel compile(final String source) throws Exception {
        return CSharpTestUtil.compileInline(new ProjectFile("/Gaps.cs", source)).model();
    }

    private static boolean references(final OOPSourceCodeModel model, final String from, final String to) {
        return model.copyOfComponent(from).orElseThrow()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE).stream()
                .map(ComponentReference::invokedComponent).anyMatch(to::equals);
    }

    @Test
    public void primaryConstructorParameterTypeIsReferenced() throws Exception {
        final OOPSourceCodeModel model = compile("""
                namespace Demo;
                public interface IClock {}
                public class Tracker(IClock clock, int count) { }
                public class Plain { }
                """);
        assertTrue(references(model, "Demo.Tracker", "Demo.IClock"));
    }

    @Test
    public void primaryConstructorParameterOfABaseCallIsStillTheBase() throws Exception {
        final OOPSourceCodeModel model = compile("""
                namespace Demo;
                public class Base(int n) { }
                public class Derived(int n) : Base(n) { }
                """);
        assertTrue(model.copyOfComponent("Demo.Derived").orElseThrow()
                .references(OOPSourceModelConstants.TypeReferences.EXTENSION).stream()
                .map(ComponentReference::invokedComponent).anyMatch("Demo.Base"::equals));
    }

    @Test
    public void genericMethodTypeArgumentIsReferenced() throws Exception {
        final OOPSourceCodeModel model = compile("""
                namespace Demo;
                public class Auto { }
                public class Dim { public bool Has<T>() { return true; } }
                public class User { public void M(Dim d) { if (d.Has<Auto>()) { } } }
                """);
        assertTrue(references(model, "Demo.User.M(Dim)", "Demo.Auto"));
    }

    @Test
    public void staticGenericCallTypeArgumentIsReferenced() throws Exception {
        final OOPSourceCodeModel model = compile("""
                namespace Demo;
                public class Auto { }
                public class User { public void M() { var x = Pick<Auto>(); } T Pick<T>() { return default; } }
                """);
        assertTrue(references(model, "Demo.User.M()", "Demo.Auto"));
    }

    @Test
    public void fieldInitializerStaticReadIsReferenced() throws Exception {
        final OOPSourceCodeModel model = compile("""
                namespace Demo;
                public static class Limits { public const int Default = 3; public static int Max() { return 1; } }
                public class User {
                  private const int Min = Limits.Default;
                  private int max = Limits.Max();
                  private readonly Widget w = new Widget();
                  private int none = 4;
                }
                public class Widget { }
                """);
        assertTrue(references(model, "Demo.User.Min", "Demo.Limits"));
        assertTrue(references(model, "Demo.User.max", "Demo.Limits"));
        assertTrue(references(model, "Demo.User.w", "Demo.Widget"));
        assertFalse(references(model, "Demo.User.none", "Demo.Limits"));
        assertTrue(references(model, "Demo.User", "Demo.Limits"));
    }

    @Test
    public void propertyInitializerIsReferenced() throws Exception {
        final OOPSourceCodeModel model = compile("""
                namespace Demo;
                public static class Limits { public const int Default = 3; }
                public class User { public int Top { get; set; } = Limits.Default; }
                """);
        assertTrue(references(model, "Demo.User.Top", "Demo.Limits"));
    }

    @Test
    public void interfaceExtendingAnInterfaceIsAnExtension() throws Exception {
        final OOPSourceCodeModel model = compile("""
                namespace Demo;
                public interface IPopover { }
                public interface IPopoverView : IPopover { }
                public interface IBoth : IPopover, IOther { }
                public interface IOther { }
                """);
        final Component view = model.copyOfComponent("Demo.IPopoverView").orElseThrow();
        assertTrue(view.references(OOPSourceModelConstants.TypeReferences.EXTENSION).stream()
                .map(ComponentReference::invokedComponent).anyMatch("Demo.IPopover"::equals));
        assertTrue(view.references(OOPSourceModelConstants.TypeReferences.IMPLEMENTATION).isEmpty());
    }

    @Test
    public void nestedTypeIsAChildOfItsDeclaringType() throws Exception {
        final OOPSourceCodeModel model = compile("""
                namespace Demo;
                public static class Registry {
                  public static class Names { public const string A = "a"; }
                  public int Count;
                }
                """);
        assertTrue(model.copyOfComponent("Demo.Registry").orElseThrow().children()
                .contains("Demo.Registry.Names"));
    }

    @Test
    public void requiredInitPropertyIsAMember() throws Exception {
        final OOPSourceCodeModel model = compile("""
                namespace Demo;
                public interface IContext { }
                public class Args {
                  public required IContext? Context { get; init; }
                  public required string Name { get; init; }
                  public static required int Count { get; set; }
                }
                """);
        assertTrue(model.containsComponent("Demo.Args.Context"));
        assertTrue(model.containsComponent("Demo.Args.Name"));
        assertTrue(references(model, "Demo.Args.Context", "Demo.IContext"));
        assertTrue(model.copyOfComponent("Demo.Args.Context").orElseThrow().modifiers().contains("required"));
        assertTrue(model.copyOfComponent("Demo.Args.Count").orElseThrow().modifiers().contains("required"));
        assertTrue(model.copyOfComponent("Demo.Args").orElseThrow().children().contains("Demo.Args.Context"));
    }

    @Test
    public void parameterFieldAndLocalTypesInGenericStaticMethodsAreReferenced() throws Exception {
        final OOPSourceCodeModel model = compile("""
                namespace Demo;
                public enum Topology { A, B }
                public class ProcessingOptions { public Topology Topology { get; set; } }
                public static class Processor
                {
                    public static (int, int) Calculate<T>(IEnumerable<T> items, ProcessingOptions options)
                        where T : class
                    {
                        if (options.Topology == Topology.A) { return (1, 2); }
                        return (0, 0);
                    }
                    public static async Task<List<U>> RunAsync<T, U>(Func<T, U> f, ProcessingOptions options, int n = 0)
                        where T : struct where U : class
                    {
                        ProcessingOptions local;
                        return null;
                    }
                }
                """);
        assertTrue(references(model, "Demo.Processor", "Demo.ProcessingOptions"));
        assertTrue(references(model, "Demo.Processor", "Demo.Topology"));
    }
}
