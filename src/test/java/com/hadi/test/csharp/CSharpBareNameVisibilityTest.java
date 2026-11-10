package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A bare or generic-argument type name binds to a repository type only when the type's namespace is
 * one the name is visible from: the file's namespace and the namespaces enclosing it, a using
 * directive, a using alias or a global using. A type that sits in none of them is not the one meant.
 */
public class CSharpBareNameVisibilityTest {

    private static final String SCROLLER = "App.Desktop.Controls.ScrollViewer";

    private static final String HELPER = """
            using Avalonia.Controls;
            namespace App.Controls;
            public class ScrollHelper {
              public object Find(Control c) => c.FindAncestorOfType<ScrollViewer>();
              public ScrollViewer Viewer;
            }
            """;

    private static final String DESKTOP_SCROLLER = """
            namespace App.Desktop.Controls;
            public class ScrollViewer { }
            """;

    private static OOPSourceCodeModel compile(final String helper, final String... otherFiles) throws Exception {
        final ProjectFile[] files = new ProjectFile[otherFiles.length + 1];
        files[0] = new ProjectFile("/src/App.Controls/Scroll.cs", helper);
        for (int i = 0; i < otherFiles.length; i++) {
            files[i + 1] = new ProjectFile("/src/Other" + i + ".cs", otherFiles[i]);
        }
        return CSharpTestUtil.compileInline(files).model();
    }

    private static boolean anyReferenceTo(final OOPSourceCodeModel model, final String target) {
        return model.components().anyMatch(component -> component.references().stream()
                .map(ComponentReference::invokedComponent).anyMatch(target::equals));
    }

    private static boolean references(final OOPSourceCodeModel model, final String from, final String to) {
        return model.copyOfComponent(from).orElseThrow().references().stream()
                .map(ComponentReference::invokedComponent).anyMatch(to::equals);
    }

    @Test
    public void aTypeInANamespaceNothingMakesVisibleIsNotBound() throws Exception {
        final OOPSourceCodeModel model = compile(HELPER, DESKTOP_SCROLLER);
        assertFalse("no component may reference " + SCROLLER, anyReferenceTo(model, SCROLLER));
        assertFalse(references(model, "App.Controls.ScrollHelper", SCROLLER));
        assertFalse(references(model, "App.Controls.ScrollHelper.Viewer", SCROLLER));
        assertFalse(references(model, "App.Controls.ScrollHelper.Find(Control)", SCROLLER));
    }

    @Test
    public void aTypeInASiblingNamespaceWithoutAUsingIsNotBound() throws Exception {
        final OOPSourceCodeModel model = compile("""
                namespace App.Controls;
                public class Holder { public Gadget Item; }
                """, """
                namespace App.Widgets;
                public class Gadget { }
                """);
        assertFalse(references(model, "App.Controls.Holder.Item", "App.Widgets.Gadget"));
    }

    @Test
    public void aUsingDirectiveMakesTheNameVisible() throws Exception {
        final OOPSourceCodeModel model = compile(
                HELPER.replace("using Avalonia.Controls;", "using Avalonia.Controls;\nusing App.Desktop.Controls;"),
                DESKTOP_SCROLLER);
        assertTrue(references(model, "App.Controls.ScrollHelper.Viewer", SCROLLER));
        assertTrue(references(model, "App.Controls.ScrollHelper.Find(Control)", SCROLLER));
    }

    @Test
    public void anEnclosingNamespaceMakesTheNameVisible() throws Exception {
        final OOPSourceCodeModel model = compile(HELPER, """
                namespace App;
                public class ScrollViewer { }
                """);
        assertTrue(references(model, "App.Controls.ScrollHelper.Viewer", "App.ScrollViewer"));
        assertTrue(references(model, "App.Controls.ScrollHelper.Find(Control)", "App.ScrollViewer"));
    }

    @Test
    public void aGlobalUsingInAnotherFileMakesTheNameVisible() throws Exception {
        final OOPSourceCodeModel model = compile(HELPER, DESKTOP_SCROLLER,
                "global using App.Desktop.Controls;\n");
        assertTrue(references(model, "App.Controls.ScrollHelper.Viewer", SCROLLER));
        assertTrue(references(model, "App.Controls.ScrollHelper.Find(Control)", SCROLLER));
    }

    @Test
    public void aUsingAliasMakesTheNameVisible() throws Exception {
        final OOPSourceCodeModel model = compile(
                HELPER.replace("using Avalonia.Controls;",
                        "using Avalonia.Controls;\nusing ScrollViewer = App.Desktop.Controls.ScrollViewer;"),
                DESKTOP_SCROLLER);
        assertTrue(references(model, "App.Controls.ScrollHelper.Viewer", SCROLLER));
    }

    @Test
    public void aFullyQualifiedNameStillBinds() throws Exception {
        final OOPSourceCodeModel model = compile("""
                namespace App.Controls;
                public class Holder { public App.Desktop.Controls.ScrollViewer Item; }
                """, DESKTOP_SCROLLER);
        assertTrue(references(model, "App.Controls.Holder.Item", SCROLLER));
        assertEquals(1, model.components().filter(c -> c.uniqueName().equals(SCROLLER)).count());
    }
}
