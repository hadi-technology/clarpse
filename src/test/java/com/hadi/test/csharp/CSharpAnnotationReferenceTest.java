package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A C# attribute ({@code [ApiController]}) is an applied annotation, so the parsed model records it
 * as a distinct {@code ANNOTATION}-kind {@link ComponentReference} on the component it decorates --
 * the same mechanism used for {@code extends}/{@code implements}. See issue #181.
 */
public class CSharpAnnotationReferenceTest {

    @Test
    public void classLevelAttributeIsCaptured() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/Foo.cs", "namespace Demo; [ApiController] public class Foo {}")
        ).model();
        final Component foo = component(model, "Demo.Foo");
        assertTrue("[ApiController] should be an annotation reference: " + annotations(foo),
                hasAnnotation(foo, "ApiController"));
    }

    @Test
    public void multipleClassAttributesAreAllCaptured() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/Foo.cs",
                        "namespace Demo; [ApiController] [Route(\"/x\")] public class Foo {}")
        ).model();
        final Component foo = component(model, "Demo.Foo");
        assertTrue("[ApiController] should be captured: " + annotations(foo), hasAnnotation(foo, "ApiController"));
        assertTrue("[Route] should be captured even with members: " + annotations(foo),
                hasAnnotation(foo, "Route"));
    }

    @Test
    public void methodLevelAttributeIsCaptured() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/Foo.cs",
                        "namespace Demo; public class Foo { [HttpGet] public void List() {} }")
        ).model();
        final Component method = component(model, "Demo.Foo.List()");
        assertTrue("[HttpGet] should be an annotation reference on the method: " + annotations(method),
                hasAnnotation(method, "HttpGet"));
    }

    @Test
    public void unannotatedClassHasNoAnnotationReferences() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/Foo.cs", "namespace Demo; public class Foo {}")
        ).model();
        final Component foo = component(model, "Demo.Foo");
        assertTrue("an un-attributed class has no annotation references: " + annotations(foo),
                foo.references(TypeReferences.ANNOTATION).isEmpty());
    }

    @Test
    public void attributeIsNotAlsoABaseTypeReference() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/Foo.cs", "namespace Demo; [ApiController] public class Foo {}")
        ).model();
        final Component foo = component(model, "Demo.Foo");
        assertFalse("an attribute must not leak into extension references: " + foo.references(),
                foo.references(TypeReferences.EXTENSION).stream()
                        .anyMatch(ref -> ref.invokedComponent().endsWith("ApiController")));
    }

    private static List<String> annotations(final Component component) {
        return component.references(TypeReferences.ANNOTATION).stream()
                .map(ComponentReference::invokedComponent)
                .collect(Collectors.toList());
    }

    private static boolean hasAnnotation(final Component component, final String simpleName) {
        return annotations(component).stream()
                .anyMatch(a -> a.equals(simpleName) || a.endsWith("." + simpleName));
    }

    private static Component component(final OOPSourceCodeModel model, final String uniqueName) {
        final Component component = model.copyOfComponent(uniqueName).orElse(null);
        assertNotNull("no component named " + uniqueName, component);
        return component;
    }
}
