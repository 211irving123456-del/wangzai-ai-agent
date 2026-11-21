package com.example.ai_pet_agent.device;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class HttpClientDemo{
    public static void main(String[] args)
            throws Exception {
                HttpClient client = HttpClient.newHttpClient();

                HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080/api/health"))
                            .GET()
                            .build();

                HttpResponse<String> response = client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                );
                System.out.println("HTTP状态码：" + response.statusCode());

                System.out.println("响应内容：" + response.body());
            }
}