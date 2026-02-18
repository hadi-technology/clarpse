package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonRelativeImportTest {

    private static final String FIXTURE = "relative-parent-import";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void testParentRelativeImportResolvesClassExtension() {
        String childName = PythonTestUtil.uniqueName("src/pkg/sub", "child", "Child");
        ComponentReference ref = model.getComponent(childName).orElseThrow()
                .references(TypeReferences.EXTENSION).get(0);
        String baseName = PythonTestUtil.uniqueName("src/pkg", "base", "Base");
        Assert.assertEquals(baseName, ref.invokedComponent());
    }

    @Test
    public void testParentRelativeImportResolvesFieldType() {
        String fieldName = PythonTestUtil.uniqueName("src/pkg/sub", "child", "Child.value");
        Component field = model.getComponent(fieldName).orElseThrow();
        String baseName = PythonTestUtil.uniqueName("src/pkg", "base", "Base");
        Assert.assertTrue(hasSimpleRef(field, baseName));
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
