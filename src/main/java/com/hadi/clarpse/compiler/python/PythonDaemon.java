package com.hadi.clarpse.compiler.python;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hadi.clarpse.compiler.DaemonResourceExtractor;
import com.hadi.clarpse.compiler.python.model.PythonFileModel;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Manages the Node-based Python daemon process and JSON transport.
 */
public final class PythonDaemon implements AutoCloseable {

    private static final String DAEMON_RESOURCE = "python/daemon.js";
    private static final String PYRIGHT_BUNDLE_RESOURCE = "python/pyright-bundle.zip";
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;
    private int nextId = 1;
    private Path tempDir;

    public void start() throws PythonDaemonException {
        if (process != null && process.isAlive()) {
            return;
        }
        final String nodeCommand = NodeRuntime.resolveNodeCommand();
        if (nodeCommand == null) {
            throw new PythonDaemonException("Node.js not available.",
                    PythonDaemonException.CODE_NODE_NOT_FOUND);
        }
        final Path daemonScript = extractDaemonScript();
        final ProcessBuilder builder = new ProcessBuilder(nodeCommand, daemonScript.toString());
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        try {
            process = builder.start();
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        } catch (final IOException e) {
            throw new PythonDaemonException("Failed to start Python daemon.",
                    PythonDaemonException.CODE_RESOLVER_START_FAILED, e);
        }
    }

    public InitResult initRepo(final String repoRoot, final String pythonVersion) throws PythonDaemonException {
        ensureStarted();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("repoRoot", repoRoot);
        if (pythonVersion != null && !pythonVersion.isEmpty()) {
            ObjectNode options = objectMapper.createObjectNode();
            options.put("pythonVersion", pythonVersion);
            params.set("options", options);
        }
        JsonNode result = request("initRepo", params);
        List<String> warnings = new ArrayList<>();
        if (result.has("warnings") && result.get("warnings").isArray()) {
            for (JsonNode warning : result.get("warnings")) {
                warnings.add(warning.asText());
            }
        }
        return new InitResult(
                result.path("effectivePythonVersion").asText(""),
                result.path("configSource").asText(""),
                warnings
        );
    }

    public PythonFileModel getFileModel(final String filePath) throws PythonDaemonException {
        ensureStarted();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("filePath", filePath);
        JsonNode result = request("getFileModel", params);
        try {
            return objectMapper.treeToValue(result, PythonFileModel.class);
        } catch (final IOException e) {
            throw new PythonDaemonException("Failed to parse file model.",
                    PythonDaemonException.CODE_DAEMON_ERROR, e);
        }
    }

    private synchronized JsonNode request(final String op, final JsonNode params)
            throws PythonDaemonException {
        final int id = nextId++;
        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", id);
        request.put("op", op);
        if (params != null) {
            request.set("params", params);
        }
        try {
            writer.write(request.toString());
            writer.newLine();
            writer.flush();
        } catch (final IOException e) {
            throw new PythonDaemonException("Failed to write daemon request.",
                    PythonDaemonException.CODE_DAEMON_ERROR, e);
        }
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                JsonNode response = objectMapper.readTree(line);
                if (!response.has("id") || response.get("id").asInt() != id) {
                    continue;
                }
                if (!response.path("ok").asBoolean(false)) {
                    JsonNode error = response.path("error");
                    int code = error.path("code").asInt(0);
                    String message = error.path("message").asText("Unknown error");
                    throw new PythonDaemonException(message, code);
                }
                return response.get("result");
            }
        } catch (final IOException e) {
            throw new PythonDaemonException("Failed to read daemon response.",
                    PythonDaemonException.CODE_DAEMON_ERROR, e);
        }
        throw new PythonDaemonException("Python daemon terminated unexpectedly.",
                PythonDaemonException.CODE_DAEMON_ERROR);
    }

    private void ensureStarted() throws PythonDaemonException {
        if (process == null || !process.isAlive()) {
            throw new PythonDaemonException("Python daemon is not running.",
                    PythonDaemonException.CODE_DAEMON_ERROR);
        }
    }

    private Path extractDaemonScript() throws PythonDaemonException {
        try {
            DaemonResourceExtractor.Extraction extraction = DaemonResourceExtractor.extract(
                    getClass(),
                    "clarpse-py-daemon",
                    DAEMON_RESOURCE,
                    PYRIGHT_BUNDLE_RESOURCE
            );
            tempDir = extraction.tempDir();
            return extraction.scriptPath();
        } catch (final IOException e) {
            throw new PythonDaemonException("Failed to extract daemon resource.",
                    PythonDaemonException.CODE_DAEMON_ERROR, e);
        }
    }

    @Override
    public void close() {
        if (process != null && process.isAlive()) {
            try {
                request("shutdown", null);
            } catch (final PythonDaemonException ignored) {
            }
            try {
                if (!process.waitFor(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        process = null;
        writer = null;
        reader = null;
        if (tempDir != null) {
            FileUtils.deleteQuietly(tempDir.toFile());
            tempDir = null;
        }
    }

    public static final class InitResult {
        private final String effectivePythonVersion;
        private final String configSource;
        private final List<String> warnings;

        public InitResult(final String effectivePythonVersion, final String configSource,
                          final List<String> warnings) {
            this.effectivePythonVersion = effectivePythonVersion;
            this.configSource = configSource;
            this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        public String effectivePythonVersion() {
            return effectivePythonVersion;
        }

        public String configSource() {
            return configSource;
        }

        public List<String> warnings() {
            return warnings;
        }
    }
}
