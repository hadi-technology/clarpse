package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** What an import brings into scope, and what takes precedence over it. */
public class JavaImportScopeTest {

    private static OOPSourceCodeModel model() throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/src/main/java/a/Lim.java",
                "package a;\npublic class Lim { public static final int MAX = 3; public static int cap() { return 1; } }\n"));
        files.insertFile(new ProjectFile("/src/main/java/a/User.java", String.join("\n",
                "package a2;",
                "import static a.Lim.MAX;",
                "import static a.Lim.cap;",
                "public class User {",
                "  int f() { return MAX; }",
                "  int g() { return cap(); }",
                "}")));
        files.insertFile(new ProjectFile("/src/main/java/p/Canvas.java", "package p;\npublic class Canvas { }\n"));
        files.insertFile(new ProjectFile("/src/main/java/p/Pane.java", String.join("\n",
                "package p;",
                "import java.awt.*;",
                "public class Pane {",
                "  Canvas field;",
                "  Pane(Canvas... plots) { }",
                "  Object make() { return new Canvas(); }",
                "  Frame frame;",
                "}")));
        return new ClarpseProject(files, Lang.JAVA).result().model();
    }

    private static Set<String> refs(final OOPSourceCodeModel model, final String component) {
        final Component cmp = model.copyOfComponent(component).orElseThrow(
                () -> new AssertionError("no component " + component));
        return cmp.references().stream().map(r -> r.invokedComponent()).collect(Collectors.toSet());
    }

    @Test
    public void staticallyImportedConstantReferencesItsDeclaringType() throws Exception {
        final Set<String> refs = refs(model(), "a2.User.f()");
        assertTrue(refs.toString(), refs.contains("a.Lim"));
        assertFalse(refs.toString(), refs.contains("a.Lim.MAX"));
    }

    @Test
    public void staticallyImportedMethodReferencesItsDeclaringType() throws Exception {
        assertTrue(refs(model(), "a2.User.g()").contains("a.Lim"));
    }

    @Test
    public void typeOfTheSamePackageBeatsAnOnDemandImport() throws Exception {
        final Set<String> refs = refs(model(), "p.Pane");
        assertTrue(refs.toString(), refs.contains("p.Canvas"));
        assertFalse(refs.toString(), refs.contains("java.awt.Canvas"));
    }

    @Test
    public void onDemandImportStillResolvesWhatThePackageLacks() throws Exception {
        assertTrue(refs(model(), "p.Pane").contains("java.awt.Frame"));
    }
}
