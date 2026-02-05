package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TypeScriptMultiVariableDeclarationTest {

    private static final String FIXTURE = "multi-variable";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "MultiVariable";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    @Test
    public void testLocalMultiVariableDoesNotCrossReference() {
        String methodName = name("Test." + TypeScriptTestUtil.signature("m"));
        String fooRef = name("Foo");
        String barRef = name("Bar");

        SimpleTypeReference foo = new SimpleTypeReference(fooRef);
        SimpleTypeReference bar = new SimpleTypeReference(barRef);

        String aName = methodName + ".a";
        String bName = methodName + ".b";

        assertTrue(model.getComponent(aName).orElseThrow().references(TypeReferences.SIMPLE).contains(foo));
        assertFalse(model.getComponent(aName).orElseThrow().references(TypeReferences.SIMPLE).contains(bar));

        assertTrue(model.getComponent(bName).orElseThrow().references(TypeReferences.SIMPLE).contains(bar));
        assertFalse(model.getComponent(bName).orElseThrow().references(TypeReferences.SIMPLE).contains(foo));
    }

    private static String name(final String symbolPath) {
        return TypeScriptTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
