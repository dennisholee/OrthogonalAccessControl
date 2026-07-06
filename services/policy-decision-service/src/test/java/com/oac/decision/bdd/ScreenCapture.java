package com.oac.decision.bdd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Captures test evidence artifacts for each Cucumber scenario.
 * Writes seed data, HTTP request/response, post-state, and verification logs
 * to {@code target/screen-capture/{feature}/{scenario}/}.
 */
public class ScreenCapture {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path outputDir;
    private final Map<String, Object> seedData = new LinkedHashMap<>();
    private final Map<String, Object> requestCapture = new LinkedHashMap<>();
    private final Map<String, Object> responseCapture = new LinkedHashMap<>();
    private final Map<String, Object> postState = new LinkedHashMap<>();
    private final StringBuilder verificationLog = new StringBuilder();

    public ScreenCapture(String featureName, String scenarioName) {
        String safeFeature = featureName.replaceAll("[^a-zA-Z0-9_-]", "_").replaceAll("_+", "_");
        String safeScenario = scenarioName.replaceAll("[^a-zA-Z0-9_-]", "_").replaceAll("_+", "_");
        this.outputDir = Paths.get("target", "screen-capture", safeFeature, safeScenario);
    }

    public void init() {
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create screen-capture directory: " + outputDir, e);
        }
    }

    public void captureSeedData(String collection, Object document) {
        seedData.put(collection, document);
    }

    public void captureSeedData(Map<String, Object> allSeeds) {
        seedData.putAll(allSeeds);
    }

    public void captureRequest(String method, String url, Map<String, Object> headers, Object body) {
        requestCapture.put("method", method);
        requestCapture.put("url", url);
        requestCapture.put("headers", headers);
        requestCapture.put("body", body);
    }

    public void captureResponse(int status, Object body, Map<String, Object> headers) {
        responseCapture.put("status", status);
        responseCapture.put("body", body);
        responseCapture.put("headers", headers);
    }

    public void capturePostState(String collection, Object state) {
        postState.put(collection, state);
    }

    public void log(String message) {
        verificationLog.append(message).append("\n");
    }

    public void logAssertion(String description, boolean passed, String expected, String actual) {
        verificationLog.append(passed ? "[PASS] " : "[FAIL] ")
                .append(description)
                .append(" — expected: ").append(expected)
                .append(", actual: ").append(actual)
                .append("\n");
    }

    public void write() {
        writeJson("00-seed-data.json", seedData);
        writeJson("01-request.json", requestCapture);
        writeJson("02-response.json", responseCapture);
        writeJson("03-post-state.json", postState);
        writeFile("04-verification-log.txt", verificationLog.toString());
    }

    private void writeJson(String filename, Object data) {
        try {
            if (!data.toString().equals("{}") && !data.toString().equals("[]")) {
                MAPPER.writeValue(outputDir.resolve(filename).toFile(), data);
            }
        } catch (IOException e) {
            System.err.println("[ScreenCapture] Failed to write " + filename + ": " + e.getMessage());
        }
    }

    private void writeFile(String filename, String content) {
        try {
            if (!content.isBlank()) {
                Files.writeString(outputDir.resolve(filename), content);
            }
        } catch (IOException e) {
            System.err.println("[ScreenCapture] Failed to write " + filename + ": " + e.getMessage());
        }
    }
}