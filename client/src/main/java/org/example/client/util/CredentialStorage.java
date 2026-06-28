package org.example.client.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 记住我 - 凭证持久化工具（AES-256-GCM 加密）
 *
 * <p>
 * 当用户勾选"记住我"登录后，将手机号和密码加密保存到本地文件，
 * 下次打开登录界面时自动预填表单。
 * </p>
 *
 * <p>
 * 加密方式：AES-256-GCM，密钥自动生成并存储在 {@code ~/.ai-chat/.storage-key}。
 * </p>
 */
public final class CredentialStorage {

    private static final Logger LOG = LoggerFactory.getLogger(CredentialStorage.class);

    /** 凭证文件路径 */
    private static Path getCredentialFile() {
        return Paths.get(
                System.getProperty("app.token.dir",
                        Paths.get(System.getProperty("user.home"), ".ai-chat").toString()),
                "credential.dat");
    }

    private CredentialStorage() {
        // 工具类禁止实例化
    }

    /**
     * 保存凭证（AES-256-GCM 加密）
     *
     * @param phone    手机号
     * @param password 密码
     */
    public static void save(final String phone, final String password) {
        Objects.requireNonNull(phone, "phone 不能为 null");
        Objects.requireNonNull(password, "password 不能为 null");

        final Path file = getCredentialFile();
        LOG.info("[记住我-保存] 保存凭证到文件: path={}", file.toAbsolutePath());
        try {
            Files.createDirectories(file.getParent());

            // 数据格式: "phone|password"
            final String data = phone + "|" + password;

            // AES-256-GCM 加密
            final String encrypted = SecureStorage.encrypt(data);
            if (encrypted == null) {
                LOG.error("[记住我-保存] 加密失败");
                return;
            }

            Files.writeString(file, encrypted);
            LOG.info("[记住我-保存] 凭证加密写入成功: path={}, size={}", file.toAbsolutePath(), encrypted.length());
        } catch (final IOException e) {
            LOG.error("[记住我-保存] 保存凭证失败: path={}", file.toAbsolutePath(), e);
        }
    }

    /**
     * 加载凭证（AES-256-GCM 解密）
     *
     * @return [手机号, 密码] 数组，不存在或解密失败时返回 null
     */
    public static String[] load() {
        final Path file = getCredentialFile();
        LOG.info("[记住我-加载] 尝试加载凭证: path={}, exists={}", file.toAbsolutePath(), Files.exists(file));
        if (!Files.exists(file)) {
            return null;
        }

        try {
            final String encrypted = Files.readString(file);
            LOG.info("[记住我-加载] 读取文件成功: size={}", encrypted.length());

            // AES-256-GCM 解密
            final String data = SecureStorage.decrypt(encrypted);
            if (data == null) {
                LOG.warn("[记住我-加载] 解密失败，凭证可能已损坏");
                return null;
            }

            final String[] parts = data.split("\\|", 2);
            if (parts.length != 2) {
                LOG.warn("[记住我-加载] 凭证文件格式错误: parts.length={}", parts.length);
                return null;
            }

            LOG.info("[记住我-加载] 凭证解析成功");
            return new String[]{parts[0], parts[1]};
        } catch (final Exception e) {
            LOG.error("[记住我-加载] 加载凭证失败", e);
            return null;
        }
    }

    /**
     * 清除凭证
     */
    public static void clear() {
        final Path file = getCredentialFile();
        try {
            Files.deleteIfExists(file);
            LOG.debug("凭证已清除");
        } catch (final IOException e) {
            LOG.error("清除凭证失败", e);
        }
    }
}