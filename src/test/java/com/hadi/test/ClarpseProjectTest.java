package com.hadi.test;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileException;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.hadi.test.ClarpseTestUtil.unzipArchive;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClarpseProjectTest {

    @Test
    public void testNoRelevantSourceFilesProvidedResultsInEmptyModel() throws CompileException {
        ProjectFiles projectFiles = new ProjectFiles();
        ProjectFile projectFile = new ProjectFile("/cakes.go", "{}");
        projectFiles.insertFile(projectFile);
        ClarpseProject cp = new ClarpseProject(projectFiles, Lang.JAVA);
        assertEquals(0, cp.result().model().size());
    }

    @Test
    public void testAnalysisFileFilterAppliedByClarpseProject() throws CompileException {
        ProjectFiles projectFiles = new ProjectFiles();
        projectFiles.insertFile(new ProjectFile("/kept.java", "class Kept {}"));
        projectFiles.insertFile(new ProjectFile("/ignored.java", "class Ignored {}"));

        CompileResult result = new ClarpseProject(projectFiles, Lang.JAVA, List.of("/kept.java")).result();

        assertTrue(result.model().getComponent("Kept").isPresent());
        assertFalse(result.model().getComponent("Ignored").isPresent());
        assertEquals(2, projectFiles.size());
    }
}
