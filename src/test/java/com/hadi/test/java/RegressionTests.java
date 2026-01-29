package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.Assert.assertTrue;

public class RegressionTests {

    @Test
    public void testParameterizedTestIntegrationTestsClassIsParsedProperly_i111() throws Exception {
        final ProjectFiles rawData = new ProjectFiles();
        String javaCode =
            IOUtils.toString(Objects.requireNonNull(RegressionTests.class.getResourceAsStream(
                "/ParameterizedTestIntegrationTests.java")), StandardCharsets.UTF_8);
        rawData.insertFile(new ProjectFile("/ParameterizedTestIntegrationTests.java", javaCode));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("ParameterizedTestIntegrationTests"));
    }
}
