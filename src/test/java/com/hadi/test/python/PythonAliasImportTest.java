package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonAliasImportTest {

    private static final String FIXTURE = "alias-import";
    private static final String PACKAGE_PATH = "src";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void testAliasBaseResolvesInternal() {
        String childName = PythonTestUtil.uniqueName(PACKAGE_PATH, "child", "Child");
        ComponentReference ref = model.copyOfComponent(childName).orElseThrow()
                .references(TypeReferences.EXTENSION).get(0);
        String baseName = PythonTestUtil.uniqueName(PACKAGE_PATH, "base", "Base");
        Assert.assertEquals(baseName, ref.invokedComponent());
    }

    @Test
    public void testAliasFieldResolvesInternal() {
        String fieldName = PythonTestUtil.uniqueName(PACKAGE_PATH, "child", "Child.value");
        ComponentReference ref = model.copyOfComponent(fieldName).orElseThrow()
                .references(TypeReferences.SIMPLE).get(0);
        String baseName = PythonTestUtil.uniqueName(PACKAGE_PATH, "base", "Base");
        Assert.assertEquals(baseName, ref.invokedComponent());
    }
}
