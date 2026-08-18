package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonModuleDeclarationsTest {

    private static final String FIXTURE = "module-declarations";
    private static final String PACKAGE_PATH = "src/foo/bar";
    private static final String MODULE = "ops";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void testTopLevelFunctionEmitted() {
        String fnName = name(PythonTestUtil.signature("build", "LocalFoo", "foo: LocalFoo"));
        Component fn = model.copyOfComponent(fnName).orElseThrow();
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.FUNCTION, fn.componentType());
        Assert.assertEquals(MODULE, fn.module());
    }

    @Test
    public void testTopLevelFunctionInternalTypeReference() {
        String fnName = name(PythonTestUtil.signature("build", "LocalFoo", "foo: LocalFoo"));
        Component fn = model.copyOfComponent(fnName).orElseThrow();
        String expected = PythonTestUtil.uniqueName(PACKAGE_PATH, "types", "Foo");
        Assert.assertTrue(hasSimpleRef(fn, expected));
    }

    @Test
    public void testTopLevelModuleFieldEmittedAndResolved() {
        String fieldName = name("DEFAULT_FOO");
        Component field = model.copyOfComponent(fieldName).orElseThrow();
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.MODULE_FIELD, field.componentType());
        Assert.assertEquals(MODULE, field.module());
        String expected = PythonTestUtil.uniqueName(PACKAGE_PATH, "types", "Foo");
        Assert.assertTrue(hasSimpleRef(field, expected));
    }

    @Test
    public void testUnannotatedModuleFieldIsCaptured() {
        String fieldName = name("counter");
        Component field = model.copyOfComponent(fieldName).orElseThrow();
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.MODULE_FIELD, field.componentType());
    }

    @Test
    public void testExternalTypeLabelOnTopLevelFunction() {
        String fnName = name(PythonTestUtil.signature("parse_id", "UUID", "value: UUID"));
        Component fn = model.copyOfComponent(fnName).orElseThrow();
        Assert.assertTrue(hasSimpleRef(fn, "uuid.UUID"));
    }

    private static String name(final String symbolPath) {
        return PythonTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
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
