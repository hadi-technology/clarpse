package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonMultilineImportTest {

    private static final String FIXTURE = "multiline-imports";
    private static final String PACKAGE_PATH = "src";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void testMultilineBaseImportResolvesInternal() {
        String className = PythonTestUtil.uniqueName(PACKAGE_PATH, "models", "Example");
        ComponentReference ref = model.getComponent(className).orElseThrow()
                .references(TypeReferences.EXTENSION).get(0);
        String fooName = PythonTestUtil.uniqueName(PACKAGE_PATH, "types", "Foo");
        Assert.assertEquals(fooName, ref.invokedComponent());
    }

    @Test
    public void testMultilineAliasImportResolvesInternal() {
        String fieldName = PythonTestUtil.uniqueName(PACKAGE_PATH, "models", "Example.second");
        Component field = model.getComponent(fieldName).orElseThrow();
        String barName = PythonTestUtil.uniqueName(PACKAGE_PATH, "types", "Bar");
        Assert.assertTrue(hasSimpleRef(field, barName));
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
