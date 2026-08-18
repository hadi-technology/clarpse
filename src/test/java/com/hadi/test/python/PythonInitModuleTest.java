package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonInitModuleTest {

    private static final String FIXTURE = "init-module";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void testInitPyIsParsedAsModule() {
        String className = PythonTestUtil.uniqueName("src/pkg", "__init__", "Root");
        Assert.assertTrue(model.containsComponent(className));
    }

    @Test
    public void testInitPyClassModuleAttribute() {
        String className = PythonTestUtil.uniqueName("src/pkg", "__init__", "Root");
        Component component = model.copyOfComponent(className).orElseThrow();
        Assert.assertEquals("__init__", component.module());
    }

    /**
     * A class defined in a package's {@code __init__.py} is importable as a member of the package
     * itself -- {@code from src.pkg import Root} -- because in Python {@code pkg/__init__.py} IS
     * the module {@code pkg}. The module index registered it only under {@code src.pkg.__init__},
     * so every such import missed and the reference was dropped.
     *
     * <p>Silent and load-bearing: it removes a base class from the graph for every module that
     * imports it. Measured on scrapy, where all 8 edges reaching an {@code __init__.py} class were
     * same-file and not one crossed a module -- {@code TextResponse} lost its {@code Response}
     * base, {@code JsonRequest} lost {@code Request}.
     */
    @Test
    public void testClassInInitPyIsImportableFromThePackage() {
        String childName = PythonTestUtil.uniqueName("src/pkg", "child", "Child");
        String rootName = PythonTestUtil.uniqueName("src/pkg", "__init__", "Root");
        Component child = model.copyOfComponent(childName).orElseThrow();

        Assert.assertFalse(child.references(TypeReferences.EXTENSION).isEmpty());
        ComponentReference ref = child.references(TypeReferences.EXTENSION).get(0);
        Assert.assertFalse(ref.isExternal());
        Assert.assertTrue(containsInvokedName(child.internalDependencies(), rootName));
    }

    /** The same import in a field annotation, which resolves by a different path. */
    @Test
    public void testFieldTypeFromInitPyResolvesToTheDeclaringClass() {
        String fieldName = PythonTestUtil.uniqueName("src/pkg", "holder", "Holder.root");
        String rootName = PythonTestUtil.uniqueName("src/pkg", "__init__", "Root");
        Component field = model.copyOfComponent(fieldName).orElseThrow();

        Assert.assertTrue(containsInvokedName(field.internalDependencies(), rootName));
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
