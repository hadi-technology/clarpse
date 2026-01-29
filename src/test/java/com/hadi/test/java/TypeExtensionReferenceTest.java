package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.Test;

/**
 * Ensure component type extensions invocations are accurate.
 */
public class TypeExtensionReferenceTest {

    @Test
    public void testAccurateExtendedTypes() throws Exception {
        final String code = "package com; \n public class ClassA extends ClassD { }";
        final String codeD = "package com; \n public class ClassD { public void test() { } }";
        OOPSourceCodeModel generatedSourceModel;
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/com/ClassA.java", code));
        rawData.insertFile(new ProjectFile("/com/ClassD.java", codeD));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        generatedSourceModel = parseService.result().model();
        Assert.assertEquals("com.ClassD", ((ComponentReference) generatedSourceModel.getComponent("com.ClassA").get().references()
                .toArray()[0]).invokedComponent());
    }

    @Test
    public void testAccurateExtendedTypesSize() throws Exception {
        final String code = "package com; \n public class ClassA extends ClassD { }";
        final String codeD = "package com; \n public class ClassD { }";
        OOPSourceCodeModel generatedSourceModel;
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/com/ClassA.java", code));
        rawData.insertFile(new ProjectFile("/com/ClassD.java", codeD));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        generatedSourceModel = parseService.result().model();
        Assert.assertEquals(1, generatedSourceModel.getComponent("com.ClassA").get().references().size());
    }

    @Test
    public void testAccurateExtendedTypesForNestedClass() throws Exception {
        final String code = "package com; \n public class ClassA { public class ClassB extends ClassD { } }";
        final String codeD = "package com; \n public class ClassD { }";
        OOPSourceCodeModel generatedSourceModel;
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/com/ClassA.java", code));
        rawData.insertFile(new ProjectFile("/com/ClassD.java", codeD));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        generatedSourceModel = parseService.result().model();
        Assert.assertEquals("com.ClassD", ((ComponentReference) generatedSourceModel.getComponent("com.ClassA.ClassB")
                .get().references().toArray()[0]).invokedComponent());

        Assert.assertEquals(1, generatedSourceModel.getComponent("com.ClassA.ClassB").get().references().size());
    }

    @Test
    public void testAccurateExtendedTypesSizeForNestedClass() throws Exception {
        final String code = "package com; \n public class ClassA { public class ClassB extends ClassD { } }";
        final String codeD = "package com; \n public class ClassD { }";
        OOPSourceCodeModel generatedSourceModel;
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/com/ClassA.java", code));
        rawData.insertFile(new ProjectFile("/com/ClassD.java", codeD));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        generatedSourceModel = parseService.result().model();
        Assert.assertEquals(1, generatedSourceModel.getComponent("com.ClassA.ClassB").get().references().size());
    }
}
