package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.TypeImplementationReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Ensure component invocation data of a given class is accurate.
 */
public class TypeImplementationReferenceTest {

    @Test
    public void testAccurateImplementedTypes() throws Exception {
        final String code = "package com; \n public class ClassA implements ClassD { }";
        final String codeD = "package com; \n public interface ClassD  { }";
        final OOPSourceCodeModel generatedSourceModel;
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/com/ClassA.java", code));
        rawData.insertFile(new ProjectFile("/com/ClassD.java", codeD));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        generatedSourceModel = parseService.result().model();
        assertEquals("com.ClassD", ((ComponentReference) generatedSourceModel.getComponent("com.ClassA").get().references()
                .toArray()[0]).invokedComponent());
        assertEquals(1, generatedSourceModel.getComponent("com.ClassA").get().references().size());
    }

    @Test
    public void testAccurateMultipleImplementedTypes() throws Exception {
        final String code = "package com; \n public class ClassA implements ClassD, ClassE { }";
        final String codeD = "package com; \n public interface ClassD  { }";
        final String codeE = "package com; \n public interface ClassE { }";
        final OOPSourceCodeModel generatedSourceModel;
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/com/ClassA.java", code));
        rawData.insertFile(new ProjectFile("/com/ClassD.java", codeD));
        rawData.insertFile(new ProjectFile("/com/ClassE.java", codeE));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent(
            "com.ClassA")
                                       .get().references(
                TypeReferences.IMPLEMENTATION)
                                       .contains(new TypeImplementationReference("com.ClassD")));
        assertTrue(generatedSourceModel.getComponent("com.ClassA")
                                       .get().references(
                TypeReferences.IMPLEMENTATION)
                                       .contains(
                                           new TypeImplementationReference(
                                               "com.ClassE")));
    }

    @Test
    public void testAccurateImplementedTypesSize() throws Exception {
        final String code = "package com; \n public class ClassA implements ClassD { }";
        final String codeD = "package com; \n public interface ClassD  { }";
        final OOPSourceCodeModel generatedSourceModel;
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/com/ClassA.java", code));
        rawData.insertFile(new ProjectFile("/com/ClassD.java", codeD));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        generatedSourceModel = parseService.result().model();
        assertEquals(1, generatedSourceModel.getComponent("com.ClassA").get().references().size());
    }

    @Test
    public void testAccurateMultipleImplementedTypesSize() throws Exception {
        final String code = "package com; \n public class ClassA implements ClassD, ClassE { }";
        final String codeD = "package com; \n public interface ClassD  { }";
        final String codeE = "package com; \n public interface ClassE { }";
        final OOPSourceCodeModel generatedSourceModel;
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/com/ClassA.java", code));
        rawData.insertFile(new ProjectFile("/com/ClassD.java", codeD));
        rawData.insertFile(new ProjectFile("/com/ClassE.java", codeE));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        generatedSourceModel = parseService.result().model();
        assertEquals(2, generatedSourceModel.getComponent("com.ClassA").get().references().size());
    }

    @Test
    public void testAccurateImplementedTypesForNestedClass() throws Exception {
        final String code = "package com; \n public class ClassA {  class ClassB implements " +
            "ClassD{} }";
        final String codeD = "package com; \n public interface ClassD { }";
        final OOPSourceCodeModel generatedSourceModel;
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/com/ClassA.java", code));
        rawData.insertFile(new ProjectFile("/com/ClassD.java", codeD));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        generatedSourceModel = parseService.result().model();
        assertEquals("com.ClassD", ((ComponentReference) generatedSourceModel.getComponent("com.ClassA.ClassB")
                .get().references().toArray()[0]).invokedComponent());

        assertEquals(1, generatedSourceModel.getComponent("com.ClassA.ClassB").get().references().size());
    }

    @Test
    public void testAccurateImplementedTypesSizeForNestedClass() throws Exception {
        final String code = "package com; \n public class ClassA { class ClassB implements ClassD" +
            " { } }";
        final String codeD = "package com; \n public interface ClassD  { }";
        final OOPSourceCodeModel generatedSourceModel;
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/com/ClassA.java", code));
        rawData.insertFile(new ProjectFile("/com/ClassD.java", codeD));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        generatedSourceModel = parseService.result().model();
        assertEquals(1, generatedSourceModel.getComponent("com.ClassA.ClassB").get().references().size());
    }
}
