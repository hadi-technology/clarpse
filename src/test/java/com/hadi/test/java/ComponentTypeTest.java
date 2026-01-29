package com.hadi.test.java;

import com.hadi.clarpse.ClarpseUtil;
import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Optional;

/**
 * Tests to ensure component type attribute of parsed components are accurate.
 */
public class ComponentTypeTest {

    private static OOPSourceCodeModel generatedSourceModel;
    private static final String sampleJavaClassComponentName;
    private static final OOPSourceModelConstants.ComponentType sampleJavaClassComponentType;
    private static final String sampleJavaClassFieldComponentName;
    private static final OOPSourceModelConstants.ComponentType sampleJavaClassFieldComponentType;
    private static final String sampleJavaMethodComponentName;
    private static final String sampleJavaMethodComponentKeyName;
    private static final OOPSourceModelConstants.ComponentType sampleJavaMethodComponentType;
    private static final String sampleJavaConstructorComponentName;
    private static final String sampleJavaConstructorComponentKeyName;
    private static final OOPSourceModelConstants.ComponentType sampleJavaConstructorComponentType;
    private static final String sampleJavaMethodParamComponentName;
    private static final OOPSourceModelConstants.ComponentType sampleJavaMethodParamComponentNameType;
    private static final String sampleJavaMethodParamComponent2Name;
    private static final OOPSourceModelConstants.ComponentType sampleJavaMethodParamComponent2NameType;
    private static final String sampleJavaInterfaceComponentName;
    private static final OOPSourceModelConstants.ComponentType sampleJavaInterfaceComponentType;
    private static final String sampleJavaInterfaceMethodComponentName;
    private static final String sampleJavaInterfaceMethodComponentKeyName;
    private static final OOPSourceModelConstants.ComponentType sampleJavaInterfaceMethodComponentType;
    private static final String sampleJavaInterfaceMethodParamComponentName;
    private static final OOPSourceModelConstants.ComponentType sampleJavaInterfaceMethodParamComponentType;
    private static final String sampleJavaEnumComponent;
    private static final OOPSourceModelConstants.ComponentType sampleJavaEnumComponentType;
    private static final String sampleJavaEnumClassConstant;
    private static final OOPSourceModelConstants.ComponentType sampleJavaEnumClassConstantType;
    private static final String sampleJavaEnumClassConstructor;
    private static final String sampleJavaEnumClassConstructorKey;
    private static final OOPSourceModelConstants.ComponentType sampleJavaEnumClassConstructorType;
    private static final String sampleJavaEnumMethodParam;
    private static final OOPSourceModelConstants.ComponentType sampleJavaEnumMethodParamType;
    private static final String sampleJavaPackageName;
    private static final String codeString;

    static {
        sampleJavaClassComponentName = "SampleJavaClass";
        sampleJavaClassComponentType = OOPSourceModelConstants.ComponentType.CLASS;
        sampleJavaClassFieldComponentName = "sampleJavaClassField";
        sampleJavaClassFieldComponentType = OOPSourceModelConstants.ComponentType.FIELD;
        sampleJavaMethodComponentName = "sampleJavaMethod";
        sampleJavaMethodComponentKeyName = "sampleJavaMethod(String, Object)";
        sampleJavaMethodComponentType = OOPSourceModelConstants.ComponentType.METHOD;
        sampleJavaConstructorComponentName = "sampleJavaClass";
        sampleJavaConstructorComponentKeyName = "sampleJavaClass()";
        sampleJavaConstructorComponentType = OOPSourceModelConstants.ComponentType.CONSTRUCTOR;
        sampleJavaMethodParamComponentName = "sampleJavaMethodParam";
        sampleJavaMethodParamComponentNameType = OOPSourceModelConstants.ComponentType.METHOD_PARAMETER_COMPONENT;
        sampleJavaMethodParamComponent2Name = "sampleJavaMethodParam2";
        sampleJavaMethodParamComponent2NameType = OOPSourceModelConstants.ComponentType.METHOD_PARAMETER_COMPONENT;
        sampleJavaInterfaceComponentName = "SampleJavaInterface";
        sampleJavaInterfaceComponentType = OOPSourceModelConstants.ComponentType.INTERFACE;
        sampleJavaInterfaceMethodComponentName = "sampleJavaInterfaceMethod";
        sampleJavaInterfaceMethodComponentKeyName = "sampleJavaInterfaceMethod(String)";
        sampleJavaInterfaceMethodComponentType = OOPSourceModelConstants.ComponentType.METHOD;
        sampleJavaInterfaceMethodParamComponentName = "sampleJavaInterfaceMethodParamComponent";
        sampleJavaInterfaceMethodParamComponentType = OOPSourceModelConstants.ComponentType.METHOD_PARAMETER_COMPONENT;
        sampleJavaEnumComponent = "SampleJavaEnumClass";
        sampleJavaEnumComponentType = OOPSourceModelConstants.ComponentType.ENUM;
        sampleJavaEnumClassConstant = "SampleJavaEnumClassConstant";
        sampleJavaEnumClassConstantType = OOPSourceModelConstants.ComponentType.ENUM_CONSTANT;
        sampleJavaEnumClassConstructor = "sampleJavaEnumClass";
        sampleJavaEnumClassConstructorKey = "sampleJavaEnumClass(String)";
        sampleJavaEnumClassConstructorType = OOPSourceModelConstants.ComponentType.CONSTRUCTOR;
        sampleJavaEnumMethodParam = "enumMethodParam";
        sampleJavaEnumMethodParamType = OOPSourceModelConstants.ComponentType.CONSTRUCTOR_PARAMETER_COMPONENT;
        sampleJavaPackageName = "SampleJavaPackage";
        codeString = "package " + sampleJavaPackageName + "; " + "class " + sampleJavaClassComponentName + " {"
                + "  private String " + sampleJavaClassFieldComponentName + ";" + "  private String "
                + sampleJavaMethodComponentName + " (final String " + sampleJavaMethodParamComponentName + ", Object..."
                + sampleJavaMethodParamComponent2Name + ") { " + "  } " + "  public "
                + sampleJavaConstructorComponentName + " () { " + "} " + "  interface "
                + sampleJavaInterfaceComponentName + " { " + "  	public void "
                + sampleJavaInterfaceMethodComponentName + "(String " + sampleJavaInterfaceMethodParamComponentName
                + " );" + "  }" + "  public enum " + sampleJavaEnumComponent + " { " + sampleJavaEnumClassConstant
                + "(\"\");" + sampleJavaEnumClassConstructor + "(final String " + sampleJavaEnumMethodParam + ") {}"
                + "  }" + "}";
    }

    @BeforeClass
    public static void parseJavaSourceFile() throws Exception {
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file1.java", codeString));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        generatedSourceModel = parseService.result().model();
    }

    @Test
    public final void testSampleJavaClassMethodParamComponentType() {
        final Optional<Component> tmp = generatedSourceModel.getComponent(sampleJavaPackageName + "." + sampleJavaClassComponentName + "."
                        + sampleJavaMethodComponentKeyName + "." + sampleJavaMethodParamComponentName);
        Assert.assertEquals(tmp.get().componentType().toString(),
                            sampleJavaMethodParamComponentNameType.toString());
    }

    @Test
    public final void testSampleJavaClassMethodParam2ComponentType() {
        final Optional<Component> tmp = generatedSourceModel.getComponent(sampleJavaPackageName + "." + sampleJavaClassComponentName + "."
                        + sampleJavaMethodComponentKeyName + "." + sampleJavaMethodParamComponent2Name);
        Assert.assertEquals(tmp.get().componentType().toString(),
                            sampleJavaMethodParamComponent2NameType.toString());
    }

    @Test
    public final void testSampleJavaClassConstructorComponentType() {
        final Optional<Component> tmp = generatedSourceModel.getComponent(sampleJavaPackageName + "."
                + sampleJavaClassComponentName + "." + sampleJavaConstructorComponentKeyName);
        Assert.assertTrue(tmp.get().componentType().toString().equals(sampleJavaConstructorComponentType.toString()));
    }

    @Test
    public final void testSampleJavaClassMethodComponentType() {
        final Optional<Component> tmp = generatedSourceModel.getComponent(sampleJavaPackageName + "."
                + sampleJavaClassComponentName + "." + sampleJavaMethodComponentKeyName);
        Assert.assertEquals(tmp.get().componentType().toString(),
                            sampleJavaMethodComponentType.toString());
    }

    @Test
    public final void testSampleJavaClassFieldComponentType() {
        final Optional<Component> tmp = generatedSourceModel.getComponent(sampleJavaPackageName + "."
                + sampleJavaClassComponentName + "." + sampleJavaClassFieldComponentName);
        Assert.assertEquals(tmp.get().componentType().toString(),
                            sampleJavaClassFieldComponentType.toString());
    }

    @Test
    public final void testSampleJavaClassComponentType() {
        final Optional<Component> tmp = generatedSourceModel.getComponent(sampleJavaPackageName + "." + sampleJavaClassComponentName);
        Assert.assertEquals(tmp.get().componentType().toString(),
                            sampleJavaClassComponentType.toString());
    }

    @Test
    public final void testSampleJavaInterfaceMethodParamComponentType() {
        final Optional<Component> tmp = generatedSourceModel.getComponent(sampleJavaPackageName + "." + sampleJavaClassComponentName + "."
                        + sampleJavaInterfaceComponentName + "." + sampleJavaInterfaceMethodComponentKeyName + "."
                        + sampleJavaInterfaceMethodParamComponentName);
        Assert.assertEquals(tmp.get().componentType().toString(),
                            sampleJavaInterfaceMethodParamComponentType.toString());
    }

    @Test
    public final void testSampleJavaInterfaceMethodComponentType() {
        final Optional<Component> tmp = generatedSourceModel.getComponent(sampleJavaPackageName + "." + sampleJavaClassComponentName + "."
                        + sampleJavaInterfaceComponentName + "." + sampleJavaInterfaceMethodComponentKeyName);
        Assert.assertEquals(tmp.get().componentType().toString(),
                            sampleJavaInterfaceMethodComponentType.toString());
    }

    @Test
    public final void testSampleJavaInterfaceComponentType() {
        final Optional<Component> tmp = generatedSourceModel.getComponent(sampleJavaPackageName + "."
                + sampleJavaClassComponentName + "." + sampleJavaInterfaceComponentName);
        Assert.assertEquals(tmp.get().componentType().toString(),
                            sampleJavaInterfaceComponentType.toString());
    }

    @Test
    public final void testSampleJavaEnumClassComponentType() {
        final Optional<Component> tmp = generatedSourceModel.getComponent(sampleJavaPackageName + "."
                + sampleJavaClassComponentName + "." + sampleJavaEnumComponent);
        Assert.assertEquals(tmp.get().componentType().toString(),
                            sampleJavaEnumComponentType.toString());
    }

    @Test
    public final void testSampleJavaEnumClassConstantComponentType() {
        final Optional<Component> tmp = generatedSourceModel.getComponent(sampleJavaPackageName + "."
                + sampleJavaClassComponentName + "." + sampleJavaEnumComponent + "." + sampleJavaEnumClassConstant);
        Assert.assertEquals(tmp.get().componentType().toString(),
                            sampleJavaEnumClassConstantType.toString());
    }

    @Test
    public final void testSampleJavaEnumClassMethodComponentType() {
        final Optional<Component> tmp = generatedSourceModel.getComponent(sampleJavaPackageName + "." + sampleJavaClassComponentName + "."
                        + sampleJavaEnumComponent + "." + sampleJavaEnumClassConstructorKey);
        Assert.assertEquals(tmp.get().componentType().toString(),
                            sampleJavaEnumClassConstructorType.toString());
    }

    @Test
    public final void testSampleJavaEnumClassMethodParamComponentType() {
        final Optional<Component> tmp = generatedSourceModel.getComponent(sampleJavaPackageName + "." + sampleJavaClassComponentName + "."
                        + sampleJavaEnumComponent + "." + sampleJavaEnumClassConstructorKey + "."
                        + sampleJavaEnumMethodParam);
        Assert.assertEquals(tmp.get().componentType().toString(),
                            sampleJavaEnumMethodParamType.toString());
    }
}
