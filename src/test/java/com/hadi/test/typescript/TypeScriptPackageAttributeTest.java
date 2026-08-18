package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class TypeScriptPackageAttributeTest {

    private static final String FIXTURE = "package-attribute";
    private static final String PACKAGE_PATH = "src/com/clarity/test";
    private static final String MODULE = "Sample";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    @Test
    public void testClassAccuratePackageName() {
        Component cmp = model.copyOfComponent(name("SampleClass")).orElseThrow();
        assertTrue(cmp.pkg().path().equals(PACKAGE_PATH));
    }

    @Test
    public void testFieldVarAccuratePackageName() {
        Component cmp = model.copyOfComponent(name("SampleClass.sampleClassField")).orElseThrow();
        assertTrue(cmp.pkg().path().equals(PACKAGE_PATH));
    }

    @Test
    public void testMethodAccuratePackageName() {
        Component cmp = model.copyOfComponent(name("SampleClass." + TypeScriptTestUtil.signature("method")))
                .orElseThrow();
        assertTrue(cmp.pkg().name().equals(PACKAGE_PATH));
    }

    private static String name(final String symbolPath) {
        return TypeScriptTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
