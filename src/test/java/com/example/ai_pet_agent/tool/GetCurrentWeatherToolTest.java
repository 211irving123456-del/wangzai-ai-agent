package com.example.ai_pet_agent.tool;

import com.example.ai_pet_agent.weather.OpenMeteoWeatherClient;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GetCurrentWeatherToolTest {

    private final JsonMapper mapper =
            JsonMapper.builder().build();

    @Test
    void testSchema() {

        Map<String, Object> schema =
                GetCurrentWeatherTool.schema();

        assertEquals("function", schema.get("type"));

        Map<?, ?> function =
                (Map<?, ?>) schema.get("function");

        assertEquals(
                "getCurrentWeather",
                function.get("name")
        );
    }

    @Test
    void testWeatherSuccess() {

        AtomicReference<String> requestedCity =
                new AtomicReference<>();

        OpenMeteoWeatherClient fakeClient =
                new OpenMeteoWeatherClient() {

            @Override
            public Map<String, Object> getCurrentWeather(
                    String city) {

                requestedCity.set(city);

                return Map.of(
                        "success", true,
                        "city", "广州",
                        "temperature_c", 13.3,
                        "weather_code", 3,
                        "condition", "overcast",
                        "precipitation_mm", 0.0
                );
            }
        };

        GetCurrentWeatherTool tool =
                new GetCurrentWeatherTool(fakeClient);

        JsonNode result = mapper.readTree(
                tool.execute("{\"city\":\"广州\"}")
        );

        assertTrue(result.path("success").asBoolean());
        assertEquals("广州", requestedCity.get());
        assertEquals(3, result.path("weather_code").asInt());
        assertEquals(
                "overcast",
                result.path("condition").asText()
        );
    }

    @Test
    void testWeatherApiFailure() {

        OpenMeteoWeatherClient fakeClient =
                new OpenMeteoWeatherClient() {

            @Override
            public Map<String, Object> getCurrentWeather(
                    String city) {

                return Map.of(
                        "success", false,
                        "error_code", "WEATHER_API_UNAVAILABLE",
                        "message", "天气服务暂时不可用"
                );
            }
        };

        GetCurrentWeatherTool tool =
                new GetCurrentWeatherTool(fakeClient);

        JsonNode result = mapper.readTree(
                tool.execute("{\"city\":\"广州\"}")
        );

        assertFalse(result.path("success").asBoolean());

        assertEquals(
                "WEATHER_API_UNAVAILABLE",
                result.path("error_code").asText()
        );
    }

    @Test
    void testInvalidArguments() {

        AtomicInteger calls = new AtomicInteger();

        OpenMeteoWeatherClient fakeClient =
                new OpenMeteoWeatherClient() {

            @Override
            public Map<String, Object> getCurrentWeather(
                    String city) {

                calls.incrementAndGet();
                return Map.of("success", true);
            }
        };

        GetCurrentWeatherTool tool =
                new GetCurrentWeatherTool(fakeClient);

        String[] invalidInputs = {
                "{}",
                "{\"city\":\"\"}",
                "{\"city\":123}",
                "{invalid-json}"
        };

        for (String input : invalidInputs) {

            JsonNode result =
                    mapper.readTree(tool.execute(input));

            assertFalse(
                    result.path("success").asBoolean()
            );
        }

        assertEquals(0, calls.get());
    }

    @Test
    void testNullWeatherResult() {

        OpenMeteoWeatherClient fakeClient =
                new OpenMeteoWeatherClient() {

            @Override
            public Map<String, Object> getCurrentWeather(
                    String city) {

                return null;
            }
        };

        GetCurrentWeatherTool tool =
                new GetCurrentWeatherTool(fakeClient);

        JsonNode result = mapper.readTree(
                tool.execute("{\"city\":\"广州\"}")
        );

        assertFalse(result.path("success").asBoolean());

        assertEquals(
                "INVALID_WEATHER_RESULT",
                result.path("error_code").asText()
        );
    }
}
