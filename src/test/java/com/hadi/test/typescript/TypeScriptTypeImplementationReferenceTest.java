package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.TypeImplementationReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Test;
import org.junit.BeforeClass;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TypeScriptTypeImplementationReferenceTest {

    private static final String FIXTURE = "type-implementation-reference";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "TypeImplementation";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    @Test
    public void testAccurateImplementedTypes() {
        ComponentReference ref = (ComponentReference) model.copyOfComponent(name("ClassB")).orElseThrow().references()
                .toArray()[0];
        assertEquals(name("ClassD"), ref.invokedComponent());
        assertEquals(1, model.copyOfComponent(name("ClassB")).orElseThrow().references().size());
    }

    @Test
    public void testAccurateMultipleImplementedTypes() {
        assertTrue(model.copyOfComponent(name("ClassA")).orElseThrow().references(TypeReferences.IMPLEMENTATION)
                .contains(new TypeImplementationReference(name("ClassD"))));
        assertTrue(model.copyOfComponent(name("ClassA")).orElseThrow().references(TypeReferences.IMPLEMENTATION)
                .contains(new TypeImplementationReference(name("ClassE"))));
    }

    @Test
    public void testAccurateImplementedTypesSize() {
        assertEquals(1, model.copyOfComponent(name("ClassB")).orElseThrow().references().size());
    }

    @Test
    public void testAccurateMultipleImplementedTypesSize() {
        assertEquals(2, model.copyOfComponent(name("ClassA")).orElseThrow().references().size());
    }

    private static String name(final String symbolPath) {
        return TypeScriptTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
