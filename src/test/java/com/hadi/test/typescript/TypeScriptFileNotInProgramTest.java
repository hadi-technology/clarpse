package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.compiler.typescript.TypeScriptDaemonException;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.junit.Assume;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TypeScriptFileNotInProgramTest {

    private static final String FIXTURE = "file-not-in-program";

    private static final String COMPILER_LOGGER =
            "com.hadi.clarpse.compiler.typescript.ClarpseTypeScriptCompiler";

    /**
     * The fixture's tsconfig names `Included.ts` and nothing else, so `Ignored.ts` belongs to no
     * program. It was skipped at DEBUG and recorded nowhere, which reaches a caller as a file that
     * was parsed and declared nothing.
     */
    @Test
    public void aFileInNoProgramIsRecordedAsAFailure() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        ProjectFiles projectFiles = TypeScriptTestUtil.loadProject(FIXTURE);
        CompileResult result = new ClarpseProject(projectFiles, Lang.TYPESCRIPT).result();

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
        ProjectFiles projectFiles = TypeScriptTestUtil.loadProject(FIXTURE);
        CapturingAppender captured = new CapturingAppender();

        CompileResult result;
        try (LogCapture ignored = new LogCapture(COMPILER_LOGGER, captured)) {
            result = new ClarpseProject(projectFiles, Lang.TYPESCRIPT).result();
        }

        assertEquals(1, result.failures().size());
        assertEquals(Integer.valueOf(TypeScriptDaemonException.CODE_FILE_NOT_IN_PROGRAM),
                result.failures().iterator().next().errorCode());

        List<LogEvent> aboutTheFile = captured.eventsMentioning("Ignored.ts");
        assertEquals(1, aboutTheFile.size());
        LogEvent event = aboutTheFile.get(0);
        assertNull("the line must carry no stack trace", event.getThrown());
        assertTrue("the level must not suggest a fault, was " + event.getLevel(),
                event.getLevel().isLessSpecificThan(Level.DEBUG));
        for (LogEvent anyEvent : captured.events()) {
            assertNull("no compiler line may carry a stack trace for this project",
                    anyEvent.getThrown());
        }
    }

    /** Routes one logger's events to an appender for the duration of a block. */
    private static final class LogCapture implements AutoCloseable {

        private final String loggerName;

        private LogCapture(final String loggerName, final CapturingAppender appender) {
            this.loggerName = loggerName;
            appender.start();
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            Configuration configuration = context.getConfiguration();
            LoggerConfig loggerConfig = new LoggerConfig(loggerName, Level.ALL, true);
            loggerConfig.addAppender(appender, Level.ALL, null);
            configuration.addLogger(loggerName, loggerConfig);
            context.updateLoggers();
        }

        @Override
        public void close() {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            context.getConfiguration().removeLogger(this.loggerName);
            context.updateLoggers();
        }
    }

    /** Keeps every event a logger emits, so a test can read what was logged and how. */
    private static final class CapturingAppender extends AbstractAppender {

        private final List<LogEvent> events = Collections.synchronizedList(new ArrayList<>());

        private CapturingAppender() {
            super("clarpse-test-capture", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(final LogEvent event) {
            this.events.add(event.toImmutable());
        }

        private List<LogEvent> events() {
            return new ArrayList<>(this.events);
        }

        private List<LogEvent> eventsMentioning(final String text) {
            List<LogEvent> matching = new ArrayList<>();
            for (LogEvent event : events()) {
                if (event.getMessage().getFormattedMessage().contains(text)) {
                    matching.add(event);
                }
            }
            return matching;
        }
    }
}
