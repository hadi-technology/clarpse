package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Basic tests to ensure components are being recognized and parsed.
 */
public class ComponentExistTest {

    private static final String sampleJavaClassComponentName = "SampleJavaClass";
    private static final String sampleJavaClassFieldComponentName = "sampleJavaClassField";
    private static final String sampleJavaInterfaceMethodParamComponentName = "sampleJavaInterfaceMethodParamComponent";
    private static final String sampleJavaEnumComponent = "SampleJavaEnumClass";
    private static final String sampleJavaEnumClassConstant = "SampleJavaEnumClassConstant";
    private static final String sampleJavaEnumMethodParam = "enumMethodParam";
    private static final String sampleJavaPackageName = "SampleJavaPackage";
    private static final String sampleJavaMethodParamComponentName = "sampleJavaMethodParam";
    private static final String sampleJavaInterfaceComponentName = "SampleJavaInterface";

    private static final String sampleJavaMethodComponentName = "sampleJavaMethod";
    private static final String sampleJavaMethodComponentKeyName = "sampleJavaMethod(String)";

    private static final String sampleJavaInterfaceMethodComponentName = "sampleJavaInterfaceMethod";
    private static final String sampleJavaInterfaceMethodComponentKeyName = "sampleJavaInterfaceMethod(String)";

    private static final String sampleJavaEnumClassConstructor = "sampleJavaEnumClass";
    private static final String sampleJavaEnumClassConstructurKey = "sampleJavaEnumClass(String)";

    private static final String codeString =
            "package " + sampleJavaPackageName + ";"
                    + "class " + sampleJavaClassComponentName  + " {"
                    + "  private String " + sampleJavaClassFieldComponentName + ";"
                    + "  private void " + sampleJavaMethodComponentName + " (final String " + sampleJavaMethodParamComponentName+ ") { "
                    + "      String cakes = testMethod(" + sampleJavaMethodParamComponentName +
                    ");"
                    + "  } "
                    + "  public void testMethod(String test) { } "
                    + "  interface " + sampleJavaInterfaceComponentName + " { "
                    + "      public void " + sampleJavaInterfaceMethodComponentName + "(String " + sampleJavaInterfaceMethodParamComponentName + " );"
                    + "  }"
                    + "  public enum " + sampleJavaEnumComponent + " { " + "  "
                    +      sampleJavaEnumClassConstant + "(\"\");" + "  "
                    +       sampleJavaEnumClassConstructor + "(final String " + sampleJavaEnumMethodParam + ") {}"
                    + "  }"
                    + " }";


    private static OOPSourceCodeModel generatedSourceModel;

    @BeforeClass
    public static void parseJavaSourceFile() throws Exception {
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file1.java", codeString));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        generatedSourceModel = parseService.result().model();
    }

    @Test
    public void noJavaFilesParsedTest() throws Exception {
        final ProjectFiles rawData = new ProjectFiles();
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals(0, generatedSourceModel.components().count());
    }

    @Test
    public final void testSampleJavaEnumClassMethodParamComponent() {
        Assert.assertTrue(generatedSourceModel.containsComponent(
                sampleJavaPackageName + "." + sampleJavaClassComponentName + "."
                        + sampleJavaEnumComponent + "." + sampleJavaEnumClassConstructurKey + "."
                        + sampleJavaEnumMethodParam));
    }

    @Test
    public final void testSampleJavaEnumClassConstructorComponentExists() {
        Assert.assertTrue(generatedSourceModel.containsComponent(
                sampleJavaPackageName + "." + sampleJavaClassComponentName + "."
                        + sampleJavaEnumComponent + "." + sampleJavaEnumClassConstructurKey));
    }

    @Test
    public final void testSampleJavaEnumClassConstantComponentExists() {
        Assert.assertTrue(generatedSourceModel.containsComponent(
                sampleJavaPackageName + "." + sampleJavaClassComponentName + "."
                        + sampleJavaEnumComponent + "." + sampleJavaEnumClassConstant));
    }

    @Test
    public final void testSampleJavaEnumClassComponentExists() {
        Assert.assertTrue(generatedSourceModel.containsComponent(
                sampleJavaPackageName + "."  + sampleJavaClassComponentName + "."
                        + sampleJavaEnumComponent));
    }

    @Test
    public final void testSampleJavaInterfaceMethodParamComponentExists() {
        Assert.assertTrue(generatedSourceModel.containsComponent(
                sampleJavaPackageName + "." + sampleJavaClassComponentName + "."
                        + sampleJavaInterfaceComponentName + "." + sampleJavaInterfaceMethodComponentKeyName + "."
                        + sampleJavaInterfaceMethodParamComponentName));
    }

    @Test
    public final void testSampleJavaInterfaceMethodComponentExists() {
        Assert.assertTrue(generatedSourceModel.containsComponent(
                sampleJavaPackageName + "." + sampleJavaClassComponentName + "."
                        + sampleJavaInterfaceComponentName + "." + sampleJavaInterfaceMethodComponentKeyName));
    }

    @Test
    public final void testSampleJavaInterfaceComponentExists() {
        Assert.assertTrue(generatedSourceModel.containsComponent(
                sampleJavaPackageName + "." + sampleJavaClassComponentName + "."
                        + sampleJavaInterfaceComponentName));
    }

    @Test
    public final void testSampleJavaClassMethodParamComponentExists() {
        Assert.assertTrue(generatedSourceModel.containsComponent(
                sampleJavaPackageName + "." + sampleJavaClassComponentName + "."
                        + sampleJavaMethodComponentKeyName + "." + sampleJavaMethodParamComponentName));
    }

    @Test
    public final void testSampleJavaClassMethodComponentExists() {

        Assert.assertTrue(generatedSourceModel.containsComponent(
                sampleJavaPackageName + "." + sampleJavaClassComponentName + "."
                        + sampleJavaMethodComponentKeyName));
    }

    @Test
    public final void testSampleJavaClassFieldComponentExists() {
        Assert.assertTrue(generatedSourceModel.containsComponent(
                sampleJavaPackageName + "." + sampleJavaClassComponentName + "."
                        + sampleJavaClassFieldComponentName));
    }

    @Test
    public final void testSampleJavaClassComponentExists() {
        Assert.assertTrue(generatedSourceModel.containsComponent(
                sampleJavaPackageName + "." + sampleJavaClassComponentName));
    }
}
