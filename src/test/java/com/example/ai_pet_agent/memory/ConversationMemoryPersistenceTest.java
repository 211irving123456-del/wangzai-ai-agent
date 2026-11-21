
package com.example.ai_pet_agent.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConversationMemoryPersistenceTest {

    @TempDir
    Path tempDir;

    @Test
    void testMemorySurvivesNewInstance() {

        String dbPath =
                tempDir.resolve("memory.db").toString();

        // 第一个记忆对象：保存对话
        ConversationMemory first =
                new ConversationMemory(dbPath);

        first.saveTurn(
                "session-A",
                "我叫小明",
                "你好，小明！"
        );

        // 模拟程序重新创建记忆组件
        ConversationMemory second =
                new ConversationMemory(dbPath);

        // 新对象应该能够从 SQLite 恢复历史
        var history = second.getHistory("session-A");

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
}
