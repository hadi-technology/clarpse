package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/** Names declared at the top of the same module count as references wherever they are used. */
public class PythonModuleNameReferenceTest {

    private static final java.util.Set<OOPSourceModelConstants.ComponentType> DECLARATIONS = java.util.Set.of(
            OOPSourceModelConstants.ComponentType.FUNCTION, OOPSourceModelConstants.ComponentType.METHOD,
            OOPSourceModelConstants.ComponentType.MODULE_FIELD);

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture("module-name-references");
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    private static Component component(final String suffix) {
        return model.components()
                .filter(c -> c.uniqueName().startsWith("src.sample." + suffix)
                        && DECLARATIONS.contains(c.componentType()))
                .findFirst().orElseThrow(() -> new AssertionError("no component " + suffix));
    }

    private static boolean references(final String from, final String toPrefix) {
        return component(from).internalDependencies().stream()
                .anyMatch(r -> r.invokedComponent().startsWith(toPrefix));
    }

    @Test
    public void bareCallOfASameModuleFunctionIsAnEdge() {
        Assert.assertTrue(references("main(", "src.sample.helper("));
    }

    @Test
    public void bareCallFromAMethodIsAnEdge() {
        Assert.assertTrue(references("Runner.go(", "src.sample.helper("));
    }

    @Test
    public void aParameterShadowingTheFunctionIsNotAnEdge() {
        Assert.assertFalse(references("shadowed(", "src.sample.helper("));
    }

    @Test
    public void recursionIsNotAnEdge() {
        Assert.assertFalse(references("recursive(", "src.sample.recursive("));
    }

    @Test
    public void functionsNamedInAModuleLevelTupleAreReferencedByIt() {
        Assert.assertTrue(references("_PARSERS", "src.sample.helper("));
        Assert.assertTrue(references("_PARSERS", "src.sample.other("));
        Assert.assertFalse(references("_PARSERS", "src.sample.unrelated("));
        Assert.assertTrue(references("_NAMED", "src.sample.other("));
    }

    @Test
    public void aModuleLevelValueUsedInAFunctionIsAnEdge() {
        Assert.assertTrue(references("run(", "src.sample._PARSERS"));
    }
}
