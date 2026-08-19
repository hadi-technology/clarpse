package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TypeScriptReferenceClassificationTest {

    private static final String FIXTURE = "reference-classification";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "ReferenceClassification";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    @Test
    public void testInternalAndExternalDependencies() {
        Component ext = model.copyOfComponent(name("Test.ext")).orElseThrow();
        Component internal = model.copyOfComponent(name("Test.internal")).orElseThrow();

        SimpleTypeReference dateRef = new SimpleTypeReference("Date");
        SimpleTypeReference classBRef = new SimpleTypeReference(name("ClassB"));

        assertTrue(ext.references().contains(dateRef));
        assertTrue(ext.externalDependencies().contains(dateRef));
        assertFalse(ext.internalDependencies().contains(dateRef));
        assertTrue(ext.references().stream().anyMatch(ref ->
                "Date".equals(ref.invokedComponent()) && ref.isExternal()));

        assertTrue(internal.references().contains(classBRef));
        assertTrue(internal.internalDependencies().contains(classBRef));
        assertFalse(internal.externalDependencies().contains(classBRef));
    }

    private static String name(final String symbolPath) {
        return TypeScriptTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
