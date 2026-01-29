package com.hadi.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class JsonSerializationTest {

    @Test
    public void oopSourceCodeModelSerializesToJson() throws Exception {
        final String code = "class Test { void m(){ new String(); } }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/Test.java", code));
        final OOPSourceCodeModel model = new ClarpseProject(rawData, Lang.JAVA).result().model();
        final String json = new ObjectMapper().writeValueAsString(model);
        assertTrue(json.contains("\"components\""));
        assertTrue(json.contains("Test"));
        assertTrue(json.contains("\"invokedComponent\":\"java.lang.String\""));
        assertTrue(json.contains("\"type\":\"simple\""));
    }
}
