
package com.example.ai_pet_agent.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    @Test
    void testToolRegistration() {

        ToolRegistry registry = new ToolRegistry();

        registry.register(
                "showExpression",
                args -> "success"
        );

        assertTrue(
                registry.contains("showExpression")
        );

        assertFalse(
                registry.contains("unknownTool")
        );
    }

    @Test
    void testToolExecution() {

        ToolRegistry registry = new ToolRegistry();

        registry.register(
                "showExpression",
                args -> "执行参数：" + args
        );

        String result = registry.execute(
                "showExpression",
                "{\"action\":\"happy\"}"
        );

        assertEquals(
                "执行参数：{\"action\":\"happy\"}",
                result
        );
    }

    @Test
    void testUnknownTool() {

        ToolRegistry registry = new ToolRegistry();

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.execute(
                        "unknownTool", "{}"
                )
        );
    }

    @Test
    void testDuplicateRegistration() {

        ToolRegistry registry = new ToolRegistry();

        registry.register(
                "showExpression",
                args -> "first"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(
                        "showExpression",
                        args -> "second"
                )
        );
    }
}
