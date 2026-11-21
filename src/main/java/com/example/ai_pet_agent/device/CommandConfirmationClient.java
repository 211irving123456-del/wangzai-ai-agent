
package com.example.ai_pet_agent.device;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class CommandConfirmationClient {

    private static final int MAX_ATTEMPTS = 8;
    private static final int INTERVAL_MS = 200;

    private final HttpClient httpClient;
    private final JsonMapper mapper;
    private final URI commandUri;
    private final String deviceToken;

    public CommandConfirmationClient() {

        String deviceUrl =
                System.getenv("ESP32_DEVICE_URL");

        if (deviceUrl == null || deviceUrl.isBlank()) {
            throw new IllegalStateException(
                    "ESP32_DEVICE_URL is missing"
            );
        }

        commandUri = URI.create(deviceUrl)
                .resolve("/api/device/command");

        deviceToken =
                System.getenv("ESP32_DEVICE_TOKEN");

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        mapper = JsonMapper.builder().build();
    }

    public String confirm(
            String expectedAction,
            boolean httpOk,
            int httpStatus,
            String postResponseBody
    ) throws InterruptedException {

        // Step 1: Verify the POST response.
        if (!httpOk || httpStatus != 200) {
            return result(
                    false, "post_failed", 0,
                    expectedAction, 0,
                    "ESP32 rejected the command"
            );
        }

        JsonNode accepted;

        try {
            accepted = mapper.readTree(postResponseBody);
        } catch (Exception e) {
            return result(
                    false, "invalid_response", 0,
                    expectedAction, 0,
                    "Invalid POST response JSON"
            );
        }

        if (!accepted.path("success").asBoolean(false)) {
            return result(
                    false, "post_failed", 0,
                    expectedAction, 0,
                    "Device did not accept the command"
            );
        }

        JsonNode idNode = accepted.path("command_id");

        if (!idNode.isIntegralNumber()
                || idNode.asLong() <= 0) {
            return result(
                    false, "unconfirmed", 0,
                    expectedAction, 0,
                    "Missing command_id"
            );
        }

        long commandId = idNode.asLong();


// Identify the ESP32 boot instance that
// accepted this particular command.
String expectedBootId =
        accepted.path("boot_id").asText();

if (!expectedBootId.matches("[0-9a-f]{16}")) {

    return result(
        false,
        "invalid_response",
        commandId,
        expectedAction,
        0,
        "Missing or invalid boot_id in POST response"
    );
}

        if (deviceToken == null || deviceToken.isBlank()) {
            return result(
                    false, "auth_missing", commandId,
                    expectedAction, 0,
                    "ESP32_DEVICE_TOKEN is missing"
            );
        }

        // Step 2: Poll the specific command.
        for (int attempt = 1;
             attempt <= MAX_ATTEMPTS;
             attempt++) {

            URI queryUri = URI.create(
                    commandUri.toString()
                            + "?id=" + commandId
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(queryUri)
                    .version(HttpClient.Version.HTTP_1_1)
                    .header(
                            "Authorization",
                            "Bearer " + deviceToken
                    )
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<String> response;

            try {
                response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );
            } catch (IOException e) {
                return result(
                        false, "query_error", commandId,
                        expectedAction, attempt,
                        "Cannot query ESP32"
                );
            }

if (response.statusCode() == 404) {

    return result(
        false,
        "command_not_found",
        commandId,
        expectedAction,
        attempt,
        "Command record not found; it may have "
            + "expired, been replaced, or been "
            + "lost after a device restart"
    );
}

            if (response.statusCode() != 200) {
                return result(
                        false, "query_error", commandId,
                        expectedAction, attempt,
                        "HTTP " + response.statusCode()
                );
            }

            JsonNode state;

            try {
                state = mapper.readTree(response.body());
            } catch (Exception e) {
                return result(
                        false, "invalid_response", commandId,
                        expectedAction, attempt,
                        "Invalid command status JSON"
                );
            }

// Read the boot identity reported by GET.
String actualBootId =
        state.path("boot_id").asText();

if (!actualBootId.matches("[0-9a-f]{16}")) {

    return result(
        false,
        "invalid_response",
        commandId,
        expectedAction,
        attempt,
        "Missing or invalid boot_id in GET response"
    );
}

// A different boot ID means this is no longer
// the same running ESP32 instance.
if (!expectedBootId.equals(actualBootId)) {

    return result(
        false,
        "device_restarted",
        commandId,
        expectedAction,
        attempt,
        "ESP32 restarted after accepting the command"
    );
}

            // Verify that this is the same command.
            if (state.path("command_id").asLong() != commandId
                    || !expectedAction.equals(
                            state.path("action").asText()
                    )) {

                return result(
                        false, "mismatch", commandId,
                        expectedAction, attempt,
                        "Command ID or action mismatch"
                );
            }

            String status =
                    state.path("status").asText();

            if ("completed".equals(status)) {
                return result(
                        true, "completed", commandId,
                        expectedAction, attempt,
                        "LVGL expression update completed"
                );
            }

            if ("failed".equals(status)) {
                return result(
                        false, "failed", commandId,
                        expectedAction, attempt,
                        "Device reported command failure"
                );
            }

            if (!"accepted".equals(status)
                    && !"processing".equals(status)) {

                return result(
                        false, "unknown_status", commandId,
                        expectedAction, attempt,
                        "Unexpected device command status"
                );
            }

            if (attempt < MAX_ATTEMPTS) {
                Thread.sleep(INTERVAL_MS);
            }
        }

        return result(
                false, "unconfirmed", commandId,
                expectedAction, MAX_ATTEMPTS,
                "Command did not complete within polling limit"
        );
    }

    private String result(
            boolean success,
            String status,
            long commandId,
            String action,
            int attempts,
            String message
    ) {

        return mapper.writeValueAsString(Map.of(
                "success", success,
                "status", status,
                "command_id", commandId,
                "action", action,
                "attempts", attempts,
                "message", message
        ));
    }
}
