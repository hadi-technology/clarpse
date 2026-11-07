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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
    private static final String UNOWNED_FILES_ENV = "CLARPSE_TS_UNOWNED_FILES";
    private static final String UNOWNED_FILES_PROP = "clarpse.typescript.unownedFiles";
    private static final String REPORT_UNOWNED_FILES = "report";
    private static final int LOGGED_LINE_LENGTH = 200;
    /** Fires the per-request timeouts. One idle daemon thread serves every daemon. */
    private static final ScheduledExecutorService TIMEOUTS = Executors.newSingleThreadScheduledExecutor(task -> {
        final Thread thread = new Thread(task, "clarpse-ts-request-timeout");
        thread.setDaemon(true);
        return thread;
    });

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

    /**
     * Whether a file no config names is modelled, in a program of default options, or reported
     * as outside every program. Modelled unless the setting says {@code report}.
     *
     * @return {@code true} where such files are modelled.
     */
    private static boolean modelUnownedFiles() {
        String setting = System.getProperty(UNOWNED_FILES_PROP);
        if (setting == null || setting.trim().isEmpty()) {
            setting = System.getenv(UNOWNED_FILES_ENV);
        }
        return setting == null || !REPORT_UNOWNED_FILES.equalsIgnoreCase(setting.trim());
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
    private boolean strayLineLogged;
    private Path tempDir;
    private boolean permitHeld;
    private final Duration requestTimeout;
    private final Duration heavyTimeout;
    // Guards the pairing of a reply with the timeout armed for it: the timeout kills the daemon only
    // while a request is armed, and a reply cancels it under the same lock, so a timeout that fires
    // just after a reply cannot kill a daemon whose request has been answered.
    private final Object timeoutLock = new Object();
    private boolean timeoutArmed;
    private volatile boolean timedOut;
    // Whether the next getFileModel is the first since the daemon started, and so may build the
    // programs the later ones reuse.
    private boolean coldDaemon = true;
    // What a restarted daemon is brought back to.
    private String initializedRoot;
    private List<String> plannedFocus;
    private List<String> plannedLevelOne;

    /** A daemon whose requests are bounded by the configured timeouts. */
    public TypeScriptDaemon() {
        this(Duration.ofMillis((long) (1000 * TypeScriptLimits.seconds(TypeScriptLimits.REQUEST_TIMEOUT_PROP,
                        TypeScriptLimits.REQUEST_TIMEOUT_ENV, TypeScriptLimits.DEFAULT_REQUEST_TIMEOUT_SECONDS))),
                Duration.ofMillis((long) (1000 * TypeScriptLimits.seconds(TypeScriptLimits.HEAVY_TIMEOUT_PROP,
                        TypeScriptLimits.HEAVY_TIMEOUT_ENV, TypeScriptLimits.DEFAULT_HEAVY_TIMEOUT_SECONDS))));
    }

    /**
     * A daemon whose requests are bounded by the given timeouts. The daemon computes in one thread
     * and cannot report progress while it does, so a timeout can only be a fixed time; the heavy one
     * is for requests that read the whole repository or build programs from nothing.
     *
     * @param requestTimeout How long a request for one file's model may wait for an answer; zero or
     *                       less for no limit.
     * @param heavyTimeout   How long repository-wide requests, and the first file's model after the
     *                       daemon starts, may wait; zero or less for no limit.
     */
    public TypeScriptDaemon(final Duration requestTimeout, final Duration heavyTimeout) {
        this.requestTimeout = requestTimeout;
        this.heavyTimeout = heavyTimeout;
    }

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
        initializedRoot = repoRoot;
        plannedFocus = null;
        plannedLevelOne = null;
        ObjectNode params = objectMapper.createObjectNode();
        params.put("repoRoot", repoRoot);
        params.put("maxPrograms", resolveMaxPrograms());
        params.put("modelUnownedFiles", modelUnownedFiles());
        JsonNode result = request("initRepo", params);
        return new InitResult(
                result.path("tsVersion").asText(""),
                result.path("configCount").asInt(0),
                result.path("fileCount").asInt(0),
                result.path("invalidConfigCount").asInt(0),
                parseInvalidConfigs(result.path("invalidConfigs")),
                result.path("residentProgramCount").asInt(0),
                result.path("maxPrograms").asInt(DEFAULT_MAX_PROGRAMS)
        ).withConfigDiagnostics(parseConfigDiagnostics(result.path("configDiagnostics")))
                .withUnownedFiles(result.path("unownedProgramCount").asInt(0),
                        result.path("unownedFileCount").asInt(0));
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
        plannedFocus = List.copyOf(focusFiles);
        plannedLevelOne = List.copyOf(levelOneFiles);
    }

    /**
     * The program that holds each file, as the index of the tsconfig it was built from, or -1 for a
     * file no program holds. Files that share a program are cheapest asked for together.
     *
     * @param filePaths The files' paths on disk.
     * @return The owning config index of each file, keyed by the path as given.
     */
    public java.util.Map<String, Integer> ownersOf(final List<String> filePaths)
            throws TypeScriptDaemonException {
        ensureStarted();
        final ObjectNode params = objectMapper.createObjectNode();
        final com.fasterxml.jackson.databind.node.ArrayNode files = params.putArray("files");
        filePaths.forEach(files::add);
        final JsonNode owners = request("ownersOf", params).path("owners");
        final java.util.Map<String, Integer> result = new java.util.HashMap<>();
        owners.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asInt(-1)));
        return result;
    }

    /**
     * How many programs this daemon has built since the repository was initialised. A program is
     * costly to build, so this is what tells a daemon that rebuilds the same ones over and over from
     * one that builds each once.
     *
     * @return The number of programs built.
     */
    public int programBuilds() throws TypeScriptDaemonException {
        ensureStarted();
        return request("stats", null).path("programBuilds").asInt(0);
    }

    /**
     * Replaces a daemon that was killed, or is stuck, with a fresh one brought back to the state this
     * one was in: the same repository initialised, and the same one-level plan if one was made.
     * Programs are rebuilt on first use, so the cost is the rebuilding.
     *
     * @throws TypeScriptDaemonException where the replacement cannot be started or initialised.
     */
    public void restart() throws TypeScriptDaemonException {
        final String root = initializedRoot;
        final List<String> focus = plannedFocus;
        final List<String> levelOne = plannedLevelOne;
        if (root == null) {
            throw new TypeScriptDaemonException("TypeScript daemon was never initialised, so it cannot be restarted.",
                    TypeScriptDaemonException.CODE_DAEMON_ERROR);
        }
        close();
        coldDaemon = true;
        start();
        initRepo(root);
        if (focus != null) {
            planOneLevel(focus, levelOne);
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
            throw new TypeScriptDaemonException("Failed to write JSON-RPC request.",
                    TypeScriptDaemonException.CODE_DAEMON_ERROR, e);
        }
        final ScheduledFuture<?> timeout = armTimeout(method);
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
                final JsonNode response = replyIn(line);
                if (response == null || !response.has("id") || response.get("id").asInt() != id) {
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
                    settleReply();
                    throw new TypeScriptDaemonException(message, code);
                }
                settleReply();
                return response.get("result");
            }
        } catch (final IOException e) {
            if (timedOut) {
                throw requestTimeoutException();
            }
            throw new TypeScriptDaemonException("Failed to read JSON-RPC response.",
                    TypeScriptDaemonException.CODE_DAEMON_ERROR, e);
        } finally {
            disarmTimeout();
            if (timeout != null) {
                timeout.cancel(false);
            }
        }
        if (timedOut) {
            throw requestTimeoutException();
        }
        throw new TypeScriptDaemonException("TypeScript daemon terminated unexpectedly.",
                TypeScriptDaemonException.CODE_DAEMON_ERROR);
    }

    /**
     * The reply a line holds, or {@code null} where the line is not one.
     *
     * <p>The channel carries one reply to a line. Anything else on it was written by something
     * that is not the reply writer, and says nothing about the request: it is passed over, and
     * the first such line is logged so that its source can be found.
     *
     * @param line A line read from the daemon.
     * @return The reply, or {@code null}.
     */
    private JsonNode replyIn(final String line) {
        JsonNode reply = null;
        if (line.trim().startsWith("{")) {
            try {
                reply = objectMapper.readTree(line);
            } catch (final IOException e) {
                reply = null;
            }
        }
        if ((reply == null || !reply.isObject()) && !strayLineLogged) {
            strayLineLogged = true;
            LOGGER.warn("The TypeScript daemon wrote a line that is not a reply, which is passed "
                    + "over, as any more like it will be: {}",
                    line.substring(0, Math.min(line.length(), LOGGED_LINE_LENGTH)));
        }
        if (reply == null || !reply.isObject()) {
            return null;
        }
        return reply;
    }

    /**
     * Arms the kill that bounds the request about to be read. The kill is {@link #forceStop()}, the
     * only thing that frees a thread parked on the daemon's pipe.
     */
    private ScheduledFuture<?> armTimeout(final String method) {
        final Duration limit = limitFor(method);
        synchronized (timeoutLock) {
            timedOut = false;
            timeoutArmed = !limit.isZero() && !limit.isNegative();
        }
        if (!timeoutArmed) {
            return null;
        }
        return TIMEOUTS.schedule(() -> {
            synchronized (timeoutLock) {
                if (!timeoutArmed) {
                    return;
                }
                timeoutArmed = false;
                timedOut = true;
            }
            forceStop();
        }, limit.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Ends the timeout of a request that was answered, unless it had already fired. */
    private void settleReply() throws TypeScriptDaemonException {
        if (!disarmTimeout()) {
            throw requestTimeoutException();
        }
    }

    /**
     * Ends the armed timeout, because the request was answered or has ended.
     *
     * @return Whether the daemon was left alone; false where the timeout had already fired.
     */
    private boolean disarmTimeout() {
        synchronized (timeoutLock) {
            timeoutArmed = false;
            return !timedOut;
        }
    }

    private Duration limitFor(final String method) {
        if ("getFileModel".equals(method)) {
            if (coldDaemon) {
                coldDaemon = false;
                return heavyTimeout;
            }
            return requestTimeout;
        }
        if ("shutdown".equals(method) || "stats".equals(method)) {
            return requestTimeout;
        }
        return heavyTimeout;
    }

    private TypeScriptDaemonException requestTimeoutException() {
        awaitProcessExit();
        return new TypeScriptDaemonException("TypeScript daemon gave no answer within "
                + requestTimeout.toSeconds() + "s and was stopped.",
                TypeScriptDaemonException.CODE_REQUEST_TIMEOUT);
    }

    /**
     * Waits, for a bounded time, for the process killed by the timeout to be gone, so a caller told
     * the daemon was stopped can rely on it.
     */
    private void awaitProcessExit() {
        final Process current = process;
        try {
            if (current != null && !current.waitFor(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                LOGGER.warn("The TypeScript daemon was still running {}s after it was killed.",
                        SHUTDOWN_TIMEOUT.toSeconds());
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
        private List<ConfigDiagnostic> configDiagnostics = Collections.emptyList();
        private int unownedProgramCount;
        private int unownedFileCount;

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

        InitResult withConfigDiagnostics(final List<ConfigDiagnostic> diagnostics) {
            this.configDiagnostics = diagnostics;
            return this;
        }

        InitResult withUnownedFiles(final int programCount, final int fileCount) {
            this.unownedProgramCount = programCount;
            this.unownedFileCount = fileCount;
            return this;
        }

        /**
         * What the compiler said of the configs it read and used: an option it does not know, a
         * value it could not take. Each config is used with the options the compiler could read.
         *
         * @return The diagnostics, empty where every config was read whole.
         */
        public List<ConfigDiagnostic> configDiagnostics() {
            return configDiagnostics;
        }

        /**
         * How many programs stand in for a config over the files no config names.
         *
         * @return The count, zero where every file is named by a config or such files are reported.
         */
        public int unownedProgramCount() {
            return unownedProgramCount;
        }

        /**
         * How many files no config names.
         *
         * @return The count, zero where such files are reported and not modelled.
         */
        public int unownedFileCount() {
            return unownedFileCount;
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

    /** What the compiler said of a config it read. */
    public static final class ConfigDiagnostic {
        private final String configPath;
        private final int code;
        private final String message;

        public ConfigDiagnostic(final String configPath, final int code, final String message) {
            this.configPath = configPath;
            this.code = code;
            this.message = message;
        }

        public String configPath() {
            return configPath;
        }

        /**
         * The compiler's own number for the diagnostic.
         *
         * @return The number, zero where the compiler gave none.
         */
        public int code() {
            return code;
        }

        public String message() {
            return message;
        }
    }

    public static final class InvalidConfig {
        private final String configPath;
        private final String error;
        private final int code;
        private final String message;

        public InvalidConfig(final String configPath, final String error) {
            this(configPath, error, 0, "");
        }

        public InvalidConfig(final String configPath, final String error, final int code,
                             final String message) {
            this.configPath = configPath;
            this.error = error;
            this.code = code;
            this.message = message;
        }

        /**
         * The compiler's own number for what was wrong with the config.
         *
         * @return The number, zero where the compiler gave none.
         */
        public int code() {
            return code;
        }

        /**
         * What the compiler said was wrong with the config.
         *
         * @return The text, empty where the compiler gave none.
         */
        public String message() {
            return message;
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
                    invalidConfigNode.path("error").asText(""),
                    invalidConfigNode.path("code").asInt(0),
                    invalidConfigNode.path("message").asText("")));
        }
        return Collections.unmodifiableList(invalidConfigs);
    }

    private static List<ConfigDiagnostic> parseConfigDiagnostics(final JsonNode diagnosticsNode) {
        if (diagnosticsNode == null || !diagnosticsNode.isArray() || diagnosticsNode.isEmpty()) {
            return Collections.emptyList();
        }
        final List<ConfigDiagnostic> diagnostics = new ArrayList<>();
        for (final JsonNode diagnosticNode : diagnosticsNode) {
            diagnostics.add(new ConfigDiagnostic(
                    diagnosticNode.path("configPath").asText(""),
                    diagnosticNode.path("code").asInt(0),
                    diagnosticNode.path("message").asText("")));
        }
        return Collections.unmodifiableList(diagnostics);
    }
}
