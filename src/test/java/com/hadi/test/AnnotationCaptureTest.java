package com.hadi.test;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Applied annotations ({@code @Service} on a class, {@code @Override} on a method, {@code @Autowired}
 * on a field) carry first-class architectural intent, so the parsed model must record them on the
 * component they annotate. See issue #181.
 */
public class AnnotationCaptureTest {

    @Test
    public void classLevelAnnotationIsCaptured() throws Exception {
        final String java = String.join("\n",
                "package app;",
                "@Service",
                "public class Foo {}");
        final Component foo = component(compile("/app/Foo.java", java), "app.Foo");
        assertTrue("class annotation @Service should be captured: " + foo.annotations(),
                hasAnnotation(foo, "Service"));
    }

    @Test
    public void methodLevelAnnotationsAreCaptured() throws Exception {
        final String java = String.join("\n",
                "package app;",
                "public class Foo {",
                "    @Override",
                "    @Deprecated",
                "    public String toString() { return null; }",
                "}");
        final Component method = component(compile("/app/Foo.java", java), "app.Foo.toString()");
        assertTrue("@Override should be captured: " + method.annotations(),
                hasAnnotation(method, "Override"));
        assertTrue("@Deprecated should be captured: " + method.annotations(),
                hasAnnotation(method, "Deprecated"));
    }

    @Test
    public void fieldLevelAnnotationIsCaptured() throws Exception {
        final String java = String.join("\n",
                "package app;",
                "public class Foo {",
                "    @Autowired",
                "    private Bar bar;",
                "}");
        final Component field = component(compile("/app/Foo.java", java), "app.Foo.bar");
        assertTrue("@Autowired should be captured on the field: " + field.annotations(),
                hasAnnotation(field, "Autowired"));
    }

    @Test
    public void annotationWithMembersIsCapturedByName() throws Exception {
        final String java = String.join("\n",
                "package app;",
                "@RequestMapping(\"/x\")",
                "public class Foo {}");
        final Component foo = component(compile("/app/Foo.java", java), "app.Foo");
        assertTrue("@RequestMapping name should be captured even with members: " + foo.annotations(),
                hasAnnotation(foo, "RequestMapping"));
    }

    @Test
    public void multipleClassAnnotationsAreAllCaptured() throws Exception {
        final String java = String.join("\n",
                "package app;",
                "@Service",
                "@Validated",
                "public class Foo {}");
        final Component foo = component(compile("/app/Foo.java", java), "app.Foo");
        assertTrue("@Service should be captured: " + foo.annotations(), hasAnnotation(foo, "Service"));
        assertTrue("@Validated should be captured: " + foo.annotations(), hasAnnotation(foo, "Validated"));
    }

    @Test
    public void unannotatedClassHasEmptyNonNullAnnotations() throws Exception {
        final String java = String.join("\n",
                "package app;",
                "public class Foo {}");
        final Component foo = component(compile("/app/Foo.java", java), "app.Foo");
        assertNotNull("annotations() must never be null", foo.annotations());
        assertTrue("an un-annotated class has no annotations: " + foo.annotations(),
                foo.annotations().isEmpty());
    }

    @Test
    public void resolvableAnnotationIsCapturedByFqn() throws Exception {
        final String java = String.join("\n",
                "package app;",
                "import org.springframework.stereotype.Service;",
                "@Service",
                "public class Foo {}");
        final Component foo = component(compile("/app/Foo.java", java), "app.Foo");
        assertTrue("an imported annotation should resolve to its import FQN: " + foo.annotations(),
                foo.annotations().contains("org.springframework.stereotype.Service"));
        assertFalse("it should not also be recorded under its bare simple name: " + foo.annotations(),
                foo.annotations().contains("Service"));
    }

    /**
     * An annotation is matched by its simple name so the assertion holds whether the listener resolved
     * it to an import FQN ({@code org.springframework.stereotype.Service}) or fell back to the simple
     * name ({@code Service}).
     */
    private static boolean hasAnnotation(final Component component, final String simpleName) {
        return component.annotations().stream()
                .anyMatch(a -> a.equals(simpleName) || a.endsWith("." + simpleName));
    }

    private static Component component(final OOPSourceCodeModel model, final String uniqueName) {
        final Component component = model.copyOfComponent(uniqueName).orElse(null);
        assertNotNull("no component named " + uniqueName, component);
        return component;
    }

    private static OOPSourceCodeModel compile(final String path, final String content) throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile(path, content));
        final CompileResult result = new ClarpseProject(files, Lang.JAVA).result();
        assertTrue("fixture failed to compile: " + result.failures(), result.failures().isEmpty());
        return result.model();
    }
}
