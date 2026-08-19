package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonRelativeImportVariantsTest {

    private static final String FIXTURE = "relative-import-variants";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void testFromDotImportModuleAliasResolvesInternal() {
        String fieldName = PythonTestUtil.uniqueName("src/pkg", "service", "Service.current");
        Component field = model.copyOfComponent(fieldName).orElseThrow();
        Assert.assertTrue(hasSimpleRef(field, PythonTestUtil.uniqueName("src/pkg", "types", "User")));
    }

    @Test
    public void testFromParentImportModuleAliasResolvesInternal() {
        String fieldName = PythonTestUtil.uniqueName("src/pkg/sub", "handler", "Handler.first");
        Component field = model.copyOfComponent(fieldName).orElseThrow();
        Assert.assertTrue(hasSimpleRef(field, PythonTestUtil.uniqueName("src/pkg", "types", "User")));
    }

    @Test
    public void testFromParentDirectSymbolImportResolvesInternal() {
        String fieldName = PythonTestUtil.uniqueName("src/pkg/sub", "handler", "Handler.second");
        Component field = model.copyOfComponent(fieldName).orElseThrow();
        Assert.assertTrue(hasSimpleRef(field, PythonTestUtil.uniqueName("src/pkg", "types", "User")));
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
