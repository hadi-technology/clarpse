package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class MethodCallReferenceTest {

    @Test
    public void testMethodCallTypeReferenceFromReturnType() throws Exception {
        final String code = "import java.util.List; class Test { List<String> getList(){ return null; } void m(){ getList().get(0); } }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/Test.java", code));
        final OOPSourceCodeModel generatedSourceModel = new ClarpseProject(rawData, Lang.JAVA).result().model();
        assertTrue(generatedSourceModel.getComponent("Test.m()").get()
                .references(TypeReferences.SIMPLE).contains(new SimpleTypeReference("java.util.List")));
    }
}
