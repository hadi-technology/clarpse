package com.hadi.test;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.ClarpseCompiler;
import com.hadi.clarpse.compiler.CompileException;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import org.junit.Test;

import java.util.List;

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

        assertTrue(result.model().copyOfComponent("Kept").isPresent());
        assertFalse(result.model().copyOfComponent("Ignored").isPresent());
        assertEquals(2, projectFiles.size());
    }

    @Test
    public void testAnalyzedFilesWithWindowsStylePathSeparators() throws CompileException {
        ProjectFiles projectFiles = new ProjectFiles();
        projectFiles.insertFile(new ProjectFile("/src/main/File1.java", "class File1 {}"));
        projectFiles.insertFile(new ProjectFile("/src/main/File2.java", "class File2 {}"));

        // Use Windows-style backslashes - should still match
        CompileResult result = new ClarpseProject(projectFiles, Lang.JAVA,
                List.of("src\\main\\File1.java")).result();

        assertTrue(result.model().copyOfComponent("File1").isPresent());
        assertFalse(result.model().copyOfComponent("File2").isPresent());
    }

    @Test
    public void testAnalyzedFilesWithLeadingSlash() throws CompileException {
        ProjectFiles projectFiles = new ProjectFiles();
        projectFiles.insertFile(new ProjectFile("/src/File.java", "class File {}"));

        // Both with and without leading slash should work
        CompileResult result = new ClarpseProject(projectFiles, Lang.JAVA,
                List.of("/src/File.java")).result();

        assertTrue(result.model().copyOfComponent("File").isPresent());
    }

    @Test
    public void testAnalyzedFilesWithTrailingSlash() throws CompileException {
        ProjectFiles projectFiles = new ProjectFiles();
        projectFiles.insertFile(new ProjectFile("/src/File.java", "class File {}"));

        // Trailing slash should be handled
        CompileResult result = new ClarpseProject(projectFiles, Lang.JAVA,
                List.of("/src/File.java/")).result();

        assertTrue(result.model().copyOfComponent("File").isPresent());
    }

    @Test
    public void testAnalyzedFilesEmptyCollectionReturnsNoFiles() throws CompileException {
        ProjectFiles projectFiles = new ProjectFiles();
        projectFiles.insertFile(new ProjectFile("/src/File.java", "class File {}"));

        // Empty collection should return empty result, not all files
        CompileResult result = new ClarpseProject(projectFiles, Lang.JAVA, List.of()).result();

        assertFalse(result.model().copyOfComponent("File").isPresent());
        assertEquals(0, result.model().size());
    }

    @Test
    public void testAnalyzedFilesNullReturnsAllFiles() throws CompileException {
        ProjectFiles projectFiles = new ProjectFiles();
        projectFiles.insertFile(new ProjectFile("/src/File1.java", "class File1 {}"));
        projectFiles.insertFile(new ProjectFile("/src/File2.java", "class File2 {}"));

        // null should analyze all files
        CompileResult result = new ClarpseProject(projectFiles, Lang.JAVA, null).result();

        assertTrue(result.model().copyOfComponent("File1").isPresent());
        assertTrue(result.model().copyOfComponent("File2").isPresent());
    }

    @Test
    public void testNormalizeForComparisonWithWindowsPath() {
        // Windows-style backslashes
        assertEquals("src/main/File.java",
                ClarpseCompiler.normalizeForComparison("src\\main\\File.java"));
        assertEquals("src/main/File.java",
                ClarpseCompiler.normalizeForComparison("\\src\\main\\File.java"));
    }

    @Test
    public void testNormalizeForComparisonWithLeadingTrailingSlashes() {
        // Leading slashes
        assertEquals("src/main/File.java",
                ClarpseCompiler.normalizeForComparison("/src/main/File.java"));
        assertEquals("src/main/File.java",
                ClarpseCompiler.normalizeForComparison("///src/main/File.java"));

        // Trailing slashes
        assertEquals("src/main/File.java",
                ClarpseCompiler.normalizeForComparison("src/main/File.java/"));
        assertEquals("src/main/File.java",
                ClarpseCompiler.normalizeForComparison("src/main/File.java///"));

        // Both
        assertEquals("src/main/File.java",
                ClarpseCompiler.normalizeForComparison("/src/main/File.java/"));
    }

    @Test
    public void testNormalizeForComparisonWithDotSegments() {
        // Path.normalize() should handle . and .. segments
        assertEquals("src/File.java",
                ClarpseCompiler.normalizeForComparison("src/./main/../File.java"));
    }

    @Test
    public void testNormalizeForComparisonWithNullAndEmpty() {
        assertEquals(null, ClarpseCompiler.normalizeForComparison(null));
        assertEquals("", ClarpseCompiler.normalizeForComparison(""));
    }
}
