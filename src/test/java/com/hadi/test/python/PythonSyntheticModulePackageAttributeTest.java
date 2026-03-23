package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonSyntheticModulePackageAttributeTest {

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
    public void testModuleFieldHasCorrectPackagePath() {
        Component cmp = model.getComponent(name("DEFAULT_FOO")).orElseThrow();
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.MODULE_FIELD, cmp.componentType());
        Assert.assertEquals(PACKAGE_PATH, cmp.pkg().path());
    }

    @Test
    public void testModuleFieldHasCorrectPackageName() {
        Component cmp = model.getComponent(name("DEFAULT_FOO")).orElseThrow();
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.MODULE_FIELD, cmp.componentType());
        Assert.assertEquals(PACKAGE_PATH, cmp.pkg().name());
    }

    @Test
    public void testModuleFieldUnannotatedHasCorrectPackagePath() {
        Component cmp = model.getComponent(name("counter")).orElseThrow();
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.MODULE_FIELD, cmp.componentType());
        Assert.assertEquals(PACKAGE_PATH, cmp.pkg().path());
    }

    @Test
    public void testModuleFieldUnannotatedHasCorrectPackageName() {
        Component cmp = model.getComponent(name("counter")).orElseThrow();
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.MODULE_FIELD, cmp.componentType());
        Assert.assertEquals(PACKAGE_PATH, cmp.pkg().name());
    }

    @Test
    public void testModuleFunctionHasCorrectPackagePath() {
        Component cmp = model.getComponent(name(PythonTestUtil.signature("build", "LocalFoo", "foo: LocalFoo")))
                .orElseThrow();
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.FUNCTION, cmp.componentType());
        Assert.assertEquals(PACKAGE_PATH, cmp.pkg().path());
    }

    @Test
    public void testModuleFunctionHasCorrectPackageName() {
        Component cmp = model.getComponent(name(PythonTestUtil.signature("build", "LocalFoo", "foo: LocalFoo")))
                .orElseThrow();
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.FUNCTION, cmp.componentType());
        Assert.assertEquals(PACKAGE_PATH, cmp.pkg().name());
    }

    @Test
    public void testModuleFunctionExternalTypeHasCorrectPackagePath() {
        Component cmp = model.getComponent(name(PythonTestUtil.signature("parse_id", "UUID", "value: UUID")))
                .orElseThrow();
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.FUNCTION, cmp.componentType());
        Assert.assertEquals(PACKAGE_PATH, cmp.pkg().path());
    }

    @Test
    public void testModuleFunctionExternalTypeHasCorrectPackageName() {
        Component cmp = model.getComponent(name(PythonTestUtil.signature("parse_id", "UUID", "value: UUID")))
                .orElseThrow();
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.FUNCTION, cmp.componentType());
        Assert.assertEquals(PACKAGE_PATH, cmp.pkg().name());
    }

    private static String name(final String symbolPath) {
        return PythonTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
