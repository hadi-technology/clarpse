package com.hadi.test.typescript;

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
 * A TypeScript decorator ({@code @Component}, {@code @Injectable}, {@code @Input}) is an applied
 * annotation, so the parsed model records it as a distinct {@code ANNOTATION}-kind
 * {@link ComponentReference} on the component it decorates -- the same mechanism used for
 * {@code extends}/{@code implements}. See issue #181.
 */
public class TypeScriptAnnotationReferenceTest {

    private static final String FIXTURE = "annotation-reference";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    @Test
    public void classLevelDecoratorIsCaptured() {
        final Component widget = byName("Widget");
        Assert.assertTrue("@Component should be an annotation reference: " + annotations(widget),
                hasAnnotation(widget, "Component"));
    }

    @Test
    public void methodLevelDecoratorIsCaptured() {
        final Component render = byName("render");
        Assert.assertTrue("@Injectable should be an annotation reference on the method: " + annotations(render),
                hasAnnotation(render, "Injectable"));
    }

    @Test
    public void fieldLevelDecoratorIsCaptured() {
        final Component label = byName("label");
        Assert.assertTrue("@Input should be an annotation reference on the field: " + annotations(label),
                hasAnnotation(label, "Input"));
    }

    @Test
    public void undecoratedComponentsHaveNoAnnotationReferences() {
        final Component plain = byName("Plain");
        Assert.assertTrue("an un-decorated class has no annotation references: " + annotations(plain),
                plain.references(TypeReferences.ANNOTATION).isEmpty());
        final Component run = byName("run");
        Assert.assertTrue("an un-decorated method has no annotation references: " + annotations(run),
                run.references(TypeReferences.ANNOTATION).isEmpty());
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

    private static Component byName(final String simpleName) {
        final Component component = model.components()
                .filter(c -> simpleName.equals(c.name()))
                .findFirst()
                .orElse(null);
        Assert.assertNotNull("no component with simple name " + simpleName, component);
        return component;
    }
}
