package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/** What must not become a reference or a type alias. */
public class PythonNegativeReferenceTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture("negative-references");
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    private static boolean references(final String function, final String target) {
        final Component component = model.components()
                .filter(c -> c.uniqueName().startsWith("src.sample." + function + "(")
                        && c.componentType() == ComponentType.FUNCTION)
                .findFirst().orElseThrow(() -> new AssertionError("no " + function));
        return component.internalDependencies().stream().anyMatch(r -> r.invokedComponent().startsWith(target));
    }

    @Test
    public void anInstanceAssignedAtModuleLevelIsNotATypeAlias() {
        Assert.assertEquals(ComponentType.MODULE_FIELD,
                model.copyOfComponent("src.sample.INSTANCE").orElseThrow().componentType());
        Assert.assertEquals(ComponentType.MODULE_FIELD,
                model.copyOfComponent("src.sample.COPY").orElseThrow().componentType());
    }

    @Test
    public void localNamesShadowingAModuleFunctionAreNotReferences() {
        for (String function : new String[] {"local_assignment", "comprehension", "nested_def", "nested_param"}) {
            Assert.assertFalse(function, references(function, "src.sample.helper("));
        }
    }

    /** A function-local import binds the name to the imported declaration, not to the module's own. */
    @Test
    public void aFunctionLocalImportReferencesTheImportedConstant() {
        Assert.assertTrue(references("local_import", "src.errors.LIMIT"));
        Assert.assertFalse(references("local_import", "src.sample.LIMIT"));
        Assert.assertTrue(references("local_import_constant", "src.errors.CODES"));
    }
}
