package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Optional;

public class TypeScriptComponentTypeTest {

    private static final String FIXTURE = "component-type";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "ComponentType";

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    @Test
    public void testSampleClassComponentType() {
        Optional<Component> cmp = model.copyOfComponent(name("SampleClass"));
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.CLASS.toString(),
                cmp.orElseThrow().componentType().toString());
    }

    @Test
    public void testSampleClassFieldComponentType() {
        Optional<Component> cmp = model.copyOfComponent(name("SampleClass.sampleClassField"));
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.FIELD.toString(),
                cmp.orElseThrow().componentType().toString());
    }

    @Test
    public void testSampleClassConstructorComponentType() {
        String ctorName = name("SampleClass." + TypeScriptTestUtil.signature("constructor", "string"));
        Optional<Component> cmp = model.copyOfComponent(ctorName);
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.CONSTRUCTOR.toString(),
                cmp.orElseThrow().componentType().toString());
    }

    @Test
    public void testSampleClassConstructorParamComponentType() {
        String paramName = name("SampleClass." + TypeScriptTestUtil.signature("constructor", "string")
                + ".sampleConstructorParam");
        Optional<Component> cmp = model.copyOfComponent(paramName);
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.CONSTRUCTOR_PARAMETER_COMPONENT.toString(),
                cmp.orElseThrow().componentType().toString());
    }

    @Test
    public void testSampleClassMethodComponentType() {
        String methodName = name("SampleClass." + TypeScriptTestUtil.signature("sampleMethod", "string", "object"));
        Optional<Component> cmp = model.copyOfComponent(methodName);
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.METHOD.toString(),
                cmp.orElseThrow().componentType().toString());
    }

    @Test
    public void testSampleClassMethodParamComponentType() {
        String paramName = name("SampleClass." + TypeScriptTestUtil.signature("sampleMethod", "string", "object")
                + ".sampleMethodParam");
        Optional<Component> cmp = model.copyOfComponent(paramName);
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.METHOD_PARAMETER_COMPONENT.toString(),
                cmp.orElseThrow().componentType().toString());
    }

    @Test
    public void testSampleClassMethodParam2ComponentType() {
        String paramName = name("SampleClass." + TypeScriptTestUtil.signature("sampleMethod", "string", "object")
                + ".sampleMethodParam2");
        Optional<Component> cmp = model.copyOfComponent(paramName);
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.METHOD_PARAMETER_COMPONENT.toString(),
                cmp.orElseThrow().componentType().toString());
    }

    @Test
    public void testSampleInterfaceComponentType() {
        Optional<Component> cmp = model.copyOfComponent(name("SampleInterface"));
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.INTERFACE.toString(),
                cmp.orElseThrow().componentType().toString());
    }

    @Test
    public void testSampleInterfaceMethodComponentType() {
        String methodName = name("SampleInterface." + TypeScriptTestUtil.signature("sampleInterfaceMethod", "string"));
        Optional<Component> cmp = model.copyOfComponent(methodName);
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.METHOD.toString(),
                cmp.orElseThrow().componentType().toString());
    }

    @Test
    public void testSampleInterfaceMethodParamComponentType() {
        String paramName = name("SampleInterface." + TypeScriptTestUtil.signature("sampleInterfaceMethod", "string")
                + ".sampleInterfaceMethodParam");
        Optional<Component> cmp = model.copyOfComponent(paramName);
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.METHOD_PARAMETER_COMPONENT.toString(),
                cmp.orElseThrow().componentType().toString());
    }

    @Test
    public void testSampleEnumComponentType() {
        Optional<Component> cmp = model.copyOfComponent(name("SampleEnum"));
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.ENUM.toString(),
                cmp.orElseThrow().componentType().toString());
    }

    @Test
    public void testSampleEnumConstantComponentType() {
        Optional<Component> cmp = model.copyOfComponent(name("SampleEnum.SampleEnumConstant"));
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.ENUM_CONSTANT.toString(),
                cmp.orElseThrow().componentType().toString());
    }

    @Test
    public void testTopLevelFunctionComponentType() {
        Optional<Component> cmp = model.copyOfComponent(name("topLevelFunction"));
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.FUNCTION.toString(),
                cmp.orElseThrow().componentType().toString());
    }

    private static String name(final String symbolPath) {
        return TypeScriptTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
