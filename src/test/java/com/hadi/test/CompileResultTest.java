package com.hadi.test;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CompileResultTest {

    @Test
    public void javaCompileFailuresTest() throws Exception {
        final String code = "invalid java code";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        assertEquals(1, parseService.result().failures().size());
    }

    @Test
    public void javaEmptyFileCompileFailuresTest() throws Exception {
        final String code = "";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file2.java", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVA);
        assertEquals(1, parseService.result().failures().size());
    }
}
