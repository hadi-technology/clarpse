package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReferenceClassificationTest {

    @Test
    public void testInternalAndExternalDependencies() throws Exception {
        final String code = "import java.util.List; class Test { List<String> ext; ClassB internal; } class ClassB {}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/Test.java", code));
        final OOPSourceCodeModel generatedSourceModel = new ClarpseProject(rawData, Lang.JAVA).result().model();

        final Component ext = generatedSourceModel.getComponent("Test.ext").get();
        final Component internal = generatedSourceModel.getComponent("Test.internal").get();

        final SimpleTypeReference listRef = new SimpleTypeReference("java.util.List");
        final SimpleTypeReference classBRef = new SimpleTypeReference("ClassB");

        assertTrue(ext.references().contains(listRef));
        assertTrue(ext.externalDependencies().contains(listRef));
        assertFalse(ext.internalDependencies().contains(listRef));
        assertTrue(ext.references().stream().anyMatch(ref ->
                "java.util.List".equals(ref.invokedComponent()) && ref.isExternal()));

        assertTrue(internal.references().contains(classBRef));
        assertTrue(internal.internalDependencies().contains(classBRef));
        assertFalse(internal.externalDependencies().contains(classBRef));
    }
}
