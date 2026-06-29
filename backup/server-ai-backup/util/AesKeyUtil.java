package com.voluntary.chat.server.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加密工具类
 * 用于 API Key 的加密和解密
 */
@Slf4j
public final class AesKeyUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int KEY_LENGTH = 32;

    private AesKeyUtil() {
        // 私有构造函数，防止实例化
    }

    /**
     * 加密 API Key
     *
     * @param plainText 明文
     * @param key       加密密钥（32字节）
     * @return Base64 编码的加密结果（IV + 密文）
     */
    public static String encrypt(final String plainText, final String key) {
        if (plainText == null || key == null) {
            throw new IllegalArgumentException("明文和密钥不能为空");
        }

        final byte[] keyBytes = getKeyBytes(key);
        // getKeyBytes 始终返回 32 字节，不需要额外检查

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
            final byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // 组合 IV + 密文
            final byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            // Base64 编码
            return Base64.getEncoder().encodeToString(combined);
        } catch (final GeneralSecurityException e) {
            log.error("API Key 加密失败", e);
            throw new RuntimeException("API Key 加密失败", e);
        }
    }

    /**
     * 解密 API Key
     *
     * @param encryptedText Base64 编码的加密结果（IV + 密文）
     * @param key           加密密钥（32字节）
     * @return 明文
     */
    public static String decrypt(final String encryptedText, final String key) {
        if (encryptedText == null || key == null) {
            throw new IllegalArgumentException("加密文本和密钥不能为空");
        }

        final byte[] keyBytes = getKeyBytes(key);
        // getKeyBytes 始终返回 32 字节，不需要额外检查

        try {
            // Base64 解码
            final byte[] combined = Base64.getDecoder().decode(encryptedText);

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

            // 解密
            final byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (final GeneralSecurityException e) {
            log.error("API Key 解密失败", e);
            throw new RuntimeException("API Key 解密失败", e);
        }
    }

    /**
     * 将密钥字符串转换为 32 字节数组
     * 如果密钥长度不足，用 0 补齐；如果超过，截取前 32 字节
     */
    private static byte[] getKeyBytes(final String key) {
        final byte[] keyBytes = new byte[KEY_LENGTH];
        final byte[] inputBytes = key.getBytes(StandardCharsets.UTF_8);
        final int length = Math.min(inputBytes.length, KEY_LENGTH);
        System.arraycopy(inputBytes, 0, keyBytes, 0, length);
        return keyBytes;
    }
}