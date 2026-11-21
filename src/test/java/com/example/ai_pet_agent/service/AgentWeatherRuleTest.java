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

class AgentWeatherRuleTest {

    private final JsonMapper mapper =
            JsonMapper.builder().build();

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

        return mapper.readTree(
                mapper.writeValueAsString(
                        Map.of("choices", List.of(
                                Map.of("message", message)
                        ))
                )
        );
    }

    private JsonNode reply() {
        return mapper.readTree(
                mapper.writeValueAsString(
                        Map.of("choices", List.of(
                                Map.of("message", Map.of(
                                        "role", "assistant",
                                        "content", "测试完成"
                                ))
                        ))
                )
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void checkScenario(
            String condition,
            int weatherCode,
            String action,
            boolean shouldControl) throws Exception {

        LlmService llm = mock(LlmService.class);
        Esp32Client esp32 = mock(Esp32Client.class);

        OpenMeteoWeatherClient fakeWeather =
                new OpenMeteoWeatherClient() {

            @Override
            public Map<String, Object> getCurrentWeather(
                    String city) {

                return Map.of(
                        "success", true,
                        "city", city,
                        "condition", condition,
                        "weather_code", weatherCode,
                        "time", "2026-11-21T00:30"
                );
            }
        };

        when(llm.complete(anyMap())).thenReturn(
                toolCall(
                        "weather_1",
                        "getCurrentWeather",
                        "{\"city\":\"广州\"}"
                ),
                toolCall(
                        "expression_1",
                        "showExpression",
                        "{\"action\":\"" + action + "\"}"
                ),
                reply()
        );

        if (shouldControl) {
            when(esp32.showExpression(action))
                    .thenReturn(new DeviceResult(
                            true,
                            200,
                            "{\"status\":\"success\"}"
                    ));
        }

        AgentService agent = new AgentService(
                llm,
                esp32,
                new ConversationMemory(),
                new GetCurrentWeatherTool(fakeWeather)
        );

        assertEquals(
                "测试完成",
                agent.chat("请根据广州天气调整旺仔表情")
        );

        verify(llm, times(3)).complete(anyMap());

        if (shouldControl) {
            verify(esp32, times(1))
                    .showExpression(action);
        } else {
            verifyNoInteractions(esp32);

            ArgumentCaptor<Map> captor =
                    ArgumentCaptor.forClass(Map.class);

            verify(llm, times(3))
                    .complete(captor.capture());

            List<?> messages = (List<?>)
                    captor.getAllValues().get(2).get("messages");

            Map<?, ?> last = (Map<?, ?>)
                    messages.get(messages.size() - 1);

            JsonNode result = mapper.readTree(
                    (String) last.get("content")
            );

            assertFalse(result.path("success").asBoolean());

            assertTrue(
                    result.path("error").asText()
                            .contains("天气表情规则校验失败")
            );
        }
    }

    @Test
    void wrongExpressionMustBeBlocked() throws Exception {
        checkScenario("overcast", 3, "happy", false);
    }

    @Test
    void correctExpressionMustBeAllowed() throws Exception {
        checkScenario("overcast", 3, "sad", true);
    }

    @Test
    void unsupportedWeatherMustNotControlDevice()
            throws Exception {
        checkScenario("other", 999, "sad", false);
    }
}
