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

    /**
     * Token 存储目录，支持通过系统属性 {@code app.token.dir} 覆盖，默认为 {@code ~/.ai-chat}。
     * 测试时可通过 {@code -Dapp.token.dir=...} 指定临时目录，避免污染用户主目录。
     */
    private static Path getTokenFile() {
        return Paths.get(
                System.getProperty("app.token.dir",
                        Paths.get(System.getProperty("user.home"), ".ai-chat").toString()),
                "token.dat");
    }

    /** Token 数据分隔符数量 */
    private static final int TOKEN_PARTS_COUNT = 2;

    /** 内存缓存的 Token，即使不勾选"记住我"也会保存，保证当前会话可用 */
    private static LoginResponse cachedToken;

    private TokenStorage() {
        // 工具类禁止实例化
    }

    /**
     * 保存 Token 到内存，并可选持久化到文件
     *
     * @param response 登录响应
     */
    public static void save(final LoginResponse response) {
        save(response, false);
    }

    /**
     * 保存 Token 到内存，并可选持久化到文件
     *
     * @param response 登录响应
     * @param persist  是否持久化到文件（勾选"记住我"时为 true）
     */
    public static void save(final LoginResponse response, final boolean persist) {
        Objects.requireNonNull(response, "response 不能为 null");

        // 总是缓存到内存，保证当前会话可用
        cachedToken = response;

        if (!persist) {
            LOG.debug("Token 已保存到内存（不持久化）");
            return;
        }

        final Path tokenFile = getTokenFile();
        try {
            Files.createDirectories(tokenFile.getParent());

            final String data = response.getAccessToken() + "|" + response.getRefreshToken();
            final String encrypted = Base64.getEncoder().encodeToString(data.getBytes());

            Files.writeString(tokenFile, encrypted);
            LOG.debug("Token 已保存到 {}", tokenFile);
        } catch (final IOException e) {
            LOG.error("保存 Token 失败", e);
        }
    }

    /**
     * 加载 Token
     *
     * <p>优先从内存缓存加载，不存在时再从文件加载。</p>
     *
     * @return 登录响应，不存在时返回 null
     */
    public static LoginResponse load() {
        // 优先从内存缓存加载
        if (cachedToken != null) {
            return cachedToken;
        }

        final Path tokenFile = getTokenFile();
        if (!Files.exists(tokenFile)) {
            LOG.debug("Token 文件不存在");
            return null;
        }

        try {
            final String encrypted = Files.readString(tokenFile);
            final String data = new String(Base64.getDecoder().decode(encrypted));

            final String[] parts = data.split("\\|");
            if (parts.length != TOKEN_PARTS_COUNT) {
                LOG.warn("Token 文件格式错误");
                return null;
            }

            final LoginResponse response = new LoginResponse();
            response.setAccessToken(parts[0]);
            response.setRefreshToken(parts[1]);
            // 缓存到内存，避免重复读取文件
            cachedToken = response;
            return response;
        } catch (final IOException e) {
            LOG.error("加载 Token 失败", e);
            return null;
        }
    }

    /**
     * 清除 Token（内存和文件）
     */
    public static void clear() {
        // 清除内存缓存
        cachedToken = null;

        final Path tokenFile = getTokenFile();
        try {
            Files.deleteIfExists(tokenFile);
            LOG.debug("Token 已清除");
        } catch (final IOException e) {
            LOG.error("清除 Token 失败", e);
        }
    }
}

