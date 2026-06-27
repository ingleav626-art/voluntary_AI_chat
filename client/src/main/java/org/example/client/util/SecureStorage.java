package org.example.client.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 安全存储工具类（AES-256-GCM 加密本地持久化数据）
 *
 * <p>
 * 用于加密存储凭证（密码）和 Token，替代原始的 Base64 编码。
 * 加密密钥自动生成并保存在 {@code ~/.ai-chat/.storage-key}。
 * </p>
 */
public final class SecureStorage {

  private static final Logger LOG = LoggerFactory.getLogger(SecureStorage.class);

  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int GCM_IV_LENGTH = 12;
  private static final int GCM_TAG_LENGTH = 128;
  private static final int KEY_LENGTH = 32; // 256 bits

  /** 密钥文件路径 */
  private static Path getKeyFile() {
    return Paths.get(
        System.getProperty("app.token.dir",
            Paths.get(System.getProperty("user.home"), ".ai-chat").toString()),
        ".storage-key");
  }

  private SecureStorage() {
    // 工具类禁止实例化
  }

  /**
   * 获取或创建加密密钥（自动生成并持久化）
   */
  private static byte[] loadOrCreateKey() {
    final Path keyFile = getKeyFile();
    try {
      if (Files.exists(keyFile)) {
        final byte[] key = Files.readAllBytes(keyFile);
        if (key.length == KEY_LENGTH) {
          return key;
        }
        LOG.warn("密钥文件长度异常，重新生成: expected={}, actual={}", KEY_LENGTH, key.length);
      }

      // 生成新密钥
      final byte[] newKey = new byte[KEY_LENGTH];
      final SecureRandom random = new SecureRandom();
      random.nextBytes(newKey);

      Files.createDirectories(keyFile.getParent());
      Files.write(keyFile, newKey);
      LOG.info("已生成新的加密密钥: {}", keyFile);
      return newKey;
    } catch (final IOException e) {
      LOG.error("读取/生成密钥失败，使用临时密钥", e);
      // 降级：生成内存中临时密钥（重启后失效）
      final byte[] fallback = new byte[KEY_LENGTH];
      new SecureRandom().nextBytes(fallback);
      return fallback;
    }
  }

  /**
   * 加密数据
   *
   * @param plainText 明文
   * @return Base64 编码的密文
   */
  public static String encrypt(final String plainText) {
    if (plainText == null) {
      return null;
    }

    final byte[] keyBytes = loadOrCreateKey();
    try {
      // 生成随机 IV
      final byte[] iv = new byte[GCM_IV_LENGTH];
      final SecureRandom random = new SecureRandom();
      random.nextBytes(iv);

      // 初始化加密器
      final SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
      final GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
      final Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

      // 加密
      final byte[] cipherText = cipher.doFinal(plainText.getBytes());

      // 组合 IV + 密文
      final byte[] combined = new byte[iv.length + cipherText.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

      return Base64.getEncoder().encodeToString(combined);
    } catch (final GeneralSecurityException e) {
      LOG.error("加密失败", e);
      throw new RuntimeException("加密失败", e);
    }
  }

  /**
   * 解密数据
   *
   * @param encryptedText Base64 编码的密文（IV + 密文）
   * @return 明文，解密失败返回 null
   */
  public static String decrypt(final String encryptedText) {
    if (encryptedText == null || encryptedText.isEmpty()) {
      return null;
    }

    final byte[] keyBytes = loadOrCreateKey();
    try {
      // Base64 解码
      final byte[] combined = Base64.getDecoder().decode(encryptedText);
      if (combined.length < GCM_IV_LENGTH) {
        LOG.warn("密文长度异常，无法解密");
        return null;
      }

      // 分离 IV 和密文
      final byte[] iv = new byte[GCM_IV_LENGTH];
      final byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
      System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
      System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

      // 初始化解密器
      final SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
      final GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
      final Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

      final byte[] plainText = cipher.doFinal(cipherText);
      return new String(plainText);
    } catch (final Exception e) {
      LOG.error("解密失败，数据可能已损坏", e);
      return null;
    }
  }

  /**
   * 清除密钥文件（同时清除所有已加密的数据）
   */
  public static void clearKey() {
    final Path keyFile = getKeyFile();
    try {
      Files.deleteIfExists(keyFile);
      LOG.info("加密密钥已清除");
    } catch (final IOException e) {
      LOG.error("清除密钥失败", e);
    }
  }
}
