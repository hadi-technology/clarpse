package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.TypeExtensionReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class TypeScriptTypeExtensionReferenceTest {

    private static final String FIXTURE = "type-extension-reference";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "TypeExtension";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    @Test
    public void testAccurateExtendedTypes() {
        ComponentReference ref = (ComponentReference) model.copyOfComponent(name("ClassA")).orElseThrow().references()
                .toArray()[0];
        Assert.assertEquals(name("ClassD"), ref.invokedComponent());
    }

    @Test
    public void testAccurateExtendedTypesSize() {
        Assert.assertEquals(1, model.copyOfComponent(name("ClassA")).orElseThrow().references().size());
    }

    @Test
    public void testAccurateExtendedTypesForAnotherClass() {
        ComponentReference ref = (ComponentReference) model.copyOfComponent(name("ClassE")).orElseThrow().references()
                .toArray()[0];
        Assert.assertEquals(name("ClassD"), ref.invokedComponent());
    }

    @Test
    public void testAccurateExtendedTypesSizeForAnotherClass() {
        Assert.assertEquals(1, model.copyOfComponent(name("ClassE")).orElseThrow().references().size());
    }

    @Test
    public void testInterfaceExtendsReference() {
        Assert.assertTrue(model.copyOfComponent(name("InterfaceA")).orElseThrow().references()
                .contains(new TypeExtensionReference(name("InterfaceD"))));
    }

    private static String name(final String symbolPath) {
        return TypeScriptTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
