package com.example.ai_pet_agent.service;

import com.example.ai_pet_agent.device.DeviceResult;
import com.example.ai_pet_agent.device.Esp32Client;
import com.example.ai_pet_agent.llm.LlmService;
import com.example.ai_pet_agent.memory.ConversationMemory;
import com.example.ai_pet_agent.tool.GetCurrentWeatherTool;
import com.example.ai_pet_agent.weather.OpenMeteoWeatherClient;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentWeatherSafetyTest {

    private final JsonMapper mapper =
            JsonMapper.builder().build();

    private JsonNode reply(String text) {
        return mapper.readTree(mapper.writeValueAsString(
                Map.of("choices", List.of(
                        Map.of("message", Map.of(
                                "role", "assistant",
                                "content", text
                        ))
                ))
        ));
    }

    private JsonNode toolCall(
            String id, String name, String arguments) {

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", null);
        message.put("tool_calls", List.of(
                Map.of(
                        "id", id,
                        "type", "function",
                        "function", Map.of(
                                "name", name,
                                "arguments", arguments
                        )
                )
        ));

        return mapper.readTree(mapper.writeValueAsString(
                Map.of("choices", List.of(
                        Map.of("message", message)
                ))
        ));
    }

    private AgentService createAgent(
            LlmService llm,
            Esp32Client esp32,
            Map<String, Object> weatherResult) {

        OpenMeteoWeatherClient fakeWeather =
                new OpenMeteoWeatherClient() {
                    @Override
                    public Map<String, Object> getCurrentWeather(
                            String city) {
                        return weatherResult;
                    }
                };

        return new AgentService(
                llm,
                esp32,
                new ConversationMemory(),
                new GetCurrentWeatherTool(fakeWeather)
        );
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void weatherFailureMustBlockExpression() {

        LlmService llm = mock(LlmService.class);
        Esp32Client esp32 = mock(Esp32Client.class);

        when(llm.complete(anyMap())).thenReturn(
                toolCall(
                        "weather_1",
                        "getCurrentWeather",
                        "{\"city\":\"广州\"}"
                ),
                toolCall(
                        "expression_1",
                        "showExpression",
                        "{\"action\":\"sad\"}"
                ),
                reply("天气查询失败，设备表情保持不变。")
        );

        AgentService agent = createAgent(
                llm,
                esp32,
                Map.of(
                        "success", false,
                        "error_code", "WEATHER_API_UNAVAILABLE",
                        "message", "模拟天气服务不可用"
                )
        );

        String result = agent.chat("根据广州天气调整表情");

        assertEquals(
                "天气查询失败，设备表情保持不变。",
                result
        );

        // 关键断言：不能调用真实设备客户端
        verifyNoInteractions(esp32);

        ArgumentCaptor<Map> captor =
                ArgumentCaptor.forClass(Map.class);

        verify(llm, times(3)).complete(captor.capture());

        Map<?, ?> thirdRequest =
                captor.getAllValues().get(2);

        List<?> messages =
                (List<?>) thirdRequest.get("messages");

        Map<?, ?> lastMessage =
                (Map<?, ?>) messages.get(messages.size() - 1);

        assertEquals("tool", lastMessage.get("role"));
        assertEquals("expression_1",
                lastMessage.get("tool_call_id"));

        JsonNode blocked = mapper.readTree(
                (String) lastMessage.get("content")
        );

        assertFalse(blocked.path("success").asBoolean());

        assertTrue(
                blocked.path("error").asText()
                        .contains("天气查询失败")
        );
    }

    @Test
    void weatherSuccessStillAllowsExpression() throws Exception {

        LlmService llm = mock(LlmService.class);
        Esp32Client esp32 = mock(Esp32Client.class);

        when(llm.complete(anyMap())).thenReturn(
                toolCall(
                        "weather_2",
                        "getCurrentWeather",
                        "{\"city\":\"广州\"}"
                ),
                toolCall(
                        "expression_2",
                        "showExpression",
                        "{\"action\":\"sad\"}"
                ),
                reply("已根据天气调整表情。")
        );

        when(esp32.showExpression("sad"))
                .thenReturn(new DeviceResult(
                        true,
                        200,
                        "{\"status\":\"success\",\"action\":\"sad\"}"
                ));

        AgentService agent = createAgent(
                llm,
                esp32,
                Map.of(
                        "success", true,
                        "city", "广州",
                        "condition", "overcast",
                        "weather_code", 3,
                        "time", "2026-11-20T17:30"
                )
        );

        String result = agent.chat("根据广州天气调整表情");

        assertEquals("已根据天气调整表情。", result);

        // 天气正常时，原有控制能力不能被破坏
        verify(esp32, times(1)).showExpression("sad");
        verify(llm, times(3)).complete(anyMap());
    }
}
