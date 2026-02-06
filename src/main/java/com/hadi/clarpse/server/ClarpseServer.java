package com.hadi.clarpse.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileException;
import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ClarpseServer {

    private static final Logger LOGGER = LogManager.getLogger(ClarpseServer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int DEFAULT_PORT = 8080;
    private static final long DEFAULT_MAX_BYTES = 200L * 1024L * 1024L;
    private static final int READ_BUFFER_SIZE = 8192;

    private ClarpseServer() {
    }

    public static void main(final String[] args) throws IOException {
        final int port = readIntEnv("CLARPSE_PORT", DEFAULT_PORT);
        final long maxBytes = readLongEnv("CLARPSE_MAX_BYTES", DEFAULT_MAX_BYTES);
        final HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        final CountDownLatch stopLatch = new CountDownLatch(1);
        try (final AutoCloseableExecutor closeableExecutor =
                     new AutoCloseableExecutor(Executors.newFixedThreadPool(resolveThreadCount()))) {
            final ExecutorService executor = closeableExecutor.service();
            server.setExecutor(executor);
            server.createContext("/health", new HealthHandler());
            server.createContext("/parse", new ParseHandler(maxBytes));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    server.stop(0);
                } finally {
                    stopLatch.countDown();
                }
            }));

            try {
                server.start();
                LOGGER.info("Clarpse server listening on port " + port + ".");
                stopLatch.await();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                server.stop(0);
            }
        }
    }


    private static int resolveThreadCount() {
        final int processors = Runtime.getRuntime().availableProcessors();
        if (processors <= 1) {
            return 2;
        }
        return processors;
    }

    private static int readIntEnv(final String key, final int defaultValue) {
        final String raw = System.getenv(key);
        if (raw == null || raw.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (final NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long readLongEnv(final String key, final long defaultValue) {
        final String raw = System.getenv(key);
        if (raw == null || raw.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (final NumberFormatException e) {
            return defaultValue;
        }
    }

    private static final class HealthHandler implements HttpHandler {
        @Override
        public void handle(final HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "MethodNotAllowed", "Only GET is supported.");
                return;
            }
            sendText(exchange, 200, "OK");
        }
    }

    private static final class ParseHandler implements HttpHandler {
        private final long maxBytes;

        private ParseHandler(final long maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public void handle(final HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "MethodNotAllowed", "Only POST is supported.");
                return;
            }

            final String contentType = headerValue(exchange.getRequestHeaders(), "Content-Type");
            final long startNanos = System.nanoTime();

            try {
                ParseResponse response;
                if (contentType != null && contentType.startsWith("application/zip")) {
                    response = handleZip(exchange, startNanos);
                } else if (contentType != null && contentType.startsWith("application/json")) {
                    response = handleJson(exchange, startNanos);
                } else {
                    sendError(exchange, 415, "UnsupportedMediaType",
                            "Content-Type must be application/zip or application/json.");
                    return;
                }
                sendJson(exchange, 200, response);
            } catch (final IllegalArgumentException e) {
                sendError(exchange, 400, "BadRequest", e.getMessage());
            } catch (final CompileException e) {
                sendError(exchange, 400, "CompileException", e.getMessage());
            } catch (final Exception e) {
                LOGGER.error("Parse request failed.", e);
                sendError(exchange, 500, "ServerError", "Unexpected server error.");
            }
        }

        private ParseResponse handleZip(final HttpExchange exchange, final long startNanos) throws Exception {
            final Map<String, String> queryParams = parseQuery(exchange.getRequestURI().getRawQuery());
            final Lang lang = parseLang(queryParams.get("lang"));
            if (lang == null) {
                throw new IllegalArgumentException("Missing or invalid lang query parameter.");
            }
            final byte[] body = readRequestBytes(exchange.getRequestBody(), maxBytes);
            final ProjectFiles projectFiles = new ProjectFiles(new ByteArrayInputStream(body));
            return compile(lang, projectFiles, startNanos);
        }

        private ParseResponse handleJson(final HttpExchange exchange, final long startNanos) throws Exception {
            final byte[] body = readRequestBytes(exchange.getRequestBody(), maxBytes);
            final ParseRequest request = OBJECT_MAPPER.readValue(body, ParseRequest.class);
            if (request == null) {
                throw new IllegalArgumentException("Request body is missing.");
            }
            final Lang lang = parseLang(request.getLanguage());
            if (lang == null) {
                throw new IllegalArgumentException("Missing or invalid language field.");
            }
            final List<ParseFile> files = request.getFiles();
            if (files == null || files.isEmpty()) {
                throw new IllegalArgumentException("Request contains no files.");
            }
            final ProjectFiles projectFiles = new ProjectFiles();
            for (final ParseFile file : files) {
                if (file == null) {
                    throw new IllegalArgumentException("Request contains a null file entry.");
                }
                final String path = file.getPath();
                if (path == null || path.trim().isEmpty()) {
                    throw new IllegalArgumentException("Each file must include a path.");
                }
                String content = file.getContent();
                if (content == null) {
                    content = "";
                }
                projectFiles.insertFile(new ProjectFile(path, content));
            }
            return compile(lang, projectFiles, startNanos);
        }

        private ParseResponse compile(final Lang lang, final ProjectFiles projectFiles, final long startNanos)
                throws Exception {
            final CompileResult result = new ClarpseProject(projectFiles, lang).result();
            final long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            return new ParseResponse(lang.value(), result.model(), toFailureResponses(result.failures()), durationMs);
        }

        private List<FailureResponse> toFailureResponses(final Iterable<CompileFailure> failures) {
            if (failures == null) {
                return Collections.emptyList();
            }
            final List<FailureResponse> responses = new ArrayList<>();
            for (final CompileFailure failure : failures) {
                if (failure == null || failure.file() == null) {
                    continue;
                }
                responses.add(new FailureResponse(failure.file().path(), failure.message(), failure.errorCode()));
            }
            return responses;
        }
    }

    private static final class AutoCloseableExecutor implements AutoCloseable {
        private final ExecutorService service;

        private AutoCloseableExecutor(final ExecutorService service) {
            this.service = service;
        }

        private ExecutorService service() {
            return service;
        }

        @Override
        public void close() {
            service.shutdown();
        }
    }

    private static Lang parseLang(final String raw) {
        if (raw == null) {
            return null;
        }
        final String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        if ("ts".equals(normalized)) {
            return Lang.TYPESCRIPT;
        }
        return Lang.forValue(normalized);
    }

    private static Map<String, String> parseQuery(final String query) {
        if (query == null || query.isEmpty()) {
            return Collections.emptyMap();
        }
        final Map<String, String> result = new HashMap<>();
        final String[] pairs = query.split("&");
        for (final String pair : pairs) {
            if (pair == null || pair.isEmpty()) {
                continue;
            }
            final int idx = pair.indexOf('=');
            final String key;
            final String value;
            if (idx >= 0) {
                key = decode(pair.substring(0, idx));
                value = decode(pair.substring(idx + 1));
            } else {
                key = decode(pair);
                value = "";
            }
            if (key != null && !key.isEmpty()) {
                result.put(key, value);
            }
        }
        return result;
    }

    private static String decode(final String value) {
        if (value == null) {
            return null;
        }
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static byte[] readRequestBytes(final InputStream inputStream, final long maxBytes) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final byte[] buffer = new byte[READ_BUFFER_SIZE];
        long total = 0;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IllegalArgumentException("Request exceeds max size of " + maxBytes + " bytes.");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String headerValue(final Headers headers, final String key) {
        if (headers == null || key == null) {
            return null;
        }
        final String value = headers.getFirst(key);
        if (value == null) {
            return null;
        }
        return value.trim();
    }

    private static void sendText(final HttpExchange exchange, final int status, final String body)
            throws IOException {
        final byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private static void sendJson(final HttpExchange exchange, final int status, final Object payload)
            throws IOException {
        final byte[] data = OBJECT_MAPPER.writeValueAsBytes(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(data);
        }
    }

    private static void sendError(final HttpExchange exchange, final int status,
                                  final String error, final String message) throws IOException {
        final ErrorResponse response = new ErrorResponse(error, message);
        sendJson(exchange, status, response);
    }
}
