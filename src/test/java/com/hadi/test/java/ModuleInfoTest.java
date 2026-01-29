package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Tests related to module-info.java files.
 */
public class ModuleInfoTest {

    @Test
    public final void dontThrowExceptionWhileParsing() throws Exception {
        final String code = "package test; public class Test { }";
        final String codeB = "module lolcakes { requires test; }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/test.java", code));
        rawData.insertFile(new ProjectFile("/module-info.java", codeB));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
    }
}
