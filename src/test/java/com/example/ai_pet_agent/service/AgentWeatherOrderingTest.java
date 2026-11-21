package com.example.ai_pet_agent.service;

import com.example.ai_pet_agent.device.Esp32Client;
import com.example.ai_pet_agent.llm.LlmService;
import com.example.ai_pet_agent.memory.ConversationMemory;
import com.example.ai_pet_agent.tool.GetCurrentWeatherTool;
import com.example.ai_pet_agent.weather.OpenMeteoWeatherClient;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentWeatherOrderingTest {

    private final JsonMapper mapper =
            JsonMapper.builder().build();

    private Map<String, Object> call(
            String id, String name, String arguments) {

        return Map.of(
                "id", id,
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "arguments", arguments
                )
        );
    }

    private JsonNode decision(
            List<Map<String, Object>> calls) {

        Map<String, Object> message =
                new LinkedHashMap<>();

        message.put("role", "assistant");
        message.put("content", null);
        message.put("tool_calls", calls);

        return mapper.readTree(
                mapper.writeValueAsString(
                        Map.of("choices", List.of(
                                Map.of("message", message)
                        ))
                )
        );
    }

    private JsonNode reply(String text) {

        return mapper.readTree(
                mapper.writeValueAsString(
                        Map.of("choices", List.of(
                                Map.of("message", Map.of(
                                        "role", "assistant",
                                        "content", text
                                ))
                        ))
                )
        );
    }

    private AgentService createAgent(
            LlmService llm,
            Esp32Client esp32) {

        OpenMeteoWeatherClient fakeWeather =
                new OpenMeteoWeatherClient() {
                    @Override
                    public Map<String, Object> getCurrentWeather(
                            String city) {

                        return Map.of(
                                "success", true,
                                "city", "广州",
                                "condition", "overcast",
                                "weather_code", 3,
                                "time", "2026-11-20T17:30"
                        );
                    }
                };

        return new AgentService(
                llm,
                esp32,
                new ConversationMemory(),
                new GetCurrentWeatherTool(fakeWeather)
        );
    }

    private void assertLastToolWasBlocked(
            LlmService llm,
            int requestCount) {

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Map> captor =
                ArgumentCaptor.forClass(Map.class);

        verify(llm, times(requestCount))
                .complete(captor.capture());

        List<?> messages = (List<?>)
                captor.getAllValues()
                        .get(requestCount - 1)
                        .get("messages");

        Map<?, ?> last = (Map<?, ?>)
                messages.get(messages.size() - 1);

        assertEquals("tool", last.get("role"));

        JsonNode result = mapper.readTree(
                (String) last.get("content")
        );

        assertFalse(
                result.path("success").asBoolean()
        );

        assertTrue(
                result.path("error").asText()
                        .contains("天气相关设备控制")
        );
    }

    @Test
    void shouldBlockExpressionBeforeWeatherQuery()
            throws Exception {

        LlmService llm = mock(LlmService.class);
        Esp32Client esp32 = mock(Esp32Client.class);

        when(llm.complete(anyMap())).thenReturn(
                decision(List.of(
                        call(
                                "expression_1",
                                "showExpression",
                                "{\"action\":\"sad\"}"
                        )
                )),
                reply("没有查询天气，因此未控制设备。")
        );

        AgentService agent = createAgent(llm, esp32);

        String result = agent.chat(
                "根据广州天气调整旺仔表情"
        );

        assertEquals(
                "没有查询天气，因此未控制设备。",
                result
        );

        verifyNoInteractions(esp32);
        assertLastToolWasBlocked(llm, 2);
    }

    @Test
    void shouldBlockExpressionInSameToolBatch()
            throws Exception {

        LlmService llm = mock(LlmService.class);
        Esp32Client esp32 = mock(Esp32Client.class);

        List<Map<String, Object>> calls =
                new ArrayList<>();

        calls.add(call(
                "weather_1",
                "getCurrentWeather",
                "{\"city\":\"广州\"}"
        ));

        calls.add(call(
                "expression_2",
                "showExpression",
                "{\"action\":\"sad\"}"
        ));

        when(llm.complete(anyMap())).thenReturn(
                decision(calls),
                reply("天气已查询，但提前控制已被阻止。")
        );

        AgentService agent = createAgent(llm, esp32);

        String result = agent.chat(
                "查询广州天气，根据天气切换表情"
        );

        assertEquals(
                "天气已查询，但提前控制已被阻止。",
                result
        );

        verifyNoInteractions(esp32);
        assertLastToolWasBlocked(llm, 2);
    }
}
