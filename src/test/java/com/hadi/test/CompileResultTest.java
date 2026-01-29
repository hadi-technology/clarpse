package com.hadi.test;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.test.go.GoTestBase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CompileResultTest extends GoTestBase {

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

    @Test
    public void golangCompileFailuresTest() throws Exception {
        final String code = "package main\n  type person struct fwefwefewf {";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        assertEquals(1, parseService.result().failures().size());
    }

    @Test
    public void golangEmptyFileCompileFailuresTest() throws Exception {
        final String code = "";
        projectFiles.insertFile(new ProjectFile("/person.go", code));
        final ClarpseProject parseService = new ClarpseProject(projectFiles, Lang.GOLANG);
        assertEquals(1, parseService.result().failures().size());
    }

    @Test
    public void es6CompileFailuresTest() throws Exception {
        final String code = "class Polygon wefwfewf {  }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        assertEquals(1, parseService.result().failures().size());
    }

    @Test
    public void es6EmptyFileCompileFailuresTest() throws Exception {
        final String code = "";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        assertEquals(1, parseService.result().failures().size());
    }
}
