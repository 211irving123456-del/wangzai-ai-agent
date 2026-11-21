
package com.example.ai_pet_agent.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConversationMemoryTest {

    private ConversationMemory memory;

    @BeforeEach
    void setUp() {
        memory = new ConversationMemory();
    }

    // 测试一：保存和读取历史消息
    @Test
    void testSaveAndReadHistory() {

        memory.saveTurn(
                "session-001",
                "我叫小明",
                "你好，小明！"
        );

        List<ConversationMemory.Turn> history =
                memory.getHistory("session-001");

        assertEquals(1, history.size());

        assertEquals(
                "我叫小明",
                history.get(0).userMessage()
        );

        assertEquals(
                "你好，小明！",
                history.get(0).assistantReply()
        );
    }

    // 测试二：不同会话之间互不干扰
    @Test
    void testSessionIsolation() {

        memory.saveTurn(
                "session-A",
                "我叫小明",
                "你好，小明"
        );

        memory.saveTurn(
                "session-B",
                "我叫小红",
                "你好，小红"
        );

        assertEquals(
                "我叫小明",
                memory.getHistory("session-A")
                        .get(0).userMessage()
        );

        assertEquals(
                "我叫小红",
                memory.getHistory("session-B")
                        .get(0).userMessage()
        );
    }

    // 测试三：只保留最近五轮对话
    @Test
    void testHistoryLimit() {

        for (int i = 1; i <= 6; i++) {
            memory.saveTurn(
                    "session-001",
                    "用户消息" + i,
                    "旺仔回复" + i
            );
        }

        List<ConversationMemory.Turn> history =
                memory.getHistory("session-001");

        assertEquals(5, history.size());

        // 第 1 轮应该已经被移除
        assertEquals(
                "用户消息2",
                history.get(0).userMessage()
        );

        // 最近一轮应该仍然存在
        assertEquals(
                "用户消息6",
                history.get(4).userMessage()
        );
    }

    // 测试四：清除指定会话的记忆
    @Test
    void testClearHistory() {

        memory.saveTurn(
                "session-001",
                "你好",
                "你好呀"
        );

        memory.clear("session-001");

        assertTrue(
                memory.getHistory("session-001").isEmpty()
        );
    }
}
