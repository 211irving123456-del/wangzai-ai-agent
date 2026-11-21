
package com.example.ai_pet_agent.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqliteChatRepositoryTest {

    @TempDir
    Path tempDir;

    // 测试一：创建新对象后仍然能读取原来的数据
    @Test
    void testPersistence() {

        Path dbPath = tempDir.resolve("wangzai.db");

        SqliteChatRepository first =
                new SqliteChatRepository(dbPath);

        first.saveTurn(
                "session-A",
                "我叫小明",
                "你好，小明！"
        );

        // 模拟下一次重新打开同一个数据库文件
        SqliteChatRepository second =
                new SqliteChatRepository(dbPath);

        List<ConversationMemory.Turn> history =
                second.getHistory("session-A");

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

    // 测试二：不同会话的记录相互隔离
    @Test
    void testSessionIsolation() {

        SqliteChatRepository repository =
                new SqliteChatRepository(
                        tempDir.resolve("isolation.db")
                );

        repository.saveTurn(
                "session-A", "我是甲", "你好，甲"
        );

        repository.saveTurn(
                "session-B", "我是乙", "你好，乙"
        );

        assertEquals(
                "我是甲",
                repository.getHistory("session-A")
                        .get(0).userMessage()
        );

        assertEquals(
                "我是乙",
                repository.getHistory("session-B")
                        .get(0).userMessage()
        );
    }

    // 测试三：最多保存最近五轮
    @Test
    void testHistoryLimit() {

        SqliteChatRepository repository =
                new SqliteChatRepository(
                        tempDir.resolve("limit.db")
                );

        for (int i = 1; i <= 6; i++) {
            repository.saveTurn(
                    "session-A",
                    "消息" + i,
                    "回复" + i
            );
        }

        List<ConversationMemory.Turn> history =
                repository.getHistory("session-A");

        assertEquals(5, history.size());

        assertEquals(
                "消息2",
                history.get(0).userMessage()
        );

        assertEquals(
                "消息6",
                history.get(4).userMessage()
        );
    }

    // 测试四：清空历史记录
    @Test
    void testClearHistory() {

        SqliteChatRepository repository =
                new SqliteChatRepository(
                        tempDir.resolve("clear.db")
                );

        repository.saveTurn(
                "session-A", "你好", "你好呀"
        );

        repository.clear("session-A");

        assertTrue(
                repository.getHistory("session-A").isEmpty()
        );
    }
}
