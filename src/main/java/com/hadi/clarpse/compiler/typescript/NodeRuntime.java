package com.hadi.clarpse.compiler.typescript;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Node.js availability checks for TypeScript compilation.
 */
public final class NodeRuntime {

    private static final Logger LOGGER = LogManager.getLogger(NodeRuntime.class);
    private static final String NODE_PATH_PROP = "clarpse.node.path";
    private static final String NODE_DISABLED_PROP = "clarpse.node.disabled";
    private static final String NODE_PATH_ENV = "CLARPSE_NODE_PATH";
    private static final long VERSION_TIMEOUT_SECONDS = 5;

    private NodeRuntime() {
    }

    public static boolean isNodeAvailable() {
        return resolveNodeCommand() != null;
    }

    private static boolean isExplicitlyDisabled() {
        final String disabled = System.getProperty(NODE_DISABLED_PROP);
        return disabled != null && "true".equals(disabled.trim().toLowerCase(Locale.ROOT));
    }

    public static String resolveNodeCommand() {
        if (isExplicitlyDisabled()) {
            LOGGER.warn("Node.js is explicitly disabled via system property.");
            return null;
        }
        final String propertyOverride = System.getProperty(NODE_PATH_PROP);
        if (propertyOverride != null) {
            return resolveExplicitPath(propertyOverride, "system property");
        }
        final String envOverride = System.getenv(NODE_PATH_ENV);
        if (envOverride != null) {
            return resolveExplicitPath(envOverride, "environment variable");
        }
        if (canExecute("node")) {
            return "node";
        }
        return null;
    }

    private static boolean checkExplicitPath(final String value, final String sourceLabel) {
        String trimmed = value;
        if (trimmed == null) {
            trimmed = "";
        }
        trimmed = trimmed.trim();
        if (trimmed.isEmpty()) {
            LOGGER.warn("Node.js path override from " + sourceLabel + " is empty.");
            return false;
        }
        final File nodeFile = new File(trimmed);
        if (!nodeFile.isFile()) {
            LOGGER.warn("Node.js path override from " + sourceLabel + " is invalid: " + trimmed);
            return false;
        }
        return canExecute(nodeFile.getAbsolutePath());
    }

    private static String resolveExplicitPath(final String value, final String sourceLabel) {
        String trimmed = value;
        if (trimmed == null) {
            trimmed = "";
        }
        trimmed = trimmed.trim();
        if (trimmed.isEmpty()) {
            LOGGER.warn("Node.js path override from " + sourceLabel + " is empty.");
            return null;
        }
        final File nodeFile = new File(trimmed);
        if (!nodeFile.isFile()) {
            LOGGER.warn("Node.js path override from " + sourceLabel + " is invalid: " + trimmed);
            return null;
        }
        if (canExecute(nodeFile.getAbsolutePath())) {
            return nodeFile.getAbsolutePath();
        }
        return null;
    }

    private static boolean canExecute(final String command) {
        final ProcessBuilder processBuilder = new ProcessBuilder(command, "--version");
        processBuilder.redirectErrorStream(true);
        try {
            final Process process = processBuilder.start();
            boolean finished = process.waitFor(VERSION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            try (InputStream inputStream = process.getInputStream()) {
                inputStream.readAllBytes();
            }
            return process.exitValue() == 0;
        } catch (final IOException e) {
            LOGGER.warn("Failed to execute Node.js command.", e);
            return false;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while checking Node.js availability.", e);
            return false;
        }
    }
}
