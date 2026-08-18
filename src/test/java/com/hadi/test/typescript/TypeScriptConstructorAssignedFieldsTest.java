package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class TypeScriptConstructorAssignedFieldsTest {

    private static final String FIXTURE = "constructor-assigned-fields";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "ConstructorFields";

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void constructorAssignmentPreservesDeclaredField() {
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.FIELD,
                model.copyOfComponent(name("Service.owner")).orElseThrow().componentType());
    }

    @Test
    public void constructorLocalVariableIsNotModeledAsField() {
        Assert.assertFalse(model.copyOfComponent(name("Service.temporary")).isPresent());
    }

    @Test
    public void constructorParameterIsStillModeled() {
        String ctor = "Service." + TypeScriptTestUtil.signature("constructor", "User");
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.CONSTRUCTOR_PARAMETER_COMPONENT,
                model.copyOfComponent(name(ctor + ".owner")).orElseThrow().componentType());
    }

    private static String name(final String symbolPath) {
        return TypeScriptTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
