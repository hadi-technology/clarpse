package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonConstructorAssignedFieldsTest {

    private static final String FIXTURE = "constructor-assigned-fields";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "sample";

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void constructorAssignmentsAreModeledAsFields() {
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.FIELD,
                model.getComponent(name("Service.owner")).orElseThrow().componentType());
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.FIELD,
                model.getComponent(name("Service.team")).orElseThrow().componentType());
    }

    @Test
    public void nonSelfLocalVariablesAreNotModeledAsFields() {
        Assert.assertFalse(model.getComponent(name("Service.temporary")).isPresent());
    }

    @Test
    public void repeatedConstructorAssignmentsDoNotCreateExtraComponents() {
        long ownerCount = model.components()
                .filter(component -> component.uniqueName().equals(name("Service.owner")))
                .count();
        Assert.assertEquals(1, ownerCount);
    }

    @Test
    public void nestedClassConstructorAssignmentsAreModeledAsFields() {
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.FIELD,
                model.getComponent(name("Outer.Inner.inner_owner")).orElseThrow().componentType());
    }

    private static String name(final String symbolPath) {
        return PythonTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
