package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonExternalTypesTest {

    private static final String FIXTURE = "external-types";
    private static final String PACKAGE_PATH = "src";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    @Test
    public void testExternalBaseLabel() {
        String userName = PythonTestUtil.uniqueName(PACKAGE_PATH, "models", "User");
        ComponentReference ref = model.copyOfComponent(userName).orElseThrow()
                .references(TypeReferences.EXTENSION).get(0);
        Assert.assertEquals("pydantic.BaseModel", ref.invokedComponent());
    }

    @Test
    public void testExternalFieldLabel() {
        String fieldName = PythonTestUtil.uniqueName(PACKAGE_PATH, "models", "User.id");
        ComponentReference ref = model.copyOfComponent(fieldName).orElseThrow()
                .references(TypeReferences.SIMPLE).get(0);
        Assert.assertEquals("uuid.UUID", ref.invokedComponent());
    }
}
