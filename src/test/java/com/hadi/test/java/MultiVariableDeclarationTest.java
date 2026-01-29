package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MultiVariableDeclarationTest {

    @Test
    public void testLocalMultiVariableDoesNotCrossReference() throws Exception {
        final String code = "class Test { void m(){ Object a = new Foo(), b = new Bar(); } } class Foo {} class Bar {}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/Test.java", code));
        final OOPSourceCodeModel generatedSourceModel = new ClarpseProject(rawData, Lang.JAVA).result().model();

        final SimpleTypeReference fooRef = new SimpleTypeReference("Foo");
        final SimpleTypeReference barRef = new SimpleTypeReference("Bar");

        assertTrue(generatedSourceModel.getComponent("Test.m().a").get()
                .references(TypeReferences.SIMPLE).contains(fooRef));
        assertFalse(generatedSourceModel.getComponent("Test.m().a").get()
                .references(TypeReferences.SIMPLE).contains(barRef));

        assertTrue(generatedSourceModel.getComponent("Test.m().b").get()
                .references(TypeReferences.SIMPLE).contains(barRef));
        assertFalse(generatedSourceModel.getComponent("Test.m().b").get()
                .references(TypeReferences.SIMPLE).contains(fooRef));
    }
}
