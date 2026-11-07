package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A name written before a member access is a type only where nothing nearer can be what it names. */
public class CSharpReceiverShadowingTest {

    private static boolean references(final OOPSourceCodeModel model, final String from, final String to) {
        return model.copyOfComponent(from).orElseThrow().references().stream()
                .map(ComponentReference::invokedComponent).anyMatch(to::equals);
    }

    @Test
    public void namespaceSegmentEqualToAClassNameIsNotThatClass() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/Builder.cs", "namespace Acme.Reports; public static class Builder { public static int Run() { return 1; } }"),
                new ProjectFile("/Other.cs", "namespace Other; public class Reports { }"),
                new ProjectFile("/User.cs", "namespace Demo; public class User { int M() { return Acme.Reports.Builder.Run(); } }")).model();
        assertTrue(references(model, "Demo.User.M()", "Acme.Reports.Builder"));
        assertFalse(references(model, "Demo.User.M()", "Other.Reports"));
    }

    @Test
    public void inheritedMemberShadowsTheReceiver() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/Settings.cs", "namespace Demo; public class Settings { public static int Load() { return 1; } }"),
                new ProjectFile("/Base.cs", "namespace Demo; public class Base { protected Holder Settings; }"),
                new ProjectFile("/Holder.cs", "namespace Demo; public class Holder { public int Load() { return 1; } }"),
                new ProjectFile("/Child.cs", """
                        namespace Demo;
                        public class Child : Base {
                          int M() { return Settings.Load(); }
                          int P => Settings.Load();
                        }
                        """)).model();
        assertFalse(references(model, "Demo.Child.M()", "Demo.Settings"));
        assertFalse(references(model, "Demo.Child.P", "Demo.Settings"));
    }

    @Test
    public void frameworkStaticClassIsNotARepositoryTypeOfTheSameName() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/File.cs", "namespace Domain; public class File { }"),
                new ProjectFile("/Path.cs", "namespace Domain; public class Path { }"),
                new ProjectFile("/User.cs", """
                        namespace Demo;
                        using System.IO;
                        public class User {
                          string M(string p) { return File.ReadAllText(Path.Combine(p, "a")); }
                        }
                        """)).model();
        assertFalse(references(model, "Demo.User.M(string)", "Domain.File"));
        assertFalse(references(model, "Demo.User.M(string)", "Domain.Path"));
    }

    @Test
    public void bodiesOfPropertiesFieldsAndEventsAreShadowedByTheirOwnTypesMembers() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/Settings.cs", "namespace Demo; public class Settings { public static int Count; public static void Hook() { } }"),
                new ProjectFile("/Holder.cs", "namespace Demo; public class Holder { public int Count; public void Hook() { } }"),
                new ProjectFile("/User.cs", """
                        namespace Demo;
                        public class User {
                          private Holder Settings = new Holder();
                          public int Prop { get { return Settings.Count; } }
                          private int total = Settings.Count;
                          public event System.Action Changed { add { Settings.Hook(); } remove { } }
                        }
                        """)).model();
        assertFalse(references(model, "Demo.User.Prop", "Demo.Settings"));
        assertFalse(references(model, "Demo.User.total", "Demo.Settings"));
        assertFalse(references(model, "Demo.User.Changed", "Demo.Settings"));
    }
}
