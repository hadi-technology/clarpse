package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonPyrightConfigExtraPathsTest {

    private static final String FIXTURE = "pyrightconfig-extra-paths";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void testPyrightConfigExtraPathsResolveInternalType() {
        String fieldName = PythonTestUtil.uniqueName("src", "service", "Service.current");
        Component field = model.copyOfComponent(fieldName).orElseThrow();
        String expectedUser = PythonTestUtil.uniqueName("libs/domain", "models", "User");
        Assert.assertTrue(hasSimpleRef(field, expectedUser));
    }

    private static boolean hasSimpleRef(final Component component, final String invokedName) {
        for (ComponentReference ref : component.references(TypeReferences.SIMPLE)) {
            if (invokedName.equals(ref.invokedComponent())) {
                return true;
            }
        }
        return false;
    }
}
