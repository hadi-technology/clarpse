package com.hadi.test.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hadi.clarpse.server.ClarpseServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClarpseServerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static ClarpseServer.ServerHandle serverHandle;
    private static String baseUrl;

    @BeforeClass
    public static void startServer() throws Exception {
        serverHandle = ClarpseServer.startServer(0, 20L * 1024L * 1024L);
        baseUrl = "http://127.0.0.1:" + serverHandle.port();
    }

    @AfterClass
    public static void stopServer() {
        if (serverHandle != null) {
            serverHandle.close();
        }
    }

    @Test
    public void parseJsonRequestReturnsModel() throws Exception {
        final String requestJson = "{"
                + "\"language\":\"java\","
                + "\"files\":[{\"path\":\"src/Foo.java\",\"content\":\"package test; class Foo { void m() {} }\"}]"
                + "}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/parse"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        JsonNode json = OBJECT_MAPPER.readTree(response.body());
        assertEquals("java", json.path("language").asText());
        assertTrue(json.path("model").path("components").has("test.Foo"));
        assertTrue(json.path("model").path("components").has("test.Foo.m()"));
        assertTrue(json.path("failures").isArray());
        assertTrue(json.path("durationMs").asLong() >= 0);
    }

    @Test
    public void parseZipRequestReturnsModel() throws Exception {
        byte[] zipPayload = createJavaZip("src/Bar.java", "package test; class Bar { int x; }");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/parse?lang=java"))
                .header("Content-Type", "application/zip")
                .POST(HttpRequest.BodyPublishers.ofByteArray(zipPayload))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        JsonNode json = OBJECT_MAPPER.readTree(response.body());
        assertEquals("java", json.path("language").asText());
        assertTrue(json.path("model").path("components").has("test.Bar"));
        assertFalse(json.path("model").path("components").path("test.Bar").isMissingNode());
    }

    private static byte[] createJavaZip(final String path, final String content) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            ZipEntry entry = new ZipEntry(path);
            zos.putNextEntry(entry);
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return out.toByteArray();
    }
}
