
package com.example.ai_pet_agent.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class ToolRegistry {

    // A registered tool contains its schema and handler.
    private record RegisteredTool(
            Map<String, Object> schema,
            Function<String, String> handler
    ) {}

    private final Map<String, RegisteredTool> tools =
            new LinkedHashMap<>();

    // Keep the old registration API for existing tests.
    public ToolRegistry register(
            String name,
            Function<String, String> handler
    ) {
        return registerInternal(name, null, handler);
    }

    // New registration API: name + schema + handler.
    public ToolRegistry register(
            String name,
            Map<String, Object> schema,
            Function<String, String> handler
    ) {
        Objects.requireNonNull(
                schema, "工具 Schema 不能为空"
        );

        Object functionValue = schema.get("function");

        if (!(functionValue instanceof Map<?, ?> function)
                || !Objects.equals(
                        name, function.get("name"))) {

            throw new IllegalArgumentException(
                    "工具名称与 Schema 不一致：" + name
            );
        }

        return registerInternal(
                name, Map.copyOf(schema), handler
        );
    }

    private ToolRegistry registerInternal(
            String name,
            Map<String, Object> schema,
            Function<String, String> handler
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "工具名称不能为空"
            );
        }

        Objects.requireNonNull(
                handler, "工具处理函数不能为空"
        );

        if (tools.containsKey(name)) {
            throw new IllegalArgumentException(
                    "工具重复注册：" + name
            );
        }

        tools.put(
                name,
                new RegisteredTool(schema, handler)
        );

        return this;
    }

    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    public String execute(
            String name,
            String arguments
    ) {
        RegisteredTool tool = tools.get(name);

        if (tool == null) {
            throw new IllegalArgumentException(
                    "未知工具：" + name
            );
        }

        return tool.handler().apply(arguments);
    }

    // Export every tool schema to DeepSeek.
    public List<Map<String, Object>> schemas() {

        List<Map<String, Object>> result =
                new ArrayList<>();

        for (Map.Entry<String, RegisteredTool> entry
                : tools.entrySet()) {

            if (entry.getValue().schema() == null) {
                throw new IllegalStateException(
                        "工具尚未配置 Schema："
                                + entry.getKey()
                );
            }

            result.add(entry.getValue().schema());
        }

        return List.copyOf(result);
    }
}
