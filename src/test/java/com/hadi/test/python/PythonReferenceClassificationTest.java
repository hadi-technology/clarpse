package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonReferenceClassificationTest {

    private static final String FIXTURE = "reference-classification";
    private static final String PACKAGE_PATH = "src";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void testClassExtensionReferenceIsInternal() {
        String childName = PythonTestUtil.uniqueName(PACKAGE_PATH, "models", "Child");
        Component child = model.copyOfComponent(childName).orElseThrow();
        ComponentReference ref = child.references(TypeReferences.EXTENSION).get(0);
        Assert.assertFalse(ref.isExternal());
        Assert.assertTrue(containsInvokedName(child.internalDependencies(),
                PythonTestUtil.uniqueName(PACKAGE_PATH, "base", "Base")));
    }

    @Test
    public void testExternalFieldReferenceIsExternal() {
        String fieldName = PythonTestUtil.uniqueName(PACKAGE_PATH, "models", "Child.id");
        Component field = model.copyOfComponent(fieldName).orElseThrow();
        ComponentReference ref = field.references(TypeReferences.SIMPLE).get(0);
        Assert.assertTrue(ref.isExternal());
        Assert.assertTrue(containsInvokedName(field.externalDependencies(), "uuid.UUID"));
    }

    @Test
    public void testInternalFieldReferenceIsInternal() {
        String fieldName = PythonTestUtil.uniqueName(PACKAGE_PATH, "models", "Child.parent");
        Component field = model.copyOfComponent(fieldName).orElseThrow();
        ComponentReference ref = field.references(TypeReferences.SIMPLE).get(0);
        Assert.assertFalse(ref.isExternal());
        Assert.assertTrue(containsInvokedName(field.internalDependencies(),
                PythonTestUtil.uniqueName(PACKAGE_PATH, "base", "Base")));
    }

    @Test
    public void testFunctionReturnReferenceIsInternal() {
        String fnName = PythonTestUtil.uniqueName(PACKAGE_PATH, "models",
                PythonTestUtil.signature("load", "Base", "value: UUID", "parent: Base"));
        Component fn = model.copyOfComponent(fnName).orElseThrow();
        Assert.assertTrue(containsInvokedName(fn.internalDependencies(),
                PythonTestUtil.uniqueName(PACKAGE_PATH, "base", "Base")));
    }

    @Test
    public void testFunctionExternalParamReferenceIsExternal() {
        String fnParamName = PythonTestUtil.uniqueName(PACKAGE_PATH, "models",
                PythonTestUtil.signature("load", "Base", "value: UUID", "parent: Base") + ".value");
        Component param = model.copyOfComponent(fnParamName).orElseThrow();
        Assert.assertTrue(containsInvokedName(param.externalDependencies(), "uuid.UUID"));
    }

    @Test
    public void testFunctionInternalParamReferenceIsInternal() {
        String fnParamName = PythonTestUtil.uniqueName(PACKAGE_PATH, "models",
                PythonTestUtil.signature("load", "Base", "value: UUID", "parent: Base") + ".parent");
        Component param = model.copyOfComponent(fnParamName).orElseThrow();
        Assert.assertTrue(containsInvokedName(param.internalDependencies(),
                PythonTestUtil.uniqueName(PACKAGE_PATH, "base", "Base")));
    }

    private static boolean containsInvokedName(final Iterable<ComponentReference> refs,
                                               final String invokedName) {
        for (ComponentReference ref : refs) {
            if (invokedName.equals(ref.invokedComponent())) {
                return true;
            }
        }
        return false;
    }
}
