
package com.example.ai_pet_agent.service;

import com.example.ai_pet_agent.device.DeviceResult;
import com.example.ai_pet_agent.device.Esp32Client;
import com.example.ai_pet_agent.llm.LlmService;
import java.net.http.HttpTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import org.mockito.ArgumentCaptor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentServiceTest {

    private LlmService llmService;
    private Esp32Client esp32Client;
    private AgentService agentService;

    private final JsonMapper mapper =
            JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        llmService = mock(LlmService.class);
        esp32Client = mock(Esp32Client.class);

        agentService = new AgentService(
                llmService, esp32Client
        );
    }

    // 测试一：普通聊天不应该控制设备
    @Test
    void testNormalChat() throws Exception {

        when(llmService.complete(anyMap()))
                .thenReturn(normalReply("你好，我是旺仔！"));

        String reply = agentService.chat("你好");

        assertEquals("你好，我是旺仔！", reply);

        verifyNoInteractions(esp32Client);
        verify(llmService, times(1))
                .complete(anyMap());
    }

    // 测试二：模型选择工具，设备执行成功
    @Test
    void testToolSuccess() throws Exception {

        when(llmService.complete(anyMap()))
                .thenReturn(
                        toolDecision(),
                        normalReply("表情切换成功！")
                );

        when(esp32Client.showExpression("happy"))
                .thenReturn(new DeviceResult(
                        true,
                        200,
                        "{\"status\":\"success\",\"action\":\"happy\"}"
                ));

        String reply = agentService.chat("开心一点");

        assertEquals("表情切换成功！", reply);

        verify(esp32Client, times(1))
                .showExpression("happy");

        verify(llmService, times(2))
                .complete(anyMap());
    }

    // 测试三：设备失败，Agent 应回传失败结果
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void testToolFailure() throws Exception {

        when(llmService.complete(anyMap()))
                .thenReturn(
                        toolDecision(),
                        normalReply("抱歉，设备暂时无法切换表情。")
                );

        when(esp32Client.showExpression("happy"))
                .thenReturn(new DeviceResult(
                        false,
                        503,
                        "{\"status\":\"error\"}"
                ));

        String reply = agentService.chat("开心一点");

        assertEquals(
                "抱歉，设备暂时无法切换表情。",
                reply
        );

        verify(esp32Client).showExpression("happy");

        // 检查第二次请求是否带上真实的失败结果
        var captor = org.mockito.ArgumentCaptor
                .forClass(Map.class);

        verify(llmService, times(2))
                .complete(captor.capture());

        Map secondRequest = captor.getAllValues().get(1);

        List<?> messages =
                (List<?>) secondRequest.get("messages");

        Map<?, ?> toolMessage =
                (Map<?, ?>) messages.get(messages.size() - 1);

        assertEquals("tool", toolMessage.get("role"));
        assertEquals("call_test_1",
                toolMessage.get("tool_call_id"));

        JsonNode toolResult = mapper.readTree(
                (String) toolMessage.get("content")
        );

        assertFalse(
                toolResult.path("success").asBoolean()
        );

        assertEquals(
                503,
                toolResult.path("httpStatus").asInt()
        );
    }

    // 构造普通聊天的模拟响应
    private JsonNode normalReply(String content)
            throws Exception {

        Map<String, Object> message = Map.of(
                "role", "assistant",
                "content", content
        );

        Map<String, Object> response = Map.of(
                "choices",
                List.of(Map.of("message", message))
        );

        return mapper.readTree(
                mapper.writeValueAsString(response)
        );
    }
private JsonNode toolDecision() throws Exception {
    return toolDecision("happy");
}
    // 构造模型选择 showExpression 的模拟响应
    private JsonNode toolDecision(String action) throws Exception {

        String arguments = mapper.writeValueAsString(
                Map.of("action", action)
        );

        Map<String, Object> function = Map.of(
                "name", "showExpression",
                "arguments", arguments
        );

        Map<String, Object> call = Map.of(
                "id", "call_test_1",
                "type", "function",
                "function", function
        );

        Map<String, Object> message =
                new LinkedHashMap<>();

        message.put("role", "assistant");
        message.put("content", null);
        message.put("tool_calls", List.of(call));

        return mapper.readTree(
                mapper.writeValueAsString(
                        Map.of(
                                "choices",
                                List.of(Map.of(
                                        "message", message
                                ))
                        )
                )
        );
    }



@Test
@SuppressWarnings({"rawtypes", "unchecked"})
void testDeviceNetworkFailure() throws Exception {

    // 1. 模拟第一次 DeepSeek 请求：选择 happy 工具
    // 2. 模拟第二次 DeepSeek 请求：生成失败提示
    when(llmService.complete(anyMap()))
            .thenReturn(
                    toolDecision(),
                    normalReply("设备连接失败，暂时无法切换表情。")
            );

    // 3. 模拟 ESP32 网络中断
    when(esp32Client.showExpression("happy"))
            .thenThrow(
                    new IOException("模拟ESP32网络断开")
            );

    // 4. 执行 AgentService
    String reply = agentService.chat("旺仔，开心一点！");

    // 5. 验证 Agent 最终返回了模型回复
    assertEquals(
            "设备连接失败，暂时无法切换表情。",
            reply
    );

    // 6. 验证 Java 确实调用过设备客户端
    verify(esp32Client, times(1))
            .showExpression("happy");

    // 7. 捕获发给 DeepSeek 的第二次请求
    ArgumentCaptor<Map> captor =
            ArgumentCaptor.forClass(Map.class);

    verify(llmService, times(2))
            .complete(captor.capture());

    Map secondRequest = captor.getAllValues().get(1);

    List<?> messages =
            (List<?>) secondRequest.get("messages");

    Map<?, ?> toolMessage =
            (Map<?, ?>) messages.get(messages.size() - 1);

    // 8. 验证最后一条消息是工具结果
    assertEquals("tool", toolMessage.get("role"));

    assertEquals(
            "call_test_1",
            toolMessage.get("tool_call_id")
    );

    // 9. 验证传给模型的是失败，而不是成功
    JsonNode toolResult = mapper.readTree(
            (String) toolMessage.get("content")
    );

    assertFalse(
            toolResult.path("success").asBoolean()
    );

    assertTrue(
            toolResult.path("error")
                    .asText()
                    .contains("IOException")
    );
}


@Test
@SuppressWarnings({"rawtypes", "unchecked"})
void testRejectInvalidAction() throws Exception {

    // 1. 模拟 DeepSeek 返回非法动作 dance
    when(llmService.complete(anyMap()))
            .thenReturn(
                    toolDecision("dance"),
                    normalReply("抱歉，我不支持这个动作。")
            );

    // 2. 执行 Agent
    String reply = agentService.chat("旺仔，跳舞！");

    assertEquals(
            "抱歉，我不支持这个动作。",
            reply
    );

    // 3. 关键验证：不允许调用设备
    verifyNoInteractions(esp32Client);

    // 4. 获取发送给模型的第二次请求
    var captor = org.mockito.ArgumentCaptor
            .forClass(Map.class);

    verify(llmService, times(2))
            .complete(captor.capture());

    Map secondRequest = captor.getAllValues().get(1);

    List<?> messages =
            (List<?>) secondRequest.get("messages");

    Map<?, ?> toolMessage =
            (Map<?, ?>) messages.get(messages.size() - 1);

    // 5. 检查工具失败消息
    assertEquals("tool", toolMessage.get("role"));
    assertEquals(
            "call_test_1",
            toolMessage.get("tool_call_id")
    );

    JsonNode toolResult = mapper.readTree(
            (String) toolMessage.get("content")
    );

    assertFalse(toolResult.path("success").asBoolean());

    assertTrue(
            toolResult.path("error")
                    .asText()
                    .contains("不支持的表情")
    );
}

@Test
@SuppressWarnings({"rawtypes", "unchecked"})
void testDeviceTimeout() throws Exception {

    // 模拟第一次请求选择工具，第二次请求生成回复
    when(llmService.complete(anyMap()))
            .thenReturn(
                    toolDecision(),
                    normalReply("设备响应超时，请稍后再试。")
            );

    // 模拟设备请求超时
    when(esp32Client.showExpression("happy"))
            .thenThrow(
                    new HttpTimeoutException("模拟设备响应超时")
            );

    String reply = agentService.chat("旺仔，开心一点！");

    assertEquals(
            "设备响应超时，请稍后再试。",
            reply
    );

    verify(esp32Client, times(1))
            .showExpression("happy");

    // 检查 Agent 反馈给模型的工具执行结果
    var captor = org.mockito.ArgumentCaptor
            .forClass(Map.class);

    verify(llmService, times(2))
            .complete(captor.capture());

    Map secondRequest = captor.getAllValues().get(1);

    List<?> messages =
            (List<?>) secondRequest.get("messages");

    Map<?, ?> toolMessage =
            (Map<?, ?>) messages.get(messages.size() - 1);

    JsonNode toolResult = mapper.readTree(
            (String) toolMessage.get("content")
    );

    assertEquals("tool", toolMessage.get("role"));
    assertFalse(toolResult.path("success").asBoolean());

    assertTrue(
            toolResult.path("error")
                    .asText()
                    .contains("HttpTimeoutException")
    );
}

@Test
void testTooManyToolCalls() throws Exception {

    // 1. 模拟模型一次请求调用 4 个工具
    List<Map<String, Object>> calls = new ArrayList<>();

    for (int i = 0; i < 4; i++) {

        calls.add(Map.of(
                "id", "call_test_" + i,
                "type", "function",
                "function", Map.of(
                        "name", "showExpression",
                        "arguments", "{\"action\":\"happy\"}"
                )
        ));
    }

    // 2. 构造模型返回的 assistant 消息
    Map<String, Object> assistantMessage =
            new LinkedHashMap<>();

    assistantMessage.put("role", "assistant");
    assistantMessage.put("content", null);
    assistantMessage.put("tool_calls", calls);

    JsonNode firstResponse = mapper.readTree(
            mapper.writeValueAsString(
                    Map.of(
                            "choices",
                            List.of(Map.of(
                                    "message", assistantMessage
                            ))
                    )
            )
    );

    when(llmService.complete(anyMap()))
            .thenReturn(firstResponse);

    // 3. 执行 Agent
    String reply = agentService.chat("连续切换四次表情");

    // 4. 验证 Agent 拒绝超量操作
    assertTrue(reply.contains("一次最多允许 3 个操作"));

    // 5. 关键：设备一次都不能被调用
    verifyNoInteractions(esp32Client);

    // 6. 只应有第一次模型请求
    verify(llmService, times(1))
            .complete(anyMap());
}

}
