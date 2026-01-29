package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests to ensure package name attribute of parsed components are correct.
 */
public class PackageAttributeTest {

    @Test
    public final void testClassAccuratePackageName() throws Exception {
        String pkgName = "com.clarity.test";
        String codeString = "package " + pkgName + "; class SampleJavaClass { private String " +
            "sampleClassField; }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file1.java", codeString));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        final Component cmp = generatedSourceModel.getComponent("com.clarity.test.SampleJavaClass").get();
        Assert.assertTrue(cmp.pkg().path().equals(pkgName));
    }

    @Test
    public final void testFieldVarAccuratePackageName() throws Exception {
        String pkgName = "com.clarity.test";
        String codeString = "package " + pkgName + ";   class SampleJavaClass {  private String " +
            "sampleClassField;  }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file1.java", codeString));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        final Component cmp = generatedSourceModel.getComponent("com.clarity.test.SampleJavaClass.sampleClassField").get();
        Assert.assertTrue(cmp.pkg().path().equals(pkgName));
    }

    @Test
    public final void testMethodAccuratePackageName() throws Exception {
        String pkgName = "com.clarity.test";
        String codeString = "package " + pkgName + ";   class SampleJavaClass { private String " +
            "method(){}  }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file1.java", codeString));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        final Component cmp = generatedSourceModel.getComponent("com.clarity.test.SampleJavaClass.method()").get();
        Assert.assertTrue(cmp.pkg().name().equals(pkgName));
    }
}
