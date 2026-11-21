package com.example.ai_pet_agent.device;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class PostHttpClientDemo{
    public static void main (String[] args)
            throws Exception{
                HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

            String json = "{\"message\":\"您好，旺仔！\"}";

            HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8080/api/chat"))
                        .header("Content-Type","application/json")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(
                            json,StandardCharsets.UTF_8))
                        .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            System.out.println("Http状态码：" + response.statusCode());
            System.out.println("服务器恢复：" + response.body());
            }
}