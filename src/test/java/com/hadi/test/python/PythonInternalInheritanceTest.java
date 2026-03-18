package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.TypeExtensionReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonInternalInheritanceTest {

    private static final String FIXTURE = "internal-inheritance";
    private static final String PACKAGE_PATH = "src";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    @Test
    public void testChildExtendsBase() {
        String baseName = PythonTestUtil.uniqueName(PACKAGE_PATH, "base", "Base");
        String childName = PythonTestUtil.uniqueName(PACKAGE_PATH, "child", "Child");
        ComponentReference ref = model.getComponent(childName).orElseThrow()
                .references(TypeReferences.EXTENSION).get(0);
        Assert.assertEquals(new TypeExtensionReference(baseName), ref);
    }
}
