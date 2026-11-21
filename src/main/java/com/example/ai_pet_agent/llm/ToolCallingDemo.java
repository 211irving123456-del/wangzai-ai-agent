package com.example.ai_pet_agent.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.example.ai_pet_agent.device.Esp32Client;
import com.example.ai_pet_agent.device.DeviceResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public class ToolCallingDemo{
    public static void main(String[] args) throws Exception{
        // 1. 读取 DeepSeek 配置
        String apiKey = System.getenv("LLM_API_KEY");
        String apiUrl = System.getenv("LLM_API_URL");
        String model = System.getenv("LLM_MODEL");

        if (apiKey == null || apiKey.isBlank()
                || apiUrl == null || apiUrl.isBlank()
                || model == null || model.isBlank()) {

            System.out.println("请先配置 DeepSeek 环境变量");
            return;
        }

        //创建json解析器
        JsonMapper mapper = JsonMapper.builder().build();

        //定义showexpression工具
        Map<String,Object> tool = Map.of(
            "type","function",
            "function",Map.of(
                "name","showExpression",
                "description","控制ai桌宠显示开心，难过或睡觉表情",
                "parameters",Map.of(
                    "type","object",
                    "properties",Map.of(
                            "action",Map.of(
                                "type","string",
                                "enum",List.of(
                                        "happy","sad","sleep"
                            )
                        )
                    ),
                    "required",List.of("action"),
                    "additionalProperties",false
                )
            )
        );

        List<Map<String,Object>> messages = new ArrayList<>();

        messages.add(Map.of(
            "role","user",
            "content","旺仔，给我做一个开心的表情！"
        ));

        Map<String,Object> payload = Map.of(
                "model",model,
                "messages",messages,
                "tools",List.of(tool),
                "tool_choice","required",
                "thinking",Map.of("type","disabled"),
                "stream",false
        );
        /*Map<String,Object> payload = Map.of(
            "model",model,
            "messages",List.of(
                Map.of(
                    "role","user",
                    "content","旺仔，我今天有点难过，请显示难过的表情！"
                )
            ),
            "tools",List.of(tool),
            "tool_choice","required",
            "thinking",Map.of("type","disabled"),
            "stream",false
        );*/
        String json = mapper.writeValueAsString(payload);

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization","Bearer " + apiKey)
                .header("Content-Type","application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(
                      json,StandardCharsets.UTF_8))
                .build();
        //调用DeepSeek
        HttpResponse<String> response =client.send(
            request,
            HttpResponse.BodyHandlers.ofString(
                StandardCharsets.UTF_8)
            );
        System.out.println("HTTP状态码：" + response.statusCode());

        if(response.statusCode()<200
            || response.statusCode()>= 300){
                System.out.println(response.body());
                return;
            }
        //解析工具调用结果
        JsonNode root =mapper.readTree(response.body());

        JsonNode assistantNode = root
                .path("choices")
                .path(0)
                .path("message");

        JsonNode toolCalls = assistantNode.path("tool_calls");

        if (!toolCalls.isArray() || toolCalls.size()  == 0){
            System.out.println("模型没有返回工具调用");
            return;
        }
        if(toolCalls.size()!=1){
            System.out.println("当前实验只支持一次工具调用，实际数量："+ toolCalls.size());
            return;
        }

        JsonNode function = toolCalls
            .path(0)
            .path("function");

        String toolName = function.path("name").asText();
        String arguments = function.path("arguments").asText();

        JsonNode argsJson = mapper.readTree(arguments);
        String action = argsJson.path("action").asText();

        Set<String> allowedActions =
                Set.of("happy","sad","sleep");

        if(!"showExpression".equals(toolName)
            || !allowedActions.contains(action)){
                System.out.println("拒绝未知工具或非法参数");
                return;
            }

        System.out.println("模型选择的工具:" + toolName);
        System.out.println("模型选择的动作:" + action);

        Esp32Client esp32 = new Esp32Client(
            "http://localhost:8080/mock-esp32/expression"
        );

        DeviceResult result = esp32.showExpression(action);

        System.out.println("设备HTTP状态码：" + result.httpStatus());
        System.out.println("设备返回内容：" + result.responseBody());

        boolean success = false;

        if(result.httpOk()){
            JsonNode deviceJson = mapper.readTree(result.responseBody());

            success = "success".equals(
                deviceJson.path("status").asText()
            );
        }

        if(success){
            System.out.println("模拟ESP32表情控制成功！");
        }else{
            System.out.println("模拟ESP32表情控制失败！");
        }

        String toolCallId = toolCalls.path(0).path("id").asText();

        if(toolCallId.isBlank()){
            throw new IllegalStateException("模型没有返回有效的tool_call_id");
        }
        Map<String,Object> assistantMessage = new LinkedHashMap<>();
        assistantMessage.put("role","assistant");
        JsonNode contentNode = assistantNode.path("content");
        assistantMessage.put(
            "content",
            contentNode.isNull() || contentNode.isMissingNode()
                    ? null
                    : contentNode.asText()
        );
        assistantMessage.put("tool_calls",toolCalls);
        messages.add(assistantMessage);

        String toolResultJson = mapper.writeValueAsString(
            Map.of(
                "success",success,
                "httpStatus",result.httpStatus(),
                "responseBody",result.responseBody()
            )
        );
        messages.add(Map.of(
            "role","tool",
            "tool_call_id",toolCallId,
            "content",toolResultJson
        ));
        Map<String,Object>secondPayload = Map.of(
            "model",model,
            "messages",messages,
            "thinking",Map.of("type","disabled"),
            "stream",false
        );

        String secondJson = mapper.writeValueAsString(secondPayload);

        HttpRequest secondRequest = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .header("Authorization","Bearer " + apiKey)
            .header("Content-Type","application/json")
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(
                    secondJson,StandardCharsets.UTF_8
            ))
            .build();
        // 检查两次请求是否访问同一个 API 地址
        System.out.println("两次请求URL是否一致："
        + request.uri().equals(secondRequest.uri()));

        // 检查两次请求的 Authorization 是否一致
        System.out.println("两次请求认证信息是否一致："
             + request.headers().firstValue("Authorization")
                  .equals(secondRequest.headers().firstValue("Authorization")));

        // 检查第二次请求的模型
        System.out.println("当前模型：" + model);
        HttpResponse<String> secondResponse = client.send(
            secondRequest,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        System.out.println("第二次请求HTTP状态码：  "
            + secondResponse.statusCode());
        if(secondResponse.statusCode()<200
        ||secondResponse.statusCode()>=300){
            System.out.println("第二次请求失败： "
            + secondResponse.body());
            return;
        }
        JsonNode secondRoot =mapper.readTree(secondResponse.body());

        String finalReply = secondRoot
            .path("choices")
            .path(0)
            .path("message")
            .path("content")
            .asText();

        if(finalReply.isBlank()){
            System.out.println("模型没有返回有效的最终回复");
            return;
        }
        System.out.println("--------------------------");
        System.out.println("旺仔最终回复：" + finalReply);
   }
}