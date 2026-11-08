package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.reference.AnnotationReference;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * An annotation written inside another annotation's arguments names a type the annotated
 * declaration depends on; it is recorded as a plain type reference, because the declaration is not
 * itself annotated with it.
 */
public class JavaNestedAnnotationReferenceTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/lib/anno/Storage.java",
                "package lib.anno;\npublic @interface Storage { String value(); }\n"));
        files.insertFile(new ProjectFile("/lib/anno/Other.java",
                "package lib.anno;\npublic @interface Other { }\n"));
        files.insertFile(new ProjectFile("/lib/anno/State.java",
                "package lib.anno;\npublic @interface State { String name() default \"\";"
                        + " Storage[] storages() default {}; Other other() default @Other; }\n"));
        files.insertFile(new ProjectFile("/app/Holder.java", String.join("\n",
                "package app;",
                "import lib.anno.State;",
                "import lib.anno.Storage;",
                "import lib.anno.Other;",
                "@State(name = \"h\", storages = @Storage(\"file.xml\"))",
                "public class Holder {",
                "  @State(storages = {@Storage(\"a\"), @Storage(\"b\")}, other = @Other)",
                "  void run() { }",
                "  @Other",
                "  void plain() { }",
                "}")));
        model = new ClarpseProject(files, Lang.JAVA).result().model();
    }

    private static Component component(final String name) {
        return model.copyOfComponent(name).orElseThrow(() -> new AssertionError("no component " + name));
    }

    private static boolean typeRef(final Component component, final String target) {
        return component.references().stream()
                .anyMatch(r -> r instanceof SimpleTypeReference && r.invokedComponent().equals(target));
    }

    private static boolean annotationRef(final Component component, final String target) {
        return component.references().stream()
                .anyMatch(r -> r instanceof AnnotationReference && r.invokedComponent().equals(target));
    }

    @Test
    public void nestedAnnotationInNamedArgumentIsRecorded() {
        assertTrue(typeRef(component("app.Holder"), "lib.anno.Storage"));
        assertTrue(annotationRef(component("app.Holder"), "lib.anno.State"));
    }

    @Test
    public void nestedAnnotationsInArrayArgumentAreRecordedOnTheMethod() {
        final Component run = component("app.Holder.run()");
        assertTrue(typeRef(run, "lib.anno.Storage"));
        assertTrue(typeRef(run, "lib.anno.Other"));
    }

    @Test
    public void nestedAnnotationIsNotAnAnnotationOfTheDeclaration() {
        assertFalse(annotationRef(component("app.Holder"), "lib.anno.Storage"));
        assertFalse(annotationRef(component("app.Holder.run()"), "lib.anno.Storage"));
    }

    @Test
    public void anAppliedAnnotationIsNotAlsoAPlainTypeReference() {
        final Component plain = component("app.Holder.plain()");
        assertTrue(annotationRef(plain, "lib.anno.Other"));
        assertFalse(typeRef(plain, "lib.anno.Other"));
    }

    @Test
    public void nestedDeclarationDefaultIsRecordedOnTheAnnotationType() {
        assertTrue(typeRef(component("lib.anno.State"), "lib.anno.Other"));
        assertEquals(1, component("app.Holder.run()").references().stream()
                .filter((ComponentReference r) -> r.invokedComponent().equals("lib.anno.Storage")).count());
    }
}
