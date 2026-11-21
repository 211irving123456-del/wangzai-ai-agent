package com.example.ai_pet_agent.tool;

import com.example.ai_pet_agent.weather.OpenMeteoWeatherClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GetCurrentWeatherTool {

    private static final Logger log =
            LoggerFactory.getLogger(GetCurrentWeatherTool.class);

    private final JsonMapper mapper =
            JsonMapper.builder().build();

    private final OpenMeteoWeatherClient weatherClient;

    public GetCurrentWeatherTool() {
        this(new OpenMeteoWeatherClient());
    }

    // 支持单元测试注入模拟客户端
    public GetCurrentWeatherTool(
            OpenMeteoWeatherClient weatherClient) {
        this.weatherClient = Objects.requireNonNull(weatherClient);
    }

    /**
     * 向 DeepSeek 描述天气工具
     */
    public static Map<String, Object> schema() {

        return Map.of(
            "type", "function",
            "function", Map.of(
                "name", "getCurrentWeather",
                "description",
                "查询指定城市当前真实天气，包括温度、降水量、" +
                "天气代码、天气分类和数据时间。" +
                "当用户要求根据天气决定表情时，必须先查询天气。" +
                "查询失败时不能编造天气，也不能据此控制设备。",

                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "city", Map.of(
                            "type", "string",
                            "description",
                            "需要查询的城市名称，例如广州、北京、Shanghai"
                        )
                    ),
                    "required", List.of("city"),
                    "additionalProperties", false
                )
            )
        );
    }

    /**
     * 执行天气工具
     */
    public String execute(String arguments) {

        String city;

        // 1. 验证模型生成的参数
        try {
            JsonNode args = mapper.readTree(arguments);

            if (!args.isObject()
                    || args.size() != 1
                    || !args.path("city").isTextual()) {

                return error(
                    "INVALID_ARGUMENTS",
                    "参数必须是包含city字符串的JSON对象"
                );
            }

            city = args.path("city").asText().trim();

            if (city.isBlank() || city.length() > 80) {
                return error(
                    "INVALID_CITY",
                    "城市名称不能为空或超过80个字符"
                );
            }

        } catch (Exception e) {
            return error(
                "INVALID_JSON",
                "工具参数不是有效JSON"
            );
        }

        // 2. 调用真实天气客户端
        try {
            log.info(
                "开始查询天气：requestId={}",
                MDC.get("requestId")
            );

            Map<String, Object> result =
                    weatherClient.getCurrentWeather(city);

            if (result == null
                    || !(result.get("success") instanceof Boolean)) {

                return error(
                    "INVALID_WEATHER_RESULT",
                    "天气客户端返回无效结果"
                );
            }

            log.info(
                "天气查询结束：success={}, requestId={}",
                result.get("success"),
                MDC.get("requestId")
            );

            return mapper.writeValueAsString(result);

        } catch (Exception e) {
            log.warn("天气工具执行异常", e);

            return error(
                "WEATHER_TOOL_ERROR",
                "天气工具执行失败"
            );
        }
    }

    private String error(String code, String message) {

        return mapper.writeValueAsString(
            Map.of(
                "success", false,
                "error_code", code,
                "message", message
            )
        );
    }
}
