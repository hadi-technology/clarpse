package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonBackslashImportTest {

    private static final String FIXTURE = "backslash-import";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void testBackslashImportPrimaryNameResolvesInternal() {
        String fieldName = PythonTestUtil.uniqueName("src", "service", "Service.main");
        Component field = model.getComponent(fieldName).orElseThrow();
        Assert.assertTrue(hasSimpleRef(field, PythonTestUtil.uniqueName("src", "types", "User")));
    }

    @Test
    public void testBackslashImportAliasNameResolvesInternal() {
        String fieldName = PythonTestUtil.uniqueName("src", "service", "Service.alt");
        Component field = model.getComponent(fieldName).orElseThrow();
        Assert.assertTrue(hasSimpleRef(field, PythonTestUtil.uniqueName("src", "types", "User")));
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
