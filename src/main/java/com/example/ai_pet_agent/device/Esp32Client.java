package com.example.ai_pet_agent.device;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

public class Esp32Client{
    private static final Set<String> ALLOWED_ACTIONS =
            Set.of("happy","sad","sleep");

    private final HttpClient client;
    private final URI endpoint;

    public Esp32Client(String deviceUrl){
        this(
            deviceUrl,
            HttpClient.newBuilder()
                      .connectTimeout(Duration.ofSeconds(5))
                      .build()
        );
    }

    Esp32Client(String deviceUrl,HttpClient client){
        this.endpoint = URI.create(deviceUrl);
        this.client = client;
    }

    public DeviceResult showExpression(String action)
            throws IOException,InterruptedException{

                if (action == null || !ALLOWED_ACTIONS.contains(action)){
                    throw new IllegalArgumentException("不支持的动作："+ action);
                }

String json = "{\"action\":\"" + action + "\"}";

HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(endpoint)
        .version(HttpClient.Version.HTTP_1_1)
        .header("Content-Type", "application/json")
        .timeout(Duration.ofSeconds(10));

// Read the device token from environment
String deviceToken =
        System.getenv("ESP32_DEVICE_TOKEN");

if (deviceToken != null && !deviceToken.isBlank()) {

    builder.header(
            "Authorization",
            "Bearer " + deviceToken
    );
}

HttpRequest request = builder
        .POST(HttpRequest.BodyPublishers.ofString(
                json,
                StandardCharsets.UTF_8
        ))
        .build();

                HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                );

                int statusCode = response.statusCode();

                boolean httpOk =
                        statusCode >=200 && statusCode<300;

                return new DeviceResult(
                    httpOk,
                    statusCode,
                    response.body()
                );
            }
}