package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/** Instance attributes are collected from every method and from any statement nesting. */
public class PythonInstanceAttributeScopeTest {

    private static final String FIXTURE = "instance-attributes-in-methods";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    private static void assertField(final String field) {
        Assert.assertEquals(field, OOPSourceModelConstants.ComponentType.FIELD,
                model.copyOfComponent(PythonTestUtil.uniqueName("src", "sample", "Estimator." + field))
                        .orElseThrow(() -> new AssertionError("missing field " + field)).componentType());
    }

    @Test
    public void attributesAssignedOutsideInitAreFields() {
        assertField("coef_");
        assertField("typed_");
        assertField("fitted_in_if");
        assertField("registry");
        assertField("with_inner");
    }

    @Test
    public void attributesNestedInInitBlocksAreFields() {
        for (String f : new String[] {"in_if", "in_else", "in_for", "in_with", "in_try", "in_except", "in_finally"}) {
            assertField(f);
        }
    }

    @Test
    public void tupleUnpackedAttributesAreFields() {
        for (String f : new String[] {"pair_a", "pair_b", "bare_a", "bare_b", "list_a", "list_b"}) {
            assertField(f);
        }
    }

    @Test
    public void typedAttributeKeepsItsType() {
        Assert.assertTrue(model.copyOfComponent(PythonTestUtil.uniqueName("src", "sample", "Estimator.typed_"))
                .orElseThrow().internalDependencies().stream()
                .anyMatch(r -> r.invokedComponent().endsWith("types.Item")));
    }

    @Test
    public void attributesOfOtherObjectsAreNotFields() {
        Assert.assertFalse(model.copyOfComponent(
                PythonTestUtil.uniqueName("src", "sample", "Estimator.not_a_field")).isPresent());
    }

    @Test
    public void repeatedAssignmentsCreateOneField() {
        Assert.assertEquals(1, model.components().filter(c -> c.uniqueName()
                .equals(PythonTestUtil.uniqueName("src", "sample", "Estimator.coef_"))).count());
    }
}
