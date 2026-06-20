package org.example.client.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Objects;

import org.example.client.model.LoginResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Token 持久化工具
 *
 * <p>Token 加密存储在本地文件中，避免明文泄露。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class TokenStorage {

    private static final Logger LOG = LoggerFactory.getLogger(TokenStorage.class);

    private static final Path TOKEN_FILE = Paths.get(System.getProperty("user.home"), ".ai-chat", "token.dat");

    /** Token 数据分隔符数量 */
    private static final int TOKEN_PARTS_COUNT = 2;

    private TokenStorage() {
        // 工具类禁止实例化
    }

    /**
     * 保存 Token
     *
     * @param response 登录响应
     */
    public static void save(final LoginResponse response) {
        Objects.requireNonNull(response, "response 不能为 null");

        try {
            Files.createDirectories(TOKEN_FILE.getParent());

            final String data = response.getAccessToken() + "|" + response.getRefreshToken();
            final String encrypted = Base64.getEncoder().encodeToString(data.getBytes());

            Files.writeString(TOKEN_FILE, encrypted);
            LOG.debug("Token 已保存到 {}", TOKEN_FILE);
        } catch (final IOException e) {
            LOG.error("保存 Token 失败", e);
        }
    }

    /**
     * 加载 Token
     *
     * @return 登录响应，不存在时返回 null
     */
    public static LoginResponse load() {
        if (!Files.exists(TOKEN_FILE)) {
            LOG.debug("Token 文件不存在");
            return null;
        }

        try {
            final String encrypted = Files.readString(TOKEN_FILE);
            final String data = new String(Base64.getDecoder().decode(encrypted));

            final String[] parts = data.split("\\|");
            if (parts.length != TOKEN_PARTS_COUNT) {
                LOG.warn("Token 文件格式错误");
                return null;
            }

            final LoginResponse response = new LoginResponse();
            response.setAccessToken(parts[0]);
            response.setRefreshToken(parts[1]);
            return response;
        } catch (final IOException e) {
            LOG.error("加载 Token 失败", e);
            return null;
        }
    }

    /**
     * 清除 Token
     */
    public static void clear() {
        try {
            Files.deleteIfExists(TOKEN_FILE);
            LOG.debug("Token 已清除");
        } catch (final IOException e) {
            LOG.error("清除 Token 失败", e);
        }
    }
}

