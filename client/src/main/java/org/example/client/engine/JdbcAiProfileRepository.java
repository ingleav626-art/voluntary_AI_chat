package org.example.client.engine;

import com.voluntary.chat.server.entity.AiMemory;
import com.voluntary.chat.server.entity.AiProfile;
import com.voluntary.chat.server.entity.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 数据访问层（JDBC 直连 H2，无 MyBatis）
 *
 * <p>客户包专用。提供 AI 角色、消息、记忆的 JDBC 直连操作。</p>
 */
public class JdbcAiProfileRepository implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcAiProfileRepository.class);

    private final Connection connection;

    public JdbcAiProfileRepository(String h2Path) {
        try {
            // 加载 H2 驱动
            Class.forName("org.h2.Driver");
            String url = "jdbc:h2:file:" + h2Path + "/ai;AUTO_SERVER=TRUE";
            this.connection = DriverManager.getConnection(url, "sa", "");
            LOG.info("H2 数据库连接成功: {}", url);
            initSchema();
        } catch (Exception e) {
            LOG.error("H2 数据库连接失败", e);
            throw new RuntimeException("H2 数据库连接失败", e);
        }
    }

    public JdbcAiProfileRepository(Connection connection) {
        this.connection = connection;
        initSchema();
    }

    private void initSchema() {
        // 建表（从 schema-h2.sql 提取 AI 模块相关表）
        executeSql("CREATE TABLE IF NOT EXISTS ai_profile (\n" +
                "    id BIGINT NOT NULL,\n" +
                "    user_id BIGINT NOT NULL,\n" +
                "    name VARCHAR(50) NOT NULL,\n" +
                "    avatar VARCHAR(500) DEFAULT NULL,\n" +
                "    persona VARCHAR(2000) DEFAULT NULL,\n" +
                "    system_prompt TEXT DEFAULT NULL,\n" +
                "    model_provider VARCHAR(20) NOT NULL,\n" +
                "    model VARCHAR(50) NOT NULL,\n" +
                "    api_key_enc VARCHAR(500) NOT NULL,\n" +
                "    is_group TINYINT DEFAULT 0,\n" +
                "    temperature DECIMAL(3,2) DEFAULT 0.70,\n" +
                "    max_tokens INT DEFAULT 2048,\n" +
                "    status TINYINT DEFAULT 0,\n" +
                "    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n" +
                "    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n" +
                "    is_deleted TINYINT DEFAULT 0,\n" +
                "    PRIMARY KEY (id)\n" +
                ")");

        // 兼容已有数据库：添加 base_url 字段（如果不存在）
        executeSql("ALTER TABLE ai_profile ADD COLUMN IF NOT EXISTS base_url VARCHAR(500) DEFAULT NULL");

        executeSql("CREATE TABLE IF NOT EXISTS message (\n" +
                "    id BIGINT NOT NULL,\n" +
                "    session_id VARCHAR(100) NOT NULL,\n" +
                "    sender_id BIGINT NOT NULL,\n" +
                "    sender_type TINYINT DEFAULT 0,\n" +
                "    target_id BIGINT NOT NULL,\n" +
                "    target_type TINYINT DEFAULT 0,\n" +
                "    type TINYINT DEFAULT 0,\n" +
                "    content TEXT NOT NULL,\n" +
                "    extra TEXT DEFAULT NULL,\n" +
                "    recall_time TIMESTAMP DEFAULT NULL,\n" +
                "    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n" +
                "    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n" +
                "    is_deleted TINYINT DEFAULT 0,\n" +
                "    PRIMARY KEY (id)\n" +
                ")");

        executeSql("CREATE TABLE IF NOT EXISTS ai_memory (\n" +
                "    id BIGINT NOT NULL,\n" +
                "    ai_id BIGINT NOT NULL,\n" +
                "    user_id BIGINT NOT NULL,\n" +
                "    session_id VARCHAR(100) DEFAULT NULL,\n" +
                "    summary VARCHAR(500) NOT NULL,\n" +
                "    keywords VARCHAR(200) DEFAULT NULL,\n" +
                "    importance DECIMAL(3,2) DEFAULT 0.50,\n" +
                "    vector_id VARCHAR(100) DEFAULT NULL,\n" +
                "    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n" +
                "    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n" +
                "    is_deleted TINYINT DEFAULT 0,\n" +
                "    PRIMARY KEY (id)\n" +
                ")");
    }

    private void executeSql(String sql) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            LOG.warn("执行建表SQL失败: {}", e.getMessage());
        }
    }

    // ==================== AI Profile CRUD ====================

    public List<AiProfile> listAiProfiles(Long userId) {
        List<AiProfile> list = new ArrayList<>();
        String sql = "SELECT * FROM ai_profile WHERE user_id = ? AND status = 0 AND is_deleted = 0 ORDER BY create_time DESC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapToAiProfile(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("查询AI角色列表失败", e);
        }
        return list;
    }

    public AiProfile findAiProfileById(Long aiId) {
        String sql = "SELECT * FROM ai_profile WHERE id = ? AND is_deleted = 0";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, aiId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapToAiProfile(rs);
                }
            }
        } catch (SQLException e) {
            LOG.error("查询AI角色失败: aiId={}", aiId, e);
        }
        return null;
    }

    public void insertAiProfile(AiProfile profile) {
        String sql = "INSERT INTO ai_profile (id, user_id, name, avatar, persona, system_prompt, " +
                "model_provider, model, api_key_enc, base_url, is_group, temperature, max_tokens, " +
                "status, create_time, update_time, is_deleted) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, profile.getId());
            ps.setLong(2, profile.getUserId());
            ps.setString(3, profile.getName());
            ps.setString(4, profile.getAvatar());
            ps.setString(5, profile.getPersona());
            ps.setString(6, profile.getSystemPrompt());
            ps.setString(7, profile.getModelProvider());
            ps.setString(8, profile.getModel());
            ps.setString(9, profile.getApiKeyEnc());
            ps.setString(10, profile.getBaseUrl());
            ps.setBoolean(11, profile.getIsGroup() != null ? profile.getIsGroup() : false);
            ps.setDouble(12, profile.getTemperature() != null ? profile.getTemperature() : 0.7);
            ps.setInt(13, profile.getMaxTokens() != null ? profile.getMaxTokens() : 2048);
            ps.setInt(14, 0);
            ps.setTimestamp(15, Timestamp.valueOf(LocalDateTime.now()));
            ps.setTimestamp(16, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(17, 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("插入AI角色失败", e);
            throw new RuntimeException("插入AI角色失败", e);
        }
    }

    public void updateAiProfile(AiProfile profile) {
        String sql = "UPDATE ai_profile SET name = ?, avatar = ?, persona = ?, system_prompt = ?, " +
                "model_provider = ?, model = ?, api_key_enc = ?, base_url = ?, is_group = ?, " +
                "temperature = ?, max_tokens = ?, update_time = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, profile.getName());
            ps.setString(2, profile.getAvatar());
            ps.setString(3, profile.getPersona());
            ps.setString(4, profile.getSystemPrompt());
            ps.setString(5, profile.getModelProvider());
            ps.setString(6, profile.getModel());
            ps.setString(7, profile.getApiKeyEnc());
            ps.setString(8, profile.getBaseUrl());
            ps.setBoolean(9, profile.getIsGroup() != null ? profile.getIsGroup() : false);
            ps.setDouble(10, profile.getTemperature() != null ? profile.getTemperature() : 0.7);
            ps.setInt(11, profile.getMaxTokens() != null ? profile.getMaxTokens() : 2048);
            ps.setTimestamp(12, Timestamp.valueOf(LocalDateTime.now()));
            ps.setLong(13, profile.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("更新AI角色失败: aiId={}", profile.getId(), e);
            throw new RuntimeException("更新AI角色失败", e);
        }
    }

    public void deleteAiProfile(Long aiId) {
        String sql = "UPDATE ai_profile SET is_deleted = 1, update_time = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setLong(2, aiId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("删除AI角色失败: aiId={}", aiId, e);
            throw new RuntimeException("删除AI角色失败", e);
        }
    }

    // ==================== Message CRUD ====================

    public List<Message> findMessages(String sessionId, int limit) {
        List<Message> list = new ArrayList<>();
        String sql = "SELECT * FROM message WHERE session_id = ? AND recall_time IS NULL " +
                "AND is_deleted = 0 ORDER BY create_time DESC LIMIT ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapToMessage(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("查询消息历史失败: sessionId={}", sessionId, e);
        }
        // 反转成时间正序
        List<Message> reversed = new ArrayList<>(list);
        java.util.Collections.reverse(reversed);
        return reversed;
    }

    public List<Message> findMessagesByPrefix(String sessionIdPrefix, int limit) {
        List<Message> list = new ArrayList<>();
        String sql = "SELECT * FROM message WHERE session_id LIKE ? AND recall_time IS NULL " +
                "AND is_deleted = 0 ORDER BY create_time DESC LIMIT ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionIdPrefix + "%");
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapToMessage(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("查询消息历史失败(前缀): sessionIdPrefix={}", sessionIdPrefix, e);
        }
        // 反转成时间正序
        List<Message> reversed = new ArrayList<>(list);
        java.util.Collections.reverse(reversed);
        return reversed;
    }

    public void insertMessage(Message message) {
        String sql = "INSERT INTO message (id, session_id, sender_id, sender_type, target_id, target_type, " +
                "type, content, extra, create_time, update_time, is_deleted) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, message.getId());
            ps.setString(2, message.getSessionId());
            ps.setLong(3, message.getSenderId());
            ps.setInt(4, message.getSenderType());
            ps.setLong(5, message.getTargetId());
            ps.setInt(6, message.getTargetType());
            ps.setInt(7, message.getType());
            ps.setString(8, message.getContent());
            ps.setString(9, message.getExtra());
            ps.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now()));
            ps.setTimestamp(11, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(12, 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("插入消息失败", e);
            throw new RuntimeException("插入消息失败", e);
        }
    }

    // ==================== AI Memory CRUD ====================

    public List<AiMemory> findMemories(Long aiId, Long userId, int limit) {
        List<AiMemory> list = new ArrayList<>();
        String sql = "SELECT * FROM ai_memory WHERE ai_id = ? AND user_id = ? AND is_deleted = 0 " +
                "ORDER BY create_time DESC LIMIT ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, aiId);
            ps.setLong(2, userId);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapToAiMemory(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("查询AI记忆失败: aiId={}, userId={}", aiId, userId, e);
        }
        return list;
    }

    public void insertAiMemory(AiMemory memory) {
        String sql = "INSERT INTO ai_memory (id, ai_id, user_id, session_id, summary, keywords, " +
                "importance, create_time, update_time, is_deleted) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, memory.getId());
            ps.setLong(2, memory.getAiId());
            ps.setLong(3, memory.getUserId());
            ps.setString(4, memory.getSessionId());
            ps.setString(5, memory.getSummary());
            ps.setString(6, memory.getKeywords());
            ps.setDouble(7, memory.getImportance() != null ? memory.getImportance() : 0.5);
            ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            ps.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(10, 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("插入AI记忆失败", e);
        }
    }

    // ==================== ResultSet 映射 ====================

    private AiProfile mapToAiProfile(ResultSet rs) throws SQLException {
        AiProfile profile = new AiProfile();
        profile.setId(rs.getLong("id"));
        profile.setUserId(rs.getLong("user_id"));
        profile.setName(rs.getString("name"));
        profile.setAvatar(rs.getString("avatar"));
        profile.setPersona(rs.getString("persona"));
        profile.setSystemPrompt(rs.getString("system_prompt"));
        profile.setModelProvider(rs.getString("model_provider"));
        profile.setModel(rs.getString("model"));
        profile.setApiKeyEnc(rs.getString("api_key_enc"));
        profile.setBaseUrl(rs.getString("base_url"));
        profile.setIsGroup(rs.getBoolean("is_group"));
        profile.setTemperature(rs.getDouble("temperature"));
        profile.setMaxTokens(rs.getInt("max_tokens"));
        profile.setStatus(rs.getInt("status"));
        profile.setIsDeleted(rs.getInt("is_deleted"));
        return profile;
    }

    private Message mapToMessage(ResultSet rs) throws SQLException {
        Message msg = new Message();
        msg.setId(rs.getLong("id"));
        msg.setSessionId(rs.getString("session_id"));
        msg.setSenderId(rs.getLong("sender_id"));
        msg.setSenderType(rs.getInt("sender_type"));
        msg.setTargetId(rs.getLong("target_id"));
        msg.setTargetType(rs.getInt("target_type"));
        msg.setType(rs.getInt("type"));
        msg.setContent(rs.getString("content"));
        msg.setExtra(rs.getString("extra"));
        Timestamp createTime = rs.getTimestamp("create_time");
        if (createTime != null) {
            msg.setCreateTime(createTime.toLocalDateTime());
        }
        Timestamp recallTime = rs.getTimestamp("recall_time");
        if (recallTime != null) {
            msg.setRecallTime(recallTime.toLocalDateTime());
        }
        msg.setIsDeleted(rs.getInt("is_deleted"));
        return msg;
    }

    private AiMemory mapToAiMemory(ResultSet rs) throws SQLException {
        AiMemory memory = new AiMemory();
        memory.setId(rs.getLong("id"));
        memory.setAiId(rs.getLong("ai_id"));
        memory.setUserId(rs.getLong("user_id"));
        memory.setSessionId(rs.getString("session_id"));
        memory.setSummary(rs.getString("summary"));
        memory.setKeywords(rs.getString("keywords"));
        memory.setImportance(rs.getDouble("importance"));
        memory.setVectorId(rs.getString("vector_id"));
        memory.setIsDeleted(rs.getInt("is_deleted"));
        return memory;
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                LOG.info("H2 数据库连接已关闭");
            }
        } catch (SQLException e) {
            LOG.warn("关闭H2连接失败", e);
        }
    }

    /**
     * 获取底层 JDBC 连接（供 JdbcUserRepository 共享使用）
     */
    public Connection getConnection() {
        return connection;
    }
}
