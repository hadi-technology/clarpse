package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.reference.TypeImplementationReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A name resolves by the scope it is written in: the member types of the enclosing types come before
 * the package, so a top-level class and a nested record of one simple name are told apart by the file
 * that mentions them. Nested types keep their own supertypes.
 */
public class JavaNestedTypeScopeTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/src/demo/Config.java", String.join("\n",
                "package demo;",
                "public record Config(Guard guard, Predictor predictor) {",
                "  public record Guard(boolean enabled) { }",
                "  public record Predictor(boolean enabled) { }",
                "  Guard fallback() { return new Guard(false); }",
                "  Object top() { return demo.Guard.make(); }",
                "}")));
        files.insertFile(new ProjectFile("/src/demo/Guard.java",
                "package demo;\npublic class Guard { public static Guard make() { return null; } }\n"));
        files.insertFile(new ProjectFile("/src/demo/Predictor.java", "package demo;\npublic class Predictor { }\n"));
        files.insertFile(new ProjectFile("/src/demo/Engine.java", String.join("\n",
                "package demo;",
                "public class Engine {",
                "  private final Guard guard;",
                "  Engine(Guard guard) { this.guard = guard; }",
                "  Config.Guard nested() { return null; }",
                "}")));
        files.insertFile(new ProjectFile("/src/demo/Popup.java", String.join("\n",
                "package demo;",
                "public sealed interface Popup permits Popup.Cancel, Popup.Choice {",
                "  record Cancel() implements Popup { }",
                "  record Choice(String text) implements Popup { }",
                "  interface Loader { void load(); }",
                "  interface Sub extends Loader { }",
                "  final class Result implements Loader { public void load() { } }",
                "}")));
        model = new ClarpseProject(files, Lang.JAVA).result().model();
    }

    private static Set<String> refs(final String component) {
        final Component cmp = model.copyOfComponent(component).orElseThrow(
                () -> new AssertionError("no component " + component));
        return cmp.references().stream().map(r -> r.invokedComponent()).collect(Collectors.toSet());
    }

    @Test
    public void nestedRecordWinsInsideItsEnclosingType() {
        assertTrue(refs("demo.Config.fallback()").contains("demo.Config.Guard"));
        assertFalse(refs("demo.Config.fallback()").contains("demo.Guard"));
        assertTrue(refs("demo.Config.guard").contains("demo.Config.Guard"));
        assertTrue(refs("demo.Config.predictor").contains("demo.Config.Predictor"));
    }

    @Test
    public void qualifiedNameStillReachesTheTopLevelClass() {
        assertTrue(refs("demo.Config.top()").contains("demo.Guard"));
    }

    @Test
    public void topLevelClassWinsOutsideTheEnclosingType() {
        assertTrue(refs("demo.Engine").contains("demo.Guard"));
        assertFalse(refs("demo.Engine.Engine(Guard)").contains("demo.Config.Guard"));
        assertTrue(refs("demo.Engine.nested()").contains("demo.Config.Guard"));
    }

    @Test
    public void nestedTypesKeepTheirOwnSupertypes() {
        assertTrue(model.copyOfComponent("demo.Popup.Cancel").orElseThrow()
                .references(TypeReferences.IMPLEMENTATION).contains(new TypeImplementationReference("demo.Popup")));
        assertTrue(model.copyOfComponent("demo.Popup.Result").orElseThrow()
                .references(TypeReferences.IMPLEMENTATION)
                .contains(new TypeImplementationReference("demo.Popup.Loader")));
        assertEquals(Set.of("demo.Popup.Loader"), model.copyOfComponent("demo.Popup.Sub").orElseThrow()
                .references(TypeReferences.EXTENSION).stream().map(r -> r.invokedComponent())
                .collect(Collectors.toSet()));
    }
}
