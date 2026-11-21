package com.example.ai_pet_agent.weather;


import java.net.URLEncoder;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;




import java.nio.charset.StandardCharsets;

import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class OpenMeteoWeatherClient {

    private final OkHttpClient client;
    private final JsonMapper mapper;

    public OpenMeteoWeatherClient() {
        this.client = createHttpClient();

        this.mapper = JsonMapper.builder().build();
    }

    public Map<String, Object> getCurrentWeather(String city) {

        if (city == null || city.isBlank()
                || city.length() > 80) {
            return error("INVALID_CITY", "城市名称不合法");
        }

        try {
            // 1. 城市地理编码
            String geoUrl =
                    "https://geocoding-api.open-meteo.com/v1/search"
                    + "?name=" + encode(city.trim())
                    + "&count=1&language=zh";

            JsonNode geo = getJson(geoUrl);
            JsonNode location = geo.path("results").path(0);

            if (!location.isObject()
                    || !location.path("latitude").isNumber()
                    || !location.path("longitude").isNumber()) {
                return error("CITY_NOT_FOUND", "未找到对应城市");
            }

            double latitude =
                    location.path("latitude").asDouble();
            double longitude =
                    location.path("longitude").asDouble();

            String timezone =
                    location.path("timezone").asText();

            if (timezone.isBlank()) {
                timezone = "auto";
            }

            // 2. 获取当前天气
            String weatherUrl =
                    "https://api.open-meteo.com/v1/forecast"
                    + "?latitude=" + latitude
                    + "&longitude=" + longitude
                    + "&current=temperature_2m,"
                    + "precipitation,weather_code,is_day"
                    + "&timezone=" + encode(timezone);

            JsonNode weather = getJson(weatherUrl);
            JsonNode current = weather.path("current");

            if (!current.isObject()
                    || !current.path("weather_code").isNumber()
                    || !current.path("temperature_2m").isNumber()
                    || !current.path("precipitation").isNumber()
                    || current.path("time").asText().isBlank()) {

                return error(
                        "INVALID_WEATHER_DATA",
                        "天气服务返回的数据不完整"
                );
            }

            int code = current.path("weather_code").asInt();

            // 3. 构造结构化结果
            Map<String, Object> result = new LinkedHashMap<>();

            result.put("success", true);
            result.put("source", "Open-Meteo");
            result.put("city", location.path("name").asText());
            result.put("country",
                    location.path("country").asText());
            result.put("latitude", latitude);
            result.put("longitude", longitude);
            result.put("time", current.path("time").asText());
            result.put("timezone", timezone);
            result.put("temperature_c",
                    current.path("temperature_2m").asDouble());
            result.put("precipitation_mm",
                    current.path("precipitation").asDouble());
            result.put("weather_code", code);
            result.put("condition", classifyWeather(code));
            result.put("is_day",
                    current.path("is_day").asInt() == 1);

            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error("WEATHER_INTERRUPTED", "天气请求被中断");

        } catch (Exception e) {
            return error(
                    "WEATHER_API_UNAVAILABLE",
                    "天气服务暂时不可用："
                            + e.getClass().getSimpleName()
            );
        }
    }

    private static OkHttpClient createHttpClient() {

        InetAddress fallback = parseFallbackIp(
                System.getenv("WANGZAI_WEATHER_FALLBACK_IP")
        );

        return new OkHttpClient.Builder()
                .dns(hostname -> {

                    if (fallback == null
                            || !"api.open-meteo.com".equalsIgnoreCase(hostname)) {
                        return Dns.SYSTEM.lookup(hostname);
                    }

                    List<InetAddress> addresses = new ArrayList<>();
                    addresses.add(fallback);

                    try {
                        for (InetAddress address : Dns.SYSTEM.lookup(hostname)) {
                            if (!address.equals(fallback)) {
                                addresses.add(address);
                            }
                        }
                    } catch (UnknownHostException ignored) {
                        // 已有配置的备用地址，可继续尝试
                    }

                    return addresses;
                })
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(25, TimeUnit.SECONDS)
                .build();
    }

    private static InetAddress parseFallbackIp(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        String[] parts = value.trim().split("\\.", -1);

        if (parts.length != 4) {
            throw new IllegalArgumentException("备用地址必须是IPv4");
        }

        byte[] bytes = new byte[4];

        for (int i = 0; i < 4; i++) {
            if (!parts[i].matches("[0-9]{1,3}")) {
                throw new IllegalArgumentException("无效的备用IPv4地址");
            }

            int number = Integer.parseInt(parts[i]);

            if (number > 255) {
                throw new IllegalArgumentException("无效的备用IPv4地址");
            }

            bytes[i] = (byte) number;
        }

        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("无效的备用IPv4地址", e);
        }
    }

    private JsonNode getJson(String url) throws Exception {

        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful() || response.body() == null) {
                throw new IllegalStateException(
                        "Weather HTTP " + response.code()
                );
            }

            return mapper.readTree(response.body().string());
        }
    }

    static String classifyWeather(int code) {

        if (code == 0 || code == 1) {
            return "clear";
        }

        if (code == 2) {
            return "partly_cloudy";
        }

        if (code == 3) {
            return "overcast";
        }

        if (code == 45 || code == 48) {
            return "foggy";
        }

        if ((code >= 51 && code <= 67)
                || (code >= 80 && code <= 82)
                || (code >= 95 && code <= 99)) {
            return "rainy";
        }

        if ((code >= 71 && code <= 77)
                || code == 85 || code == 86) {
            return "snowy";
        }

        return "other";
    }

    private static String encode(String text) {
        return URLEncoder.encode(
                text,
                StandardCharsets.UTF_8
        );
    }

    private static Map<String, Object> error(
            String code,
            String message) {

        return Map.of(
                "success", false,
                "error_code", code,
                "message", message
        );
    }
}
