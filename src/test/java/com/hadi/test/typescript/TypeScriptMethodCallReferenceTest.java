package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class TypeScriptMethodCallReferenceTest {

    private static final String FIXTURE = "method-call-reference";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "MethodCallReference";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    @Test
    public void testMethodCallTypeReferenceFromReturnType() {
        String methodName = name("Test." + TypeScriptTestUtil.signature("m"));
        String listRef = name("List");
        assertTrue(model.getComponent(methodName).orElseThrow()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE)
                .contains(new SimpleTypeReference(listRef)));
    }

    private static String name(final String symbolPath) {
        return TypeScriptTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
