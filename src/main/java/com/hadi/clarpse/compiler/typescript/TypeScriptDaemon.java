package com.hadi.clarpse.compiler.typescript;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hadi.clarpse.compiler.ClarpseProperties;
import com.hadi.clarpse.compiler.DaemonProcesses;
import com.hadi.clarpse.compiler.DaemonResourceExtractor;
import com.hadi.clarpse.compiler.NodeDaemonGate;
import com.hadi.clarpse.compiler.typescript.model.TypeScriptFileModel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages the Node-based TypeScript daemon process and JSON-RPC transport.
 */
public final class TypeScriptDaemon implements AutoCloseable {

    private static final Logger LOGGER = LogManager.getLogger(TypeScriptDaemon.class);
    private static final String DAEMON_RESOURCE = "typescript/daemon.js";
    private static final String TYPESCRIPT_BUNDLE_RESOURCE = "typescript/typescript-bundle.zip";
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);
    private static final String NODE_HEAP_SIZE_ENV = "CLARPSE_NODE_HEAP_SIZE";
    private static final String NODE_HEAP_SIZE_PROP = "clarpse.node.heapSize";
    private static final String MAX_PROGRAMS_ENV = "CLARPSE_TS_MAX_PROGRAMS";
    private static final String MAX_PROGRAMS_PROP = "clarpse.typescript.maxPrograms";
    private static final int DEFAULT_MAX_PROGRAMS = 2;

    /**
     * How many TypeScript programs the daemon may hold at once. A program retains every source file
     * it reaches and a type checker over them, so the count of resident programs - not the size of
     * any one of them - is what decides whether a repository with many tsconfig files fits in the
     * daemon's heap.
     *
     * @return The configured limit, or the default when none is set or the value is not a positive
     *     integer.
     */
    private static int resolveMaxPrograms() {
        final String systemProp = System.getProperty(MAX_PROGRAMS_PROP);
        final Integer fromProperty = parsePositiveInt(systemProp);
        if (fromProperty != null) {
            return fromProperty;
        }
        final Integer fromEnv = parsePositiveInt(System.getenv(MAX_PROGRAMS_ENV));
        if (fromEnv != null) {
            return fromEnv;
        }
        return ClarpseProperties.getInt(MAX_PROGRAMS_PROP, DEFAULT_MAX_PROGRAMS);
    }

    private static Integer parsePositiveInt(final String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            final int value = Integer.parseInt(raw.trim());
            if (value > 0) {
                return value;
            }
        } catch (final NumberFormatException e) {
            LOGGER.warn("Invalid TypeScript program limit '{}', using the default.", raw);
        }
        return null;
    }

    private static String resolveNodeHeapSize() {
        // System property overrides everything
        final String systemProp = System.getProperty(NODE_HEAP_SIZE_PROP);
        if (systemProp != null && !systemProp.trim().isEmpty()) {
            return systemProp.trim();
        }
        // Environment variable next
        final String envVar = System.getenv(NODE_HEAP_SIZE_ENV);
        if (envVar != null && !envVar.trim().isEmpty()) {
            return envVar.trim();
        }
        // Fall back to bundled properties file
        return String.valueOf(ClarpseProperties.getInt(NODE_HEAP_SIZE_PROP, 4096));
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    // Volatile: forceStop() reads these from the interrupt watchdog thread while the owning thread
    // is parked in request()'s readLine(). See #180.
    private volatile Process process;
    private volatile BufferedWriter writer;
    private volatile BufferedReader reader;
    private int nextId = 1;
    private Path tempDir;
    private boolean permitHeld;

    public void start() throws TypeScriptDaemonException {
        if (process != null && process.isAlive()) {
            return;
        }
        final String nodeCommand = NodeRuntime.resolveNodeCommand();
        if (nodeCommand == null) {
            throw new TypeScriptDaemonException("Node.js not available.",
                    TypeScriptDaemonException.CODE_NODE_NOT_FOUND);
        }
        final Path daemonScript = extractDaemonScript();
        final List<String> command = List.of(nodeCommand, "--max-old-space-size=" + resolveNodeHeapSize(), daemonScript.toString());
        final ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        // The daemon's heap allowance is large by necessity; what keeps a constrained
        // host alive is bounding how many daemons exist at once (see NodeDaemonGate).
        try {
            NodeDaemonGate.acquire();
        } catch (final IllegalStateException e) {
            throw new TypeScriptDaemonException(e.getMessage(),
                    TypeScriptDaemonException.CODE_DAEMON_ERROR, e);
        }
        permitHeld = true;
        try {
            process = builder.start();
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        } catch (final IOException e) {
            releasePermit();
            throw new TypeScriptDaemonException("Failed to start TypeScript daemon.",
                    TypeScriptDaemonException.CODE_DAEMON_ERROR, e);
        }
    }

    private void releasePermit() {
        if (permitHeld) {
            permitHeld = false;
            NodeDaemonGate.release();
        }
    }

    public InitResult initRepo(final String repoRoot) throws TypeScriptDaemonException {
        ensureStarted();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("repoRoot", repoRoot);
        params.put("maxPrograms", resolveMaxPrograms());
        JsonNode result = request("initRepo", params);
        return new InitResult(
                result.path("tsVersion").asText(""),
                result.path("configCount").asInt(0),
                result.path("fileCount").asInt(0),
                result.path("invalidConfigCount").asInt(0),
                parseInvalidConfigs(result.path("invalidConfigs")),
                result.path("residentProgramCount").asInt(0),
                result.path("maxPrograms").asInt(DEFAULT_MAX_PROGRAMS)
        );
    }

    public TypeScriptFileModel getFileModel(final String filePath) throws TypeScriptDaemonException {
        return getFileModel(filePath, false);
    }

    /**
     * The model of one file, optionally as a boundary file of a one-level analysis, whose bodies are
     * not read.
     *
     * @param filePath The file's path on disk.
     * @param boundary Whether the file is a level-one file.
     * @return The file's model.
     */
    public TypeScriptFileModel getFileModel(final String filePath, final boolean boundary)
            throws TypeScriptDaemonException {
        ensureStarted();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("filePath", filePath);
        if (boundary) {
            params.put("boundary", true);
        }
        JsonNode result = request("getFileModel", params);
        try {
            return objectMapper.treeToValue(result, TypeScriptFileModel.class);
        } catch (final IOException e) {
            throw new TypeScriptDaemonException("Failed to parse file model.",
                    TypeScriptDaemonException.CODE_DAEMON_ERROR, e);
        }
    }

    /**
     * The repository files the given files' module specifiers resolve to, closed over re-exports,
     * found by module resolution without building a program.
     *
     * @param focusFiles The analysed files' paths on disk.
     * @return The level-one files' paths on disk, sorted, excluding the analysed files.
     */
    public List<String> discoverLevelOne(final List<String> focusFiles) throws TypeScriptDaemonException {
        ensureStarted();
        final ObjectNode params = objectMapper.createObjectNode();
        final com.fasterxml.jackson.databind.node.ArrayNode focus = params.putArray("focusFiles");
        focusFiles.forEach(focus::add);
        final JsonNode result = request("discoverLevelOne", params);
        final List<String> levelOne = new ArrayList<>();
        result.path("levelOne").forEach(node -> levelOne.add(node.asText()));
        return levelOne;
    }

    /**
     * Switches the daemon to a one-level analysis of exactly the given files. Every later
     * {@link #getFileModel(String, boolean)} is answered from programs holding only these files.
     *
     * @param focusFiles    The analysed files' paths on disk.
     * @param levelOneFiles The level-one files' paths on disk.
     */
    public void planOneLevel(final List<String> focusFiles, final List<String> levelOneFiles)
            throws TypeScriptDaemonException {
        ensureStarted();
        final ObjectNode params = objectMapper.createObjectNode();
        final com.fasterxml.jackson.databind.node.ArrayNode focus = params.putArray("focusFiles");
        focusFiles.forEach(focus::add);
        final com.fasterxml.jackson.databind.node.ArrayNode levelOne = params.putArray("levelOneFiles");
        levelOneFiles.forEach(levelOne::add);
        request("planOneLevel", params);
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
            throw new TypeScriptDaemonException("Failed to write JSON-RPC request.",
                    TypeScriptDaemonException.CODE_DAEMON_ERROR, e);
        }
        try {
            String line;
            while (true) {
                // Between-lines cooperative cancellation; a stuck read is unblocked by forceStop()
                // from the interrupt watchdog. See #180.
                if (Thread.currentThread().isInterrupted()) {
                    forceStop();
                    throw new TypeScriptDaemonException("Interrupted while awaiting the daemon response.",
                            TypeScriptDaemonException.CODE_DAEMON_ERROR);
                }
                line = reader.readLine();
                if (line == null) {
                    break;
                }
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
                    if (error.has("data")) {
                        JsonNode data = error.get("data");
                        if (data != null && !data.isNull()) {
                            message = message + " (" + data.toString() + ")";
                        }
                    }
                    throw new TypeScriptDaemonException(message, code);
                }
                return response.get("result");
            }
        } catch (final IOException e) {
            throw new TypeScriptDaemonException("Failed to read JSON-RPC response.",
                    TypeScriptDaemonException.CODE_DAEMON_ERROR, e);
        }
        throw new TypeScriptDaemonException("TypeScript daemon terminated unexpectedly.",
                TypeScriptDaemonException.CODE_DAEMON_ERROR);
    }

    private void ensureStarted() throws TypeScriptDaemonException {
        if (process == null || !process.isAlive()) {
            throw new TypeScriptDaemonException("TypeScript daemon is not running.",
                    TypeScriptDaemonException.CODE_DAEMON_ERROR);
        }
    }

    private Path extractDaemonScript() throws TypeScriptDaemonException {
        try {
            DaemonResourceExtractor.Extraction extraction = DaemonResourceExtractor.extract(
                    getClass(),
                    "clarpse-ts-daemon",
                    DAEMON_RESOURCE,
                    TYPESCRIPT_BUNDLE_RESOURCE
            );
            tempDir = extraction.tempDir();
            return extraction.scriptPath();
        } catch (final IOException e) {
            throw new TypeScriptDaemonException("Failed to extract daemon resource.",
                    TypeScriptDaemonException.CODE_DAEMON_ERROR, e);
        }
    }

    @Override
    public void close() {
        if (process != null && process.isAlive()) {
            try {
                request("shutdown", null);
            } catch (final TypeScriptDaemonException ignored) {
            }
        }
        DaemonProcesses.terminate(process, SHUTDOWN_TIMEOUT);
        process = null;
        writer = null;
        reader = null;
        if (tempDir != null) {
            DaemonResourceExtractor.delete(tempDir);
            tempDir = null;
        }
        releasePermit();
    }

    /**
     * Kills the daemon process immediately, unblocking any thread parked in {@link #request} on a
     * read so a cancelled parse stops instead of running to completion out-of-process.
     *
     * <p>Deliberately NOT synchronized: the thread holding this monitor is the one blocked in
     * {@code readLine()}, so a synchronized {@code forceStop()} would deadlock against it. Called from
     * the {@link com.hadi.clarpse.compiler.InterruptWatchdog} on another thread; {@code
     * destroyForcibly()} and closing the pipe are thread-safe and are what makes the blocked read
     * return. {@link #close()} still runs afterward to release the temp dir and permit. See #180.
     */
    public void forceStop() {
        final Process current = process;
        if (current != null) {
            current.destroyForcibly();
        }
        closeQuietly(reader);
        closeQuietly(writer);
    }

    /** Whether the daemon process is currently running. Used to verify {@link #forceStop}. */
    public boolean isProcessAlive() {
        final Process current = process;
        return current != null && current.isAlive();
    }

    private static void closeQuietly(final java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (final IOException ignored) {
                // Best-effort: the daemon is being torn down.
            }
        }
    }

    public static final class InitResult {
        private final String tsVersion;
        private final int configCount;
        private final int fileCount;
        private final int invalidConfigCount;
        private final List<InvalidConfig> invalidConfigs;
        private final int residentProgramCount;
        private final int maxPrograms;

        public InitResult(final String tsVersion, final int configCount, final int fileCount,
                          final int invalidConfigCount, final List<InvalidConfig> invalidConfigs,
                          final int residentProgramCount, final int maxPrograms) {
            this.tsVersion = tsVersion;
            this.configCount = configCount;
            this.fileCount = fileCount;
            this.invalidConfigCount = invalidConfigCount;
            this.invalidConfigs = invalidConfigs;
            this.residentProgramCount = residentProgramCount;
            this.maxPrograms = maxPrograms;
        }

        /**
         * How many TypeScript programs the daemon holds after initialization.
         *
         * @return Resident program count, which is zero because programs are built on first use.
         */
        public int residentProgramCount() {
            return residentProgramCount;
        }

        /**
         * The daemon's ceiling on resident programs.
         *
         * @return The configured limit in effect for this repository.
         */
        public int maxPrograms() {
            return maxPrograms;
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

        public int invalidConfigCount() {
            return invalidConfigCount;
        }

        public List<InvalidConfig> invalidConfigs() {
            return invalidConfigs;
        }
    }

    public static final class InvalidConfig {
        private final String configPath;
        private final String error;

        public InvalidConfig(final String configPath, final String error) {
            this.configPath = configPath;
            this.error = error;
        }

        public String configPath() {
            return configPath;
        }

        public String error() {
            return error;
        }
    }

    private static List<InvalidConfig> parseInvalidConfigs(final JsonNode invalidConfigsNode) {
        if (invalidConfigsNode == null || !invalidConfigsNode.isArray() || invalidConfigsNode.isEmpty()) {
            return Collections.emptyList();
        }
        final List<InvalidConfig> invalidConfigs = new ArrayList<>();
        for (final JsonNode invalidConfigNode : invalidConfigsNode) {
            invalidConfigs.add(new InvalidConfig(
                    invalidConfigNode.path("configPath").asText(""),
                    invalidConfigNode.path("error").asText("")));
        }
        return Collections.unmodifiableList(invalidConfigs);
    }
}
