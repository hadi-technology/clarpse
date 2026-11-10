package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A member type is in scope in the whole body of its enclosing type, so a name that is used above the
 * member's declaration resolves to the member exactly as one used below it does.
 */
public class JavaForwardNestedTypeTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/Holder.java", String.join("\n",
                "package app;",
                "public class Holder {",
                "  Late make() { return null; }",
                "  Helper helper;",
                "  Deep.Leaf leaf() { return null; }",
                "  public record Late(int a) { }",
                "  static class Helper { }",
                "  static class Deep { static class Leaf { Tail tail; static class Tail { } } }",
                "}")));
        model = new ClarpseProject(files, Lang.JAVA).result().model();
    }

    private static Set<String> refs(final String component) {
        final Component cmp = model.copyOfComponent(component).orElseThrow(
                () -> new AssertionError("no component " + component));
        return cmp.references().stream().map(r -> r.invokedComponent()).collect(Collectors.toSet());
    }

    @Test
    public void recordUsedBeforeItsDeclarationIsTheNestedOne() {
        assertTrue(refs("app.Holder.make()").contains("app.Holder.Late"));
        assertFalse(refs("app.Holder").contains("app.Late"));
    }

    @Test
    public void staticClassFieldUsedBeforeItsDeclarationIsTheNestedOne() {
        assertTrue(refs("app.Holder").contains("app.Holder.Helper"));
        assertFalse(refs("app.Holder").contains("app.Helper"));
    }

    @Test
    public void qualifiedNestedNameResolvesThroughForwardMember() {
        assertTrue(refs("app.Holder.leaf()").contains("app.Holder.Deep.Leaf"));
        assertEquals(0, refs("app.Holder").stream().filter(r -> r.equals("app.Deep.Leaf")).count());
    }

    @Test
    public void memberOfMemberDeclaredAfterUseIsResolved() {
        assertTrue(refs("app.Holder.Deep.Leaf").contains("app.Holder.Deep.Leaf.Tail"));
    }
}
