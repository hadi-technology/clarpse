package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Using aliases of partial declarations, generic static receivers, ref structs and implicit usings. */
public class CSharpTypeDeclarationDetailsTest {

    private static boolean references(final OOPSourceCodeModel model, final String from, final String to) {
        return model.copyOfComponent(from).orElseThrow().references().stream()
                .map(ComponentReference::invokedComponent).anyMatch(to::equals);
    }

    @Test
    public void aliasOfOnePartialFileResolvesItsMembers() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/A.cs", "namespace Demo; public partial record S { }"),
                new ProjectFile("/B.cs", """
                        namespace Demo;
                        using O = Demo.Nodes.Obj;
                        public partial record S { public O M() => null; public void P(O value) { O local = null; } }
                        """),
                new ProjectFile("/C.cs", "namespace Demo.Nodes; public class Obj { }")).model();
        assertTrue(references(model, "Demo.S.M()", "Demo.Nodes.Obj"));
        assertTrue(references(model, "Demo.S", "Demo.Nodes.Obj"));
        assertFalse(references(model, "Demo.S", "O"));
    }

    @Test
    public void staticReceiverInALocalWithAnOutVariable() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(new ProjectFile("/A.cs", """
                namespace Demo;
                public static class Helper {
                  public static bool TryGet(int a, out int b) { b = a; return true; }
                  public static float Scale(int a) { return a; }
                }
                public class A {
                  void M() { bool ok = Helper.TryGet(1, out var x); }
                  float N() { float s = Helper.Scale(1); return s; }
                }
                """)).model();
        assertTrue(references(model, "Demo.A.M()", "Demo.Helper"));
        assertTrue(references(model, "Demo.A.N()", "Demo.Helper"));
    }

    @Test
    public void genericStaticReceiver() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(new ProjectFile("/A.cs", """
                namespace Demo;
                public static class Outer {
                  private static class Cache<T> { public static int V; }
                  public static int Get<T>() { return Cache<T>.V; }
                }
                public static class Other<T> { public static int V; }
                public class User { int M() { return Other<string>.V; } }
                """)).model();
        assertTrue(references(model, "Demo.Outer.Get<T>()", "Demo.Outer.Cache"));
        assertTrue(references(model, "Demo.User.M()", "Demo.Other"));
    }

    @Test
    public void refStructKeepsItsRefModifier() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(new ProjectFile("/A.cs", """
                namespace Demo;
                public ref struct R { }
                public readonly ref struct Q { }
                public struct Plain { }
                public class Holder { public void M(ref int x) { } }
                """)).model();
        assertTrue(model.copyOfComponent("Demo.R").orElseThrow().modifiers().contains("ref"));
        assertTrue(model.copyOfComponent("Demo.Q").orElseThrow().modifiers().contains("ref"));
        assertFalse(model.copyOfComponent("Demo.Plain").orElseThrow().modifiers().contains("ref"));
    }

    private static java.util.Set<String> imports(final OOPSourceCodeModel model, final String type) {
        return model.copyOfComponent(type).orElseThrow().imports();
    }

    private static OOPSourceCodeModel implicit(final String csproj, final String propsContent) throws Exception {
        final ProjectFile[] files = propsContent == null
                ? new ProjectFile[] {
                        new ProjectFile("/App/App.csproj", csproj),
                        new ProjectFile("/App/C.cs", "namespace App; public class C { IAsyncEnumerable<int> M() => null; Task T() => null; }"),
                        new ProjectFile("/Other/D.cs", "namespace Other; public class D { Task T() => null; }")}
                : new ProjectFile[] {
                        new ProjectFile("/Directory.Build.props", propsContent),
                        new ProjectFile("/App/App.csproj", csproj),
                        new ProjectFile("/App/C.cs", "namespace App; public class C { IAsyncEnumerable<int> M() => null; Task T() => null; }")};
        return CSharpTestUtil.compileInline(files).model();
    }

    @Test
    public void implicitUsingsApplyToTheFilesOfTheProject() throws Exception {
        final OOPSourceCodeModel model = implicit(
                "<Project Sdk=\"Microsoft.NET.Sdk\"><PropertyGroup><ImplicitUsings>enable</ImplicitUsings></PropertyGroup></Project>",
                null);
        assertTrue(imports(model, "App.C").contains("System.Collections.Generic"));
        assertTrue(imports(model, "App.C").contains("System.Threading.Tasks"));
        assertTrue(imports(model, "App.C").contains("System.Linq"));
    }

    @Test
    public void filesOutsideTheProjectGetNone() throws Exception {
        final OOPSourceCodeModel model = implicit(
                "<Project Sdk=\"Microsoft.NET.Sdk\"><PropertyGroup><ImplicitUsings>enable</ImplicitUsings></PropertyGroup></Project>",
                null);
        assertFalse(imports(model, "Other.D").contains("System.Threading.Tasks"));
    }

    @Test
    public void projectWithoutTheSettingGetsNone() throws Exception {
        final OOPSourceCodeModel model = implicit("<Project Sdk=\"Microsoft.NET.Sdk\"></Project>", null);
        assertFalse(imports(model, "App.C").contains("System.Threading.Tasks"));
    }

    @Test
    public void settingInheritedFromDirectoryBuildProps() throws Exception {
        final OOPSourceCodeModel model = implicit("<Project Sdk=\"Microsoft.NET.Sdk\"></Project>",
                "<Project><PropertyGroup><ImplicitUsings>enable</ImplicitUsings></PropertyGroup></Project>");
        assertTrue(imports(model, "App.C").contains("System.Threading.Tasks"));
    }

    @Test
    public void projectCanDisableWhatTheInheritedSettingEnables() throws Exception {
        final OOPSourceCodeModel model = implicit(
                "<Project Sdk=\"Microsoft.NET.Sdk\"><PropertyGroup><ImplicitUsings>disable</ImplicitUsings></PropertyGroup></Project>",
                "<Project><PropertyGroup><ImplicitUsings>enable</ImplicitUsings></PropertyGroup></Project>");
        assertFalse(imports(model, "App.C").contains("System.Threading.Tasks"));
    }

    @Test
    public void nestedRecordStructDoesNotChangeTheKindOfItsRecord() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/B.cs", "namespace Demo; public partial record S { public record struct Inner(int X); }"),
                new ProjectFile("/A.cs", "namespace Demo; using O = Demo.Obj; public partial record S { public O M() => null; }"),
                new ProjectFile("/C.cs", "namespace Demo; public class Obj { }"),
                new ProjectFile("/D.cs", "namespace Demo; public partial record struct V { public int X; }")).model();
        assertEquals(com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType.CLASS,
                model.copyOfComponent("Demo.S").orElseThrow().componentType());
        assertTrue(references(model, "Demo.S", "Demo.Obj"));
        assertTrue(model.copyOfComponent("Demo.S").orElseThrow().children().contains("Demo.S.M()"));
        assertTrue(model.copyOfComponent("Demo.S").orElseThrow().children().contains("Demo.S.Inner"));
        assertTrue(model.containsComponent("Demo.S.Inner"));
        assertEquals(com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType.STRUCT,
                model.copyOfComponent("Demo.V").orElseThrow().componentType());
    }
}
