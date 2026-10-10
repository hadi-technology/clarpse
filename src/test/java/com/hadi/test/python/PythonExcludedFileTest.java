package com.hadi.test.python;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.FailureCode;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.test.CapturedLog;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.Assume;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A Python file under an excluded directory is recorded as not analysed, so a caller can tell it
 * from a file that was analysed and declared nothing.
 */
public class PythonExcludedFileTest {

    private static final String COMPILER_LOGGER =
            "com.hadi.clarpse.compiler.python.ClarpsePythonCompiler";

    private static ProjectFiles project() throws Exception {
        ProjectFiles projectFiles = new ProjectFiles();
        projectFiles.insertFile(new ProjectFile("/src/app.py", "class App:\n    pass\n"));
        projectFiles.insertFile(new ProjectFile("/src/empty.py", "# declares nothing\n"));
        projectFiles.insertFile(new ProjectFile("/build/lib/generated.py", "class Generated:\n    pass\n"));
        return projectFiles;
    }

    @Test
    public void anExcludedFileIsToldApartFromOneThatDeclaredNothing() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        CompileResult result = new ClarpseProject(project(), Lang.PYTHON).result();

        assertTrue(result.model().containsComponent(PythonTestUtil.uniqueName("src", "app", "App")));
        assertEquals(result.failures().toString(), 1, result.failures().size());
        CompileFailure failure = result.failures().iterator().next();
        assertEquals(Integer.valueOf(FailureCode.FILE_EXCLUDED), failure.errorCode());
        assertEquals("/build/lib/generated.py", failure.file().path());
    }

    /**
     * Exclusion is an expected outcome, so it is one line naming the file, below warning, with no
     * stack trace.
     */
    @Test
    public void anExcludedFileIsLoggedAsOneLineWithoutAStackTrace() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        List<LogEvent> aboutTheFile;
        List<LogEvent> allEvents;
        try (CapturedLog captured = new CapturedLog(COMPILER_LOGGER)) {
            new ClarpseProject(project(), Lang.PYTHON).result();
            aboutTheFile = captured.eventsMentioning("generated.py");
            allEvents = captured.events();
        }

        assertEquals(1, aboutTheFile.size());
        LogEvent event = aboutTheFile.get(0);
        assertNull("the line must carry no stack trace", event.getThrown());
        assertTrue("the level must not suggest a fault, was " + event.getLevel(),
                event.getLevel().isLessSpecificThan(Level.DEBUG));
        for (LogEvent anyEvent : allEvents) {
            assertNull("no compiler line may carry a stack trace for this project", anyEvent.getThrown());
        }
    }

    /**
     * Only the path within the project decides exclusion: a project that itself sits under a
     * directory named like an excluded one is read in full.
     */
    @Test
    public void aProjectUnderADirectoryNamedLikeAnExcludedOneIsRead() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        Path parent = Files.createTempDirectory("clarpse-py-excluded-ancestor");
        try {
            Path root = parent.resolve("build").resolve("project");
            Files.createDirectories(root.resolve("src"));
            Files.write(root.resolve("src/app.py"), "class App:\n    pass\n".getBytes(StandardCharsets.UTF_8));

            CompileResult result = new ClarpseProject(new ProjectFiles(root.toString()), Lang.PYTHON).result();

            assertTrue(result.model().containsComponent(PythonTestUtil.uniqueName("src", "app", "App")));
            assertTrue(result.failures().toString(), result.failures().isEmpty());
        } finally {
            FileUtils.deleteQuietly(parent.toFile());
        }
    }
}
