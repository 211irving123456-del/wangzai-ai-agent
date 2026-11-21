
package com.example.ai_pet_agent.memory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ConversationMemory {

    private static final int MAX_TURNS = 5;

    private final SqliteChatRepository repository;

    private final ConcurrentHashMap<String, Deque<Turn>>
            sessions = new ConcurrentHashMap<>();

    public record Turn(
            String userMessage,
            String assistantReply
    ) {}

    // 兼容原来的单元测试：使用纯内存
    public ConversationMemory() {
        this.repository = null;
    }

    // Spring Boot 正式运行时：使用 SQLite
    @Autowired
    public ConversationMemory(
            @Value("${wangzai.memory.db-path:data/wangzai.db}")
            String databasePath) {

        this.repository =
                new SqliteChatRepository(Path.of(databasePath));
    }

    // 读取指定会话历史
    public List<Turn> getHistory(String sessionId) {

        if (repository != null) {
            return repository.getHistory(sessionId);
        }

        Deque<Turn> turns = sessions.get(sessionId);

        if (turns == null) {
            return List.of();
        }

        synchronized (turns) {
            return List.copyOf(turns);
        }
    }

    // 保存一轮聊天
    public void saveTurn(
            String sessionId,
            String userMessage,
            String assistantReply) {

        if (repository != null) {
            repository.saveTurn(
                    sessionId,
                    userMessage,
                    assistantReply
            );
            return;
        }

        Deque<Turn> turns = sessions.computeIfAbsent(
                sessionId,
                key -> new ArrayDeque<>()
        );

        synchronized (turns) {

            turns.addLast(
                    new Turn(userMessage, assistantReply)
            );

            while (turns.size() > MAX_TURNS) {
                turns.removeFirst();
            }
        }
    }

    // 删除指定会话历史
    public void clear(String sessionId) {

        if (repository != null) {
            repository.clear(sessionId);
            return;
        }

        sessions.remove(sessionId);
    }
}
