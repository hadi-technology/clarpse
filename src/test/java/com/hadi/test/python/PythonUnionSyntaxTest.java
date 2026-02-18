package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonUnionSyntaxTest {

    private static final String FIXTURE = "union-syntax";
    private static final String PACKAGE_PATH = "src";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void testUnionTypeResolvesInternal() {
        String fieldName = PythonTestUtil.uniqueName(PACKAGE_PATH, "union", "Example.value");
        ComponentReference ref = model.getComponent(fieldName).orElseThrow()
                .references(TypeReferences.SIMPLE).get(0);
        String fooName = PythonTestUtil.uniqueName(PACKAGE_PATH, "types", "Foo");
        Assert.assertEquals(fooName, ref.invokedComponent());
    }
}
