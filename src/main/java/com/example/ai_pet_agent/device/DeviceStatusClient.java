package com.example.ai_pet_agent.device;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class DeviceStatusClient {

    private final HttpClient client;

    private final URI statusUri;

    private final String deviceToken;

    public DeviceStatusClient() {

        // Read the existing ESP32 configuration
        String deviceUrl =
                System.getenv("ESP32_DEVICE_URL");

        deviceToken =
                System.getenv("ESP32_DEVICE_TOKEN");

        if (deviceUrl == null || deviceUrl.isBlank()) {
            throw new IllegalStateException(
                    "ESP32_DEVICE_URL is not configured"
            );
        }

        // Convert expression endpoint to status endpoint
        statusUri = URI.create(deviceUrl)
                .resolve("/api/device/status");

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public String getDeviceStatus() {

        if (deviceToken == null || deviceToken.isBlank()) {
            return "{\"success\":false,"
                    + "\"error\":\"device token missing\"}";
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(statusUri)
                .version(HttpClient.Version.HTTP_1_1)
                .header(
                        "Authorization",
                        "Bearer " + deviceToken
                )
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        try {

            HttpResponse<String> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            if (response.statusCode() == 200) {
                return response.body();
            }

            return "{\"success\":false,"
                    + "\"httpStatus\":"
                    + response.statusCode()
                    + ",\"error\":\"status request failed\"}";

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            return "{\"success\":false,"
                    + "\"error\":\"request interrupted\"}";

        } catch (IOException e) {

            return "{\"success\":false,"
                    + "\"error\":\"device unreachable\"}";
        }
    }

    // Temporary entry point for Lesson 28 testing
    public static void main(String[] args) {

        DeviceStatusClient client =
                new DeviceStatusClient();

        System.out.println(client.getDeviceStatus());
    }
}
