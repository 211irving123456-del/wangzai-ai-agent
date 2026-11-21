package com.example.ai_pet_agent.service;

/**
 * 标准天气表情规则。
 * 这里不调用大模型，也不访问设备。
 */
public final class WeatherExpressionPolicy {

    private WeatherExpressionPolicy() {}

    public static String expectedExpression(String condition) {

        if (condition == null) {
            return null;
        }

        return switch (condition) {
            case "clear", "partly_cloudy" -> "happy";

            case "overcast", "rainy", "foggy", "snowy" -> "sad";

            default -> null;
        };
    }
}
