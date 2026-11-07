package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/** A module-level alias of a class or union of classes is a type alias, and annotations see through it. */
public class PythonTypeAliasTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture("type-aliases");
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    private static Component shapes(final String name) {
        return model.copyOfComponent("src.shapes." + name)
                .orElseThrow(() -> new AssertionError("missing " + name));
    }

    private static boolean references(final Component from, final String to) {
        return from.internalDependencies().stream().anyMatch(r -> r.invokedComponent().equals(to));
    }

    private static Component canvas(final String method) {
        return model.components()
                .filter(c -> c.uniqueName().startsWith("src.canvas.Canvas." + method + "(")
                        && c.componentType() == ComponentType.METHOD)
                .findFirst().orElseThrow(() -> new AssertionError("missing " + method));
    }

    @Test
    public void unionAndClassAliasesAreTypeAliases() {
        for (String alias : new String[] {"Shape", "MaybeShape", "Either", "StateManager"}) {
            Assert.assertEquals(alias, ComponentType.TYPE_ALIAS, shapes(alias).componentType());
        }
    }

    @Test
    public void anAliasReferencesTheTypesItNames() {
        Assert.assertTrue(references(shapes("Shape"), "src.shapes.Circle"));
        Assert.assertTrue(references(shapes("Shape"), "src.shapes.Square"));
        Assert.assertTrue(references(shapes("Either"), "src.shapes.Square"));
        Assert.assertTrue(references(shapes("MaybeShape"), "src.shapes.Circle"));
        Assert.assertTrue(references(shapes("StateManager"), "src.shapes.LocalStateManager"));
    }

    @Test
    public void valuesThatAreNotTypesStayFields() {
        Assert.assertEquals(ComponentType.MODULE_FIELD, shapes("LIMIT").componentType());
        Assert.assertEquals(ComponentType.MODULE_FIELD, shapes("PRINTER").componentType());
    }

    @Test
    public void anAnnotationUsingAnAliasReferencesTheAliasAndItsMembers() {
        for (String method : new String[] {"draw", "maybe"}) {
            Assert.assertTrue(method, references(canvas(method), "src.shapes.Shape"));
            Assert.assertTrue(method, references(canvas(method), "src.shapes.Circle"));
            Assert.assertTrue(method, references(canvas(method), "src.shapes.Square"));
        }
        Assert.assertTrue(references(canvas("pick"), "src.shapes.Either"));
        Assert.assertTrue(references(canvas("pick"), "src.shapes.Circle"));
        Assert.assertTrue(references(canvas("manage"), "src.shapes.StateManager"));
        Assert.assertTrue(references(canvas("manage"), "src.shapes.LocalStateManager"));
    }
}
