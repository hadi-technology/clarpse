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
 * Tests to ensure module attribute of parsed components are correct.
 */
public class ModuleAttributeTest {

    @Test
    public final void testJavaComponentModuleName() throws Exception {
        String pkgName = "com.clarity.test";
        String codeString = "package " + pkgName + "; class SampleJavaClass { "
                + "private String sampleClassField; void method(){} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/com/clarity/test/SampleFile.java", codeString));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();

        final Component classCmp = generatedSourceModel.copyOfComponent("com.clarity.test.SampleJavaClass").get();
        Assert.assertEquals("SampleFile", classCmp.module());

        final Component fieldCmp = generatedSourceModel
                .copyOfComponent("com.clarity.test.SampleJavaClass.sampleClassField").get();
        Assert.assertEquals("SampleFile", fieldCmp.module());

        final Component methodCmp = generatedSourceModel
                .copyOfComponent("com.clarity.test.SampleJavaClass.method()").get();
        Assert.assertEquals("SampleFile", methodCmp.module());
    }
}
