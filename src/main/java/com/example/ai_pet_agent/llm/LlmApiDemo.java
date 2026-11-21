package com.example.ai_pet_agent.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class LlmApiDemo {

    public static void main(String[] args) throws Exception{
        String apiKey = System.getenv("LLM_API_KEY");

        String apiUrl = System.getenv("LLM_API_URL");

        String model = System.getenv("LLM_MODEL");

        if(apiKey == null || apiKey.isBlank()
            || apiUrl == null || apiUrl.isBlank()
            || model == null || model.isBlank()){
                System.out.println("请先配置LLM_API_KEY,LLM_API_URL,LLM_MODEL");
                return;
            }

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        String json = """
            {
            "model":"%s",
            "messages":[
                {
                "role":"user",
                "content":"你好旺仔,请用一句话介绍自己。"
                }
              ]
            }
            """.formatted(model);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization","Bearer " + apiKey)
                .header("Content-Type","application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(
                    json,StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(
            request,
            HttpResponse.BodyHandlers.ofString(
                StandardCharsets.UTF_8)
        );

        System.out.println("HTTP状态码:" + response.statusCode());

    if(response.statusCode() >=200 && response.statusCode() <300){
        JsonMapper mapper = new JsonMapper();
        JsonNode root = mapper.readTree(response.body());
        String reply = root
                    .path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText();

        System.out.println("旺仔：" + reply);
        }else{
             System.out.println("API调用失败！");
             System.out.println(response.body());
        }

    }

}