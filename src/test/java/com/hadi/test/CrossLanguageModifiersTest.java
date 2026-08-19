package com.hadi.test;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Assume;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@code modifiers()} should answer "is this public?" and "is this static?" the same way in every
 * language, and should only ever hold tokens the model can resolve back through its own
 * {@link OOPSourceModelConstants.AccessModifiers} enum.
 */
public class CrossLanguageModifiersTest {

    private static final String JAVA = String.join("\n",
            "package app;",
            "public abstract class Svc {",
            "    private static final int CAP = 3;",
            "    protected abstract void run();",
            "    public final synchronized int size() { return 0; }",
            "    public static int make() { return 3; }",
            "}");

    private static final String CSHARP = String.join("\n",
            "namespace App {",
            "  public abstract partial class Svc {",
            "    private static readonly int Cap = 3;",
            "    internal protected virtual void Run() { }",
            "    public sealed override string ToString() { return null; }",
            "    public static int Make() { return 3; }",
            "  }",
            "}");

    @Test
    public void javaPublicAndStaticAreExplicit() throws Exception {
        final OOPSourceCodeModel model = compile(inMemory("/app/Svc.java", JAVA), Lang.JAVA);
        assertTrue(component(model, "app.Svc").modifiers().contains("public"));
        assertTrue(component(model, "app.Svc.size()").modifiers().contains("public"));
        assertTrue(component(model, "app.Svc.make()").modifiers().contains("static"));
        assertEveryModifierIsResolvable(model);
    }

    @Test
    public void csharpPublicAndStaticAreExplicit() throws Exception {
        final OOPSourceCodeModel model = compile(inMemory("/Svc.cs", CSHARP), Lang.CSHARP);
        assertTrue(component(model, "App.Svc").modifiers().contains("public"));
        assertTrue(component(model, "App.Svc.ToString()").modifiers().contains("public"));
        assertTrue(component(model, "App.Svc.Make()").modifiers().contains("static"));
        assertEveryModifierIsResolvable(model);
    }

    @Test
    public void typescriptExportedDeclarationsArePublic() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final OOPSourceCodeModel model = compile(fixture("typescript"), Lang.TYPESCRIPT);
        assertTrue(component(model, "src.svc.Svc").modifiers().contains("public"));
        assertTrue("export is still reported alongside it",
                component(model, "src.svc.Svc").modifiers().contains("export"));
        assertTrue(component(model, "src.svc.Shape").modifiers().contains("public"));
        assertTrue(component(model, "src.svc.Color").modifiers().contains("public"));
        assertTrue(component(model, "src.svc.helper").modifiers().contains("public"));
        assertTrue("a module-private type is not public",
                component(model, "src.svc.Internal").modifiers().isEmpty());
        assertTrue(component(model, "src.svc.Svc.cap").modifiers().contains("private"));
        assertTrue(component(model, "src.svc.Svc.cap").modifiers().contains("static"));
        assertEveryModifierIsResolvable(model);
    }

    @Test
    public void pythonVisibilityAndStaticnessAreExplicit() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final OOPSourceCodeModel model = compile(fixture("python"), Lang.PYTHON);
        assertTrue(component(model, "src.svc.Svc").modifiers().contains("public"));
        assertTrue(component(model, "src.svc.Svc.size() : int").modifiers().contains("public"));
        assertTrue(component(model, "src.svc.Svc.owner").modifiers().contains("public"));
        assertTrue("a dunder method is public API",
                component(model, "src.svc.Svc.__str__() : str").modifiers().contains("public"));
        assertTrue(component(model, "src.svc.Svc._helper() : int").modifiers().contains("protected"));
        assertTrue(component(model, "src.svc.Svc.__secret() : int").modifiers().contains("private"));
        assertTrue("@staticmethod is static",
                component(model, "src.svc.Svc.make() : int").modifiers().contains("static"));
        assertTrue("@classmethod is static too",
                component(model, "src.svc.Svc.of() : int").modifiers().contains("static"));
        assertTrue(component(model, "src.svc._Hidden").modifiers().contains("protected"));
        assertEveryModifierIsResolvable(model);
    }

    /**
     * Whatever a compiler emits must round-trip through the model's own modifier vocabulary, so that a
     * consumer can reach {@link OOPSourceModelConstants.AccessModifiers#getUMLClassDigramSymbol()}
     * without guessing.
     */
    private static void assertEveryModifierIsResolvable(final OOPSourceCodeModel model) {
        final List<String> unresolvable = new ArrayList<>();
        model.components().forEach(component -> component.modifiers().forEach(modifier -> {
            if (OOPSourceModelConstants.accessModifier(modifier) == null) {
                unresolvable.add(component.uniqueName() + " -> " + modifier);
            }
        }));
        assertTrue("modifiers outside the model's vocabulary: " + unresolvable, unresolvable.isEmpty());
    }

    private static Component component(final OOPSourceCodeModel model, final String uniqueName) {
        final Component component = model.copyOfComponent(uniqueName).orElse(null);
        assertNotNull("no component named " + uniqueName, component);
        return component;
    }

    private static OOPSourceCodeModel compile(final ProjectFiles files, final Lang lang) throws Exception {
        final CompileResult result = new ClarpseProject(files, lang).result();
        assertTrue("fixture failed to compile: " + result.failures(), result.failures().isEmpty());
        return result.model();
    }

    private static ProjectFiles inMemory(final String path, final String content) {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile(path, content));
        return files;
    }

    private static ProjectFiles fixture(final String language) throws Exception {
        return new ProjectFiles(Paths.get("src/test/resources", language, "cross-language-modifiers")
                .toAbsolutePath().toString());
    }
}
