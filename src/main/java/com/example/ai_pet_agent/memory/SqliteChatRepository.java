
package com.example.ai_pet_agent.memory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SqliteChatRepository {

    private static final int MAX_TURNS = 5;

    private final String jdbcUrl;

    public SqliteChatRepository(Path databasePath) {
        try {
            Path path = databasePath.toAbsolutePath();
            Files.createDirectories(path.getParent());

            this.jdbcUrl = "jdbc:sqlite:" + path;

            initializeDatabase();

        } catch (Exception e) {
            throw new IllegalStateException(
                    "初始化 SQLite 数据库失败", e
            );
        }
    }

    // 创建数据库表（如果已经存在，就不重复创建）
    private void initializeDatabase() throws SQLException {

        String sql = """
                CREATE TABLE IF NOT EXISTS conversation_turns (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id TEXT NOT NULL,
                    user_message TEXT NOT NULL,
                    assistant_reply TEXT NOT NULL
                )
                """;

        try (Connection conn = getConnection();
             Statement statement = conn.createStatement()) {

            statement.executeUpdate(sql);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    // 保存一轮聊天，并只保留该会话最近五轮
    public void saveTurn(
            String sessionId,
            String userMessage,
            String assistantReply) {

        validateSessionId(sessionId);

        String insertSql = """
                INSERT INTO conversation_turns
                (session_id, user_message, assistant_reply)
                VALUES (?, ?, ?)
                """;

        String deleteOldSql = """
                DELETE FROM conversation_turns
                WHERE session_id = ?
                  AND id NOT IN (
                    SELECT id FROM conversation_turns
                    WHERE session_id = ?
                    ORDER BY id DESC
                    LIMIT ?
                  )
                """;

        try (Connection conn = getConnection()) {

            conn.setAutoCommit(false);

            try {
                try (PreparedStatement ps =
                             conn.prepareStatement(insertSql)) {

                    ps.setString(1, sessionId);
                    ps.setString(2, userMessage);
                    ps.setString(3, assistantReply);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps =
                             conn.prepareStatement(deleteOldSql)) {

                    ps.setString(1, sessionId);
                    ps.setString(2, sessionId);
                    ps.setInt(3, MAX_TURNS);
                    ps.executeUpdate();
                }

                conn.commit();

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "保存聊天记录失败", e
            );
        }
    }

    // 读取该会话最近五轮，按时间顺序返回
    public List<ConversationMemory.Turn> getHistory(
            String sessionId) {

        validateSessionId(sessionId);

        String sql = """
                SELECT user_message, assistant_reply
                FROM (
                    SELECT id, user_message, assistant_reply
                    FROM conversation_turns
                    WHERE session_id = ?
                    ORDER BY id DESC
                    LIMIT ?
                )
                ORDER BY id ASC
                """;

        List<ConversationMemory.Turn> history =
                new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement ps =
                     conn.prepareStatement(sql)) {

            ps.setString(1, sessionId);
            ps.setInt(2, MAX_TURNS);

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    history.add(new ConversationMemory.Turn(
                            rs.getString("user_message"),
                            rs.getString("assistant_reply")
                    ));
                }
            }

            return history;

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "读取聊天记录失败", e
            );
        }
    }

    // 删除指定会话的聊天记录
    public void clear(String sessionId) {

        validateSessionId(sessionId);

        String sql = """
                DELETE FROM conversation_turns
                WHERE session_id = ?
                """;

        try (Connection conn = getConnection();
             PreparedStatement ps =
                     conn.prepareStatement(sql)) {

            ps.setString(1, sessionId);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "清除聊天记录失败", e
            );
        }
    }

    private void validateSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException(
                    "sessionId 不能为空"
            );
        }
    }
}
