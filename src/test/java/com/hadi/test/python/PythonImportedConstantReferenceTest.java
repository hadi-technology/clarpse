package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/** A module-level name imported from another repo module is a reference when used bare. */
public class PythonImportedConstantReferenceTest {

    private static final java.util.Set<OOPSourceModelConstants.ComponentType> DECLARATIONS = java.util.Set.of(
            OOPSourceModelConstants.ComponentType.FUNCTION, OOPSourceModelConstants.ComponentType.METHOD,
            OOPSourceModelConstants.ComponentType.MODULE_FIELD);

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture("imported-constants");
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    private static boolean references(final String from, final String to) {
        final Component component = model.components()
                .filter(c -> c.uniqueName().startsWith("src.client." + from)
                        && DECLARATIONS.contains(c.componentType()))
                .findFirst().orElseThrow(() -> new AssertionError("no component " + from));
        return component.internalDependencies().stream()
                .anyMatch(r -> r.invokedComponent().startsWith(to));
    }

    @Test
    public void importedConstantUsedInAMethodIsAnEdge() {
        Assert.assertTrue(references("Client.send(", "src.errors.CODES"));
        Assert.assertTrue(references("Client.label(", "src.errors.LABEL"));
    }

    @Test
    public void importedConstantUsedInAFunctionIsAnEdge() {
        Assert.assertTrue(references("check(", "src.errors.CODES"));
    }

    @Test
    public void aParameterOrLocalShadowingTheConstantIsNotAnEdge() {
        Assert.assertFalse(references("Client.shadowed(", "src.errors.CODES"));
        Assert.assertFalse(references("local_binding(", "src.errors.LABEL"));
    }

    @Test
    public void importedFunctionCallStillResolves() {
        Assert.assertTrue(references("Client.makes(", "src.errors.make_error("));
    }
}
