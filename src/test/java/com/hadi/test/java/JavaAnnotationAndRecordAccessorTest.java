package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Members the compiler generates, and the annotation type, exist in the compiled type and so belong
 * in the model.
 */
public class JavaAnnotationAndRecordAccessorTest {

    private static final String RETRY = String.join("\n",
            "package app;",
            "/** Retries a call. */",
            "public @interface Retry {",
            "    int attempts() default 3;",
            "    String label();",
            "}");

    private static final String POINT = "package app;\npublic record Point(int x, int y) { }\n";

    private static final String NAMED = String.join("\n",
            "package app;",
            "public record Named(String value) {",
            "    public String value() { return value.trim(); }",
            "}");

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/app/Retry.java", RETRY));
        files.insertFile(new ProjectFile("/app/Point.java", POINT));
        files.insertFile(new ProjectFile("/app/Named.java", NAMED));
        model = new ClarpseProject(files, Lang.JAVA).result().model();
    }

    @Test
    public void annotationTypeIsModelledAsAComponent() {
        final Component retry = component("app.Retry");
        assertEquals(ComponentType.ANNOTATION, retry.componentType());
        assertTrue(retry.modifiers().contains("public"));
        assertTrue(retry.comment().contains("Retries a call."));
    }

    @Test
    public void annotationTypeIsABaseComponent() {
        assertTrue("an annotation type is a type, and its elements hang off it",
                component("app.Retry").componentType().isBaseComponent());
    }

    @Test
    public void annotationElementsAreMembersOfTheAnnotation() {
        final Component retry = component("app.Retry");
        assertTrue(retry.children().contains("app.Retry.attempts()"));
        assertTrue(retry.children().contains("app.Retry.label()"));
        assertEquals(ComponentType.METHOD, component("app.Retry.attempts()").componentType());
        assertEquals("attempts() : int", component("app.Retry.attempts()").codeFragment());
    }

    @Test
    public void recordAccessorsAreModelledAsMethods() {
        final Component point = component("app.Point");
        assertTrue(point.children().contains("app.Point.x()"));
        assertTrue(point.children().contains("app.Point.y()"));
        assertEquals(ComponentType.METHOD, component("app.Point.x()").componentType());
        assertEquals("x() : int", component("app.Point.x()").codeFragment());
        assertTrue(component("app.Point.x()").modifiers().contains("public"));
    }

    @Test
    public void recordComponentsRemainFieldsAlongsideTheirAccessors() {
        assertEquals(ComponentType.FIELD, component("app.Point.x").componentType());
        assertEquals(ComponentType.METHOD, component("app.Point.x()").componentType());
    }

    @Test
    public void anExplicitlyDeclaredAccessorIsNotDuplicated() {
        final Component named = component("app.Named");
        assertEquals("the declared accessor is modelled once, not once more as an implicit one",
                1, named.children().stream().filter(child -> child.equals("app.Named.value()")).count());
        assertEquals(ComponentType.METHOD, component("app.Named.value()").componentType());
    }

    private static Component component(final String uniqueName) {
        return model.copyOfComponent(uniqueName).orElseThrow(
                () -> new AssertionError("no component named " + uniqueName));
    }
}
