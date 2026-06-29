package org.example.client.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * 本地用户数据访问层（JDBC 直连 H2，密码 PBKDF2 哈希）
 *
 * <p>
 * 用于客户包 LOCAL 模式下云端不可用时的本地登录/注册兜底。
 * 密码使用 PBKDF2WithHmacSHA256 哈希存储，不存明文。
 * </p>
 */
public class JdbcUserRepository implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcUserRepository.class);

    private final Connection connection;

    /** PBKDF2 迭代次数 */
    private static final int PBKDF2_ITERATIONS = 65536;

    /** 派生密钥长度 */
    private static final int KEY_LENGTH = 256;

    /** Salt 长度（字节） */
    private static final int SALT_LENGTH = 16;

    public JdbcUserRepository(Connection connection) {
        this.connection = connection;
        initSchema();
    }

    private void initSchema() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS local_user (\n" +
                    "    id BIGINT NOT NULL,\n" +
                    "    phone VARCHAR(20) NOT NULL,\n" +
                    "    username VARCHAR(50) NOT NULL,\n" +
                    "    password_hash VARCHAR(200) NOT NULL,\n" +
                    "    password_salt VARCHAR(50) NOT NULL,\n" +
                    "    avatar VARCHAR(500) DEFAULT NULL,\n" +
                    "    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n" +
                    "    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n" +
                    "    PRIMARY KEY (id),\n" +
                    "    UNIQUE (phone),\n" +
                    "    UNIQUE (username)\n" +
                    ")");
            LOG.debug("local_user 表已就绪");
        } catch (SQLException e) {
            LOG.warn("创建 local_user 表失败: {}", e.getMessage());
        }
    }

    /**
     * 本地登录验证
     *
     * @param phone    手机号
     * @param password 明文密码
     * @return 用户信息（含 userId），验证失败返回 null
     */
    public LocalUser login(String phone, String password) {
        String sql = "SELECT * FROM local_user WHERE phone = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    String salt = rs.getString("password_salt");
                    if (verifyPassword(password, salt, storedHash)) {
                        LocalUser user = new LocalUser();
                        user.setId(rs.getLong("id"));
                        user.setPhone(rs.getString("phone"));
                        user.setUsername(rs.getString("username"));
                        user.setAvatar(rs.getString("avatar"));
                        return user;
                    }
                }
            }
        } catch (SQLException e) {
            LOG.error("本地登录查询失败", e);
        }
        return null;
    }

    /**
     * 本地注册
     *
     * @param phone    手机号
     * @param username 用户名
     * @param password 明文密码
     * @return 注册成功的用户 ID，失败返回 null
     */
    public Long register(String phone, String username, String password) {
        // 检查是否已存在
        String checkSql = "SELECT COUNT(*) FROM local_user WHERE phone = ? OR username = ?";
        try (PreparedStatement checkPs = connection.prepareStatement(checkSql)) {
            checkPs.setString(1, phone);
            checkPs.setString(2, username);
            try (ResultSet rs = checkPs.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    LOG.warn("本地注册失败：手机号或用户名已存在: phone={}, username={}", phone, username);
                    return null;
                }
            }
        } catch (SQLException e) {
            LOG.error("本地注册检查失败", e);
            return null;
        }

        // 生成盐值和哈希
        String salt = generateSalt();
        String hash = hashPassword(password, salt);

        // 插入用户
        long id = generateId();
        String insertSql = "INSERT INTO local_user (id, phone, username, password_hash, password_salt, " +
                "create_time, update_time) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
            ps.setLong(1, id);
            ps.setString(2, phone);
            ps.setString(3, username);
            ps.setString(4, hash);
            ps.setString(5, salt);
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
            LOG.info("本地注册成功: userId={}, phone={}, username={}", id, phone, username);
            return id;
        } catch (SQLException e) {
            LOG.error("本地注册插入失败", e);
            return null;
        }
    }

    /**
     * 检查本地是否有此用户
     */
    public boolean userExists(String phone) {
        String sql = "SELECT COUNT(*) FROM local_user WHERE phone = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            LOG.error("检查用户是否存在失败", e);
            return false;
        }
    }

    // ========== 密码工具 ==========

    /**
     * 生成随机盐值（Base64 编码）
     */
    private String generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * PBKDF2 密码哈希
     */
    private String hashPassword(String password, String salt) {
        try {
            byte[] saltBytes = Base64.getDecoder().decode(salt);
            KeySpec spec = new PBEKeySpec(password.toCharArray(), saltBytes, PBKDF2_ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("密码哈希失败", e);
        }
    }

    /**
     * 验证密码
     */
    private boolean verifyPassword(String password, String salt, String storedHash) {
        String computedHash = hashPassword(password, salt);
        return computedHash.equals(storedHash);
    }

    /**
     * 简化 ID 生成
     */
    private long generateId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    @Override
    public void close() {
        // 连接由 JdbcAiProfileRepository 管理，这里不关闭
    }

    // ========== 内部模型 ==========

    /**
     * 本地用户信息
     */
    public static class LocalUser {
        private Long id;
        private String phone;
        private String username;
        private String avatar;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getAvatar() { return avatar; }
        public void setAvatar(String avatar) { this.avatar = avatar; }
    }
}
