package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class TypeScriptSimpleTypeReferenceTest {

    private static final String FIXTURE = "simple-type-reference";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE_MAIN = "SimpleTypeReference";
    private static final String MODULE_OTHER = "Other";
    private static final String MODULE_COLLECTIONS = "Collections";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    @Test
    public void testFieldVarTypeDeclaration() {
        ComponentReference invocation = model.copyOfComponent(name(MODULE_MAIN, "Test.fieldVar"))
                .orElseThrow().references(TypeReferences.SIMPLE).get(0);
        Assert.assertEquals("string", invocation.invokedComponent());
    }

    @Test
    public void testSelfReferenceShouldNotExist() {
        Assert.assertEquals(0, model.copyOfComponent(name(MODULE_MAIN, "SelfRef")).orElseThrow().references().size());
        String ctorName = name(MODULE_MAIN, "SelfRef." + TypeScriptTestUtil.signature("constructor"));
        Assert.assertEquals(0, model.copyOfComponent(ctorName).orElseThrow().references().size());
    }

    @Test
    public void testFieldVarImportTypeDeclaration() {
        ComponentReference invocation = model.copyOfComponent(name(MODULE_MAIN, "Test.importedField"))
                .orElseThrow().references(TypeReferences.SIMPLE).get(0);
        Assert.assertEquals(name(MODULE_OTHER, "ClassB"), invocation.invokedComponent());
    }

    @Test
    public void testFieldVarTypeDeclarationListSize() {
        Assert.assertEquals(1, model.copyOfComponent(name(MODULE_MAIN, "Test.fieldVar"))
                .orElseThrow().references(TypeReferences.SIMPLE).size());
    }

    @Test
    public void testResolveImportInFieldType() {
        Assert.assertEquals(1, model.copyOfComponent(name(MODULE_MAIN, "Test.importedField"))
                .orElseThrow().references(TypeReferences.SIMPLE).size());
    }

    @Test
    public void testMethodParamTypeDeclaration() {
        String methodName = name(MODULE_MAIN, "Test." + TypeScriptTestUtil.signature("method", "string", "number"));
        Assert.assertEquals("string", model.copyOfComponent(methodName + ".s1").orElseThrow()
                .references(TypeReferences.SIMPLE).get(0).invokedComponent());
    }

    @Test
    public void testMethodParamTypeDeclarationListSize() {
        String methodName = name(MODULE_MAIN, "Test." + TypeScriptTestUtil.signature("method", "string", "number"));
        Assert.assertEquals(1, model.copyOfComponent(methodName + ".s1").orElseThrow()
                .references(TypeReferences.SIMPLE).size());
        Assert.assertEquals(1, model.copyOfComponent(methodName + ".s2").orElseThrow()
                .references(TypeReferences.SIMPLE).size());
    }

    @Test
    public void testMethodLocalVarTypeDeclaration() {
        String methodName = name(MODULE_MAIN, "Test." + TypeScriptTestUtil.signature("method", "string", "number"));
        Assert.assertEquals("string", model.copyOfComponent(methodName + ".localVar").orElseThrow()
                .references(TypeReferences.SIMPLE).get(0).invokedComponent());
    }

    @Test
    public void testMethodLocalVarTypeDeclarationListSize() {
        String methodName = name(MODULE_MAIN, "Test." + TypeScriptTestUtil.signature("method", "string", "number"));
        Assert.assertEquals(1, model.copyOfComponent(methodName + ".localVar").orElseThrow()
                .references(TypeReferences.SIMPLE).size());
    }

    @Test
    public void testMethodCallStaticTypeReference() {
        String methodName = name(MODULE_MAIN, "Test." + TypeScriptTestUtil.signature("testStatic"));
        Assert.assertTrue(model.copyOfComponent(methodName).orElseThrow().references(TypeReferences.SIMPLE)
                .contains(new SimpleTypeReference(name(MODULE_MAIN, "Foo"))));
    }

    @Test
    public void testInstantiateObjectType() {
        String methodName = name(MODULE_MAIN, "Test." + TypeScriptTestUtil.signature("method", "string", "number"));
        Assert.assertTrue(model.copyOfComponent(methodName).orElseThrow().references(TypeReferences.SIMPLE)
                .contains(new SimpleTypeReference(name(MODULE_MAIN, "Foo"))));
    }

    @Test
    public void typeDeclarationArrayList() {
        String classAFieldB = name(MODULE_COLLECTIONS, "ClassA.b");
        String classAFieldC = name(MODULE_COLLECTIONS, "ClassA.c");
        Assert.assertEquals(3, model.copyOfComponent(classAFieldB).orElseThrow().references().size());
        Assert.assertEquals(3, model.copyOfComponent(classAFieldC).orElseThrow().references().size());
    }

    private static String name(final String moduleName, final String symbolPath) {
        return TypeScriptTestUtil.uniqueName(PACKAGE_PATH, moduleName, symbolPath);
    }
}
