
package com.example.ai_pet_agent.tool;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistrySchemaTest {

    @Test
    void testSchemaExport() {

        ToolRegistry registry = new ToolRegistry()
            .register(
                "showExpression",
                ShowExpressionTool.schema(),
                args -> "ok"
            )
            .register(
                "getDeviceStatus",
                GetDeviceStatusTool.schema(),
                args -> "ok"
            );

        assertEquals(2, registry.schemas().size());

        Map<?, ?> first = (Map<?, ?>)
            registry.schemas().get(0).get("function");

        Map<?, ?> second = (Map<?, ?>)
            registry.schemas().get(1).get("function");

        assertEquals("showExpression", first.get("name"));
        assertEquals("getDeviceStatus", second.get("name"));
    }

    @Test
    void testSchemaNameMismatch() {

        ToolRegistry registry = new ToolRegistry();

        assertThrows(
            IllegalArgumentException.class,
            () -> registry.register(
                "wrongName",
                ShowExpressionTool.schema(),
                args -> "ok"
            )
        );
    }

    @Test
    void testMissingSchemaRejected() {

        ToolRegistry registry = new ToolRegistry();

        registry.register("legacyTool", args -> "ok");

        assertThrows(
            IllegalStateException.class,
            registry::schemas
        );
    }
}
