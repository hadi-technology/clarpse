package com.hadi.clarpse.compiler.typescript;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hadi.clarpse.compiler.typescript.model.TypeScriptFileModel;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Manages the Node-based TypeScript daemon process and JSON-RPC transport.
 */
public final class TypeScriptDaemon implements AutoCloseable {

    private static final Logger LOGGER = LogManager.getLogger(TypeScriptDaemon.class);
    private static final String DAEMON_RESOURCE = "typescript/daemon.js";
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;
    private int nextId = 1;
    private Path tempDir;

    public void start() throws TypeScriptDaemonException {
        if (process != null && process.isAlive()) {
            return;
        }
        final String nodeCommand = NodeRuntime.resolveNodeCommand();
        if (nodeCommand == null) {
            throw new TypeScriptDaemonException("Node.js not available.");
        }
        final Path daemonScript = extractDaemonScript();
        final ProcessBuilder builder = new ProcessBuilder(nodeCommand, daemonScript.toString());
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        try {
            process = builder.start();
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        } catch (final IOException e) {
            throw new TypeScriptDaemonException("Failed to start TypeScript daemon.", 0, e);
        }
    }

    public InitResult initRepo(final String repoRoot) throws TypeScriptDaemonException {
        ensureStarted();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("repoRoot", repoRoot);
        JsonNode result = request("initRepo", params);
        return new InitResult(
                result.path("tsVersion").asText(""),
                result.path("configCount").asInt(0),
                result.path("fileCount").asInt(0)
        );
    }

    public TypeScriptFileModel getFileModel(final String filePath) throws TypeScriptDaemonException {
        ensureStarted();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("filePath", filePath);
        JsonNode result = request("getFileModel", params);
        try {
            return objectMapper.treeToValue(result, TypeScriptFileModel.class);
        } catch (final IOException e) {
            throw new TypeScriptDaemonException("Failed to parse file model.", 0, e);
        }
    }

    private synchronized JsonNode request(final String method, final JsonNode params)
            throws TypeScriptDaemonException {
        final int id = nextId++;
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        if (params != null) {
            request.set("params", params);
        }
        try {
            writer.write(request.toString());
            writer.newLine();
            writer.flush();
        } catch (final IOException e) {
            throw new TypeScriptDaemonException("Failed to write JSON-RPC request.", 0, e);
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
                if (response.has("error")) {
                    JsonNode error = response.get("error");
                    int code = error.path("code").asInt(0);
                    String message = error.path("message").asText("Unknown error");
                    throw new TypeScriptDaemonException(message, code);
                }
                return response.get("result");
            }
        } catch (final IOException e) {
            throw new TypeScriptDaemonException("Failed to read JSON-RPC response.", 0, e);
        }
        throw new TypeScriptDaemonException("TypeScript daemon terminated unexpectedly.");
    }

    private void ensureStarted() throws TypeScriptDaemonException {
        if (process == null || !process.isAlive()) {
            throw new TypeScriptDaemonException("TypeScript daemon is not running.");
        }
    }

    private Path extractDaemonScript() throws TypeScriptDaemonException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(DAEMON_RESOURCE)) {
            if (inputStream == null) {
                throw new TypeScriptDaemonException("Missing daemon resource: " + DAEMON_RESOURCE);
            }
            tempDir = Files.createTempDirectory("clarpse-ts-daemon");
            Path scriptPath = tempDir.resolve("daemon.js");
            Files.copy(inputStream, scriptPath);
            scriptPath.toFile().deleteOnExit();
            tempDir.toFile().deleteOnExit();
            return scriptPath;
        } catch (final IOException e) {
            throw new TypeScriptDaemonException("Failed to extract daemon resource.", 0, e);
        }
    }

    @Override
    public void close() {
        if (process != null && process.isAlive()) {
            try {
                request("shutdown", null);
            } catch (final TypeScriptDaemonException ignored) {
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
        private final String tsVersion;
        private final int configCount;
        private final int fileCount;

        public InitResult(final String tsVersion, final int configCount, final int fileCount) {
            this.tsVersion = tsVersion;
            this.configCount = configCount;
            this.fileCount = fileCount;
        }

        public String tsVersion() {
            return tsVersion;
        }

        public int configCount() {
            return configCount;
        }

        public int fileCount() {
            return fileCount;
        }
    }
}
