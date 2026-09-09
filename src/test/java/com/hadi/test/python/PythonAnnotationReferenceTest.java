package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A Python decorator ({@code @dataclass}, {@code @app.route}) is an applied annotation, so the parsed
 * model records it as a distinct {@code ANNOTATION}-kind {@link ComponentReference} on the component
 * it decorates -- the same mechanism used for a base class's extension reference. See issue #181.
 */
public class PythonAnnotationReferenceTest {

    private static final String FIXTURE = "annotation-reference";
    private static final String PACKAGE_PATH = "src";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    @Test
    public void classLevelDecoratorIsCaptured() {
        final Component user = component(PythonTestUtil.uniqueName(PACKAGE_PATH, "models", "User"));
        Assert.assertTrue("@dataclass should be an annotation reference: " + annotations(user),
                hasAnnotation(user, "dataclass"));
    }

    @Test
    public void methodLevelDecoratorIsCaptured() {
        final Component label = byName("label");
        Assert.assertTrue("@property should be an annotation reference on the method: " + annotations(label),
                hasAnnotation(label, "property"));
    }

    @Test
    public void moduleFunctionDecoratorIsCaptured() {
        final Component handler = byName("handler");
        Assert.assertTrue("@register should be an annotation reference on the function: " + annotations(handler),
                hasAnnotation(handler, "register"));
    }

    @Test
    public void dottedDecoratorIsCapturedByTypeName() {
        final Component routed = byName("routed");
        Assert.assertTrue("@app.route should be captured as 'route': " + annotations(routed),
                hasAnnotation(routed, "route"));
    }

    @Test
    public void undecoratedComponentsHaveNoAnnotationReferences() {
        final Component plain = component(PythonTestUtil.uniqueName(PACKAGE_PATH, "models", "Plain"));
        Assert.assertTrue("an un-decorated class has no annotation references: " + annotations(plain),
                plain.references(TypeReferences.ANNOTATION).isEmpty());
        final Component ordinary = byName("ordinary");
        Assert.assertTrue("an un-decorated method has no annotation references: " + annotations(ordinary),
                ordinary.references(TypeReferences.ANNOTATION).isEmpty());
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

    private static Component component(final String uniqueName) {
        final Component component = model.copyOfComponent(uniqueName).orElse(null);
        Assert.assertNotNull("no component named " + uniqueName, component);
        return component;
    }

    private static Component byName(final String simpleName) {
        final Component component = model.components()
                .filter(c -> simpleName.equals(c.name()))
                .findFirst()
                .orElse(null);
        Assert.assertNotNull("no component with simple name " + simpleName, component);
        return component;
    }
}
