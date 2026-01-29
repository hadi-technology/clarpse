package com.hadi.test;

import com.hadi.clarpse.CommonDir;
import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Test;

import static org.junit.Assert.assertTrue;


public class RegressionTest {

    @Test
    public void shouldNotArrayOutOfBoundsException() throws Exception {
        assertTrue(new CommonDir("/test/lol/cakes", "/").value().equalsIgnoreCase("/"));
    }

    @Test
    public void parseSingleLineGoModFileDoesNotThrow() throws Exception {
        final String code = "module _";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/go.mod", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.GOLANG);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
    }
}
