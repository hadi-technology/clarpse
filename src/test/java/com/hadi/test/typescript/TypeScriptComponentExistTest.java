package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TypeScriptComponentExistTest {

    private static final String FIXTURE = "component-exist";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "ComponentExist";

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    @Test
    public void noTypeScriptFilesParsedTest() throws Exception {
        final ProjectFiles rawData = new ProjectFiles();
        final CompileResult result = new ClarpseProject(rawData, Lang.TYPESCRIPT).result();
        assertEquals(0, result.model().components().count());
    }

    @Test
    public void testSampleEnumConstantComponentExists() {
        Assert.assertTrue(model.containsComponent(name("SampleEnum.SampleEnumConstant")));
    }

    @Test
    public void testSampleEnumComponentExists() {
        Assert.assertTrue(model.containsComponent(name("SampleEnum")));
    }

    @Test
    public void testSampleInterfaceMethodParamComponentExists() {
        String methodName = name("SampleInterface." + TypeScriptTestUtil.signature("sampleInterfaceMethod", "string"));
        Assert.assertTrue(model.containsComponent(methodName + ".sampleInterfaceParam"));
    }

    @Test
    public void testSampleInterfaceMethodComponentExists() {
        String methodName = name("SampleInterface." + TypeScriptTestUtil.signature("sampleInterfaceMethod", "string"));
        Assert.assertTrue(model.containsComponent(methodName));
    }

    @Test
    public void testSampleInterfaceComponentExists() {
        Assert.assertTrue(model.containsComponent(name("SampleInterface")));
    }

    @Test
    public void testSampleClassMethodParamComponentExists() {
        String methodName = name("SampleClass." + TypeScriptTestUtil.signature("sampleMethod", "string"));
        Assert.assertTrue(model.containsComponent(methodName + ".sampleParam"));
    }

    @Test
    public void testSampleClassMethodComponentExists() {
        String methodName = name("SampleClass." + TypeScriptTestUtil.signature("sampleMethod", "string"));
        Assert.assertTrue(model.containsComponent(methodName));
    }

    @Test
    public void testSampleClassFieldComponentExists() {
        Assert.assertTrue(model.containsComponent(name("SampleClass.sampleField")));
    }

    @Test
    public void testSampleClassComponentExists() {
        Assert.assertTrue(model.containsComponent(name("SampleClass")));
    }

    private static String name(final String symbolPath) {
        return TypeScriptTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
