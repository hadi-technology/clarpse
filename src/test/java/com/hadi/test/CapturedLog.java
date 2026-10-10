package com.hadi.test;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Keeps every event one logger emits while it is open, at every level, so a test can read what was
 * logged and how.
 */
public final class CapturedLog implements AutoCloseable {

    private final String loggerName;
    private final List<LogEvent> events = Collections.synchronizedList(new ArrayList<>());

    /**
     * Starts capturing the named logger's events.
     *
     * @param loggerName The logger to capture.
     */
    public CapturedLog(final String loggerName) {
        this.loggerName = loggerName;
        final AbstractAppender appender = new AbstractAppender("clarpse-test-capture", null, null, true,
                Property.EMPTY_ARRAY) {
            @Override
            public void append(final LogEvent event) {
                CapturedLog.this.events.add(event.toImmutable());
            }
        };
        appender.start();
        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        final Configuration configuration = context.getConfiguration();
        final LoggerConfig loggerConfig = new LoggerConfig(loggerName, Level.ALL, true);
        loggerConfig.addAppender(appender, Level.ALL, null);
        configuration.addLogger(loggerName, loggerConfig);
        context.updateLoggers();
    }

    /** Every event captured so far. */
    public List<LogEvent> events() {
        return new ArrayList<>(this.events);
    }

    /** The captured events whose formatted message contains the given text. */
    public List<LogEvent> eventsMentioning(final String text) {
        final List<LogEvent> matching = new ArrayList<>();
        for (final LogEvent event : events()) {
            if (event.getMessage().getFormattedMessage().contains(text)) {
                matching.add(event);
            }
        }
        return matching;
    }

    @Override
    public void close() {
        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        context.getConfiguration().removeLogger(this.loggerName);
        context.updateLoggers();
    }
}
