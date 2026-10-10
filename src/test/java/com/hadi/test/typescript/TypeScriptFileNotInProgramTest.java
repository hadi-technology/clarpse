package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.compiler.typescript.TypeScriptDaemonException;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.test.CapturedLog;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.Assume;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TypeScriptFileNotInProgramTest {

    private static final String FIXTURE = "file-not-in-program";

    private static final String COMPILER_LOGGER =
            "com.hadi.clarpse.compiler.typescript.ClarpseTypeScriptCompiler";

    /**
     * The fixture's tsconfig names `Included.ts` and nothing else. `Ignored.ts` is a file no
     * config names, and is read all the same, in a program of default options.
     */
    @Test
    public void aFileNoConfigNamesIsModelled() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        ProjectFiles projectFiles = TypeScriptTestUtil.loadProject(FIXTURE);
        CompileResult result = new ClarpseProject(projectFiles, Lang.TYPESCRIPT).result();

        OOPSourceCodeModel model = result.model();
        assertTrue(model.copyOfComponent(
                TypeScriptTestUtil.uniqueName("src", "Included", "Included")).isPresent());
        assertTrue(model.copyOfComponent(
                TypeScriptTestUtil.uniqueName("src", "Ignored", "Ignored")).isPresent());
        assertTrue(result.failures().toString(), result.failures().isEmpty());
    }

    /**
     * Asked to report such files, the compile records `Ignored.ts` as a failure. It was once
     * skipped at DEBUG and recorded nowhere, which reaches a caller as a file that was parsed and
     * declared nothing.
     */
    @Test
    public void aFileInNoProgramIsRecordedAsAFailure() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        CompileResult result = TypeScriptTestUtil.compileReportingUnownedFiles(FIXTURE);

        OOPSourceCodeModel model = result.model();
        String includedName = TypeScriptTestUtil.uniqueName("src", "Included", "Included");
        assertTrue(model.copyOfComponent(includedName).isPresent());

        assertEquals(1, result.failures().size());
        CompileFailure failure = result.failures().iterator().next();
        assertEquals(Integer.valueOf(TypeScriptDaemonException.CODE_FILE_NOT_IN_PROGRAM),
                failure.errorCode());
        assertTrue(failure.file().path().endsWith("Ignored.ts"));
    }

    /**
     * A file left out of every program is what a tsconfig that names its files, or excludes a
     * directory of tests or tooling, produces for each file it leaves out. Nothing went wrong, so
     * the line that reports it names the file, is logged below warning, and carries no stack trace
     * for a reader to mistake for a crash.
     */
    @Test
    public void aFileInNoProgramIsLoggedWithoutAStackTrace() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        CompileResult result;
        List<LogEvent> aboutTheFile;
        List<LogEvent> allEvents;
        try (CapturedLog captured = new CapturedLog(COMPILER_LOGGER)) {
            result = TypeScriptTestUtil.compileReportingUnownedFiles(FIXTURE);
            aboutTheFile = captured.eventsMentioning("Ignored.ts");
            allEvents = captured.events();
        }

        assertEquals(1, result.failures().size());
        assertEquals(Integer.valueOf(TypeScriptDaemonException.CODE_FILE_NOT_IN_PROGRAM),
                result.failures().iterator().next().errorCode());

        assertEquals(1, aboutTheFile.size());
        LogEvent event = aboutTheFile.get(0);
        assertNull("the line must carry no stack trace", event.getThrown());
        assertTrue("the level must not suggest a fault, was " + event.getLevel(),
                event.getLevel().isLessSpecificThan(Level.DEBUG));
        for (LogEvent anyEvent : allEvents) {
            assertNull("no compiler line may carry a stack trace for this project",
                    anyEvent.getThrown());
        }
    }
}
