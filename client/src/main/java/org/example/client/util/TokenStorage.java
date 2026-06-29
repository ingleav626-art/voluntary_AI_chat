package org.example.client.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import org.example.client.model.LoginResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Token 持久化工具（AES-256-GCM 加密）
 *
 * <p>
 * LoginResponse（包括 accessToken、refreshToken、用户信息）加密存储在本地文件中。
 * </p>
 *
 * <p>
 * 加密方式：AES-256-GCM，密钥自动生成并存储在 {@code ~/.ai-chat/.storage-key}。
 * </p>
 */
public final class TokenStorage {

    private static final Logger LOG = LoggerFactory.getLogger(TokenStorage.class);

    /** Token 存储目录 */
    private static Path getTokenFile() {
        return Paths.get(
                System.getProperty("app.token.dir",
                        Paths.get(System.getProperty("user.home"), ".ai-chat").toString()),
                "token.dat");
    }

    /** 内存缓存的 Token，保证当前会话可用 */
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

            // 序列化为 JSON
            final String data = JsonUtils.toJson(response);
            if (data == null) {
                LOG.error("序列化 LoginResponse 失败");
                return;
            }

            // AES-256-GCM 加密
            final String encrypted = SecureStorage.encrypt(data);
            if (encrypted == null) {
                LOG.error("加密 Token 失败");
                return;
            }

            Files.writeString(tokenFile, encrypted);
            LOG.debug("Token 已加密保存到 {}", tokenFile);
        } catch (final IOException e) {
            LOG.error("保存 Token 失败", e);
        }
    }

    /**
     * 加载 Token
     *
     * <p>
     * 优先从内存缓存加载，不存在时从文件加载并解密。
     * </p>
     *
     * @return 登录响应，不存在或解密失败时返回 null
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

            // AES-256-GCM 解密
            final String data = SecureStorage.decrypt(encrypted);
            if (data == null) {
                LOG.warn("Token 文件解密失败，数据可能已损坏");
                return null;
            }

            // 解析 LoginResponse
            final LoginResponse response = JsonUtils.fromJson(data, LoginResponse.class);
            if (response == null || response.getAccessToken() == null || response.getRefreshToken() == null) {
                LOG.warn("Token 文件格式错误");
                return null;
            }

            // 缓存到内存，避免重复读取文件
            cachedToken = response;
            return response;
        } catch (final Exception e) {
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