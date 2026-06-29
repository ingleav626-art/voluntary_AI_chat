package com.voluntary.chat.server.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AES 加密工具测试
 */
@DisplayName("AesKeyUtil 测试")
class AesKeyUtilTest {

    private static final String VALID_KEY = "default-key-for-dev-only-32b!";
    private static final String SHORT_KEY = "short-key";
    private static final String TEST_TEXT = "sk-test-api-key-1234567890";

    @Test
    @DisplayName("加密成功 - 有效密钥")
    void encrypt_shouldSucceed_withValidKey() {
        final String encrypted = AesKeyUtil.encrypt(TEST_TEXT, VALID_KEY);

        assertNotNull(encrypted);
        assertTrue(encrypted.length() > 0);
        assertNotEquals(TEST_TEXT, encrypted);
    }

    @Test
    @DisplayName("加密成功 - 短密钥自动补齐")
    void encrypt_shouldSucceed_withShortKey() {
        // 短密钥会被自动补齐到 32 字节
        final String encrypted = AesKeyUtil.encrypt(TEST_TEXT, SHORT_KEY);
        assertNotNull(encrypted);
        assertTrue(encrypted.length() > 0);
    }

    @Test
    @DisplayName("解密成功 - 加密后可解密")
    void decrypt_shouldSucceed_afterEncrypt() {
        final String encrypted = AesKeyUtil.encrypt(TEST_TEXT, VALID_KEY);
        final String decrypted = AesKeyUtil.decrypt(encrypted, VALID_KEY);

        assertEquals(TEST_TEXT, decrypted);
    }

    @Test
    @DisplayName("解密失败 - 密钥错误")
    void decrypt_shouldFail_withWrongKey() {
        final String encrypted = AesKeyUtil.encrypt(TEST_TEXT, VALID_KEY);
        final String wrongKey = "wrong-key-for-dev-only-32b!!";

        assertThrows(RuntimeException.class, () -> {
            AesKeyUtil.decrypt(encrypted, wrongKey);
        });
    }

    @Test
    @DisplayName("解密失败 - 无效密文")
    void decrypt_shouldFail_withInvalidCipherText() {
        assertThrows(RuntimeException.class, () -> {
            AesKeyUtil.decrypt("invalid-base64-text", VALID_KEY);
        });
    }

    @Test
    @DisplayName("加密空文本")
    void encrypt_shouldHandle_emptyText() {
        final String encrypted = AesKeyUtil.encrypt("", VALID_KEY);
        final String decrypted = AesKeyUtil.decrypt(encrypted, VALID_KEY);

        assertEquals("", decrypted);
    }

    @Test
    @DisplayName("多次加密结果不同（IV随机）")
    void encrypt_shouldProduce_differentResults() {
        final String encrypted1 = AesKeyUtil.encrypt(TEST_TEXT, VALID_KEY);
        final String encrypted2 = AesKeyUtil.encrypt(TEST_TEXT, VALID_KEY);

        // 由于IV随机，每次加密结果应该不同
        assertNotEquals(encrypted1, encrypted2);

        // 但都能正确解密
        assertEquals(TEST_TEXT, AesKeyUtil.decrypt(encrypted1, VALID_KEY));
        assertEquals(TEST_TEXT, AesKeyUtil.decrypt(encrypted2, VALID_KEY));
    }

    @Test
    @DisplayName("加密长文本")
    void encrypt_shouldHandle_longText() {
        final String longText = "sk-" + "a".repeat(1000);
        final String encrypted = AesKeyUtil.encrypt(longText, VALID_KEY);
        final String decrypted = AesKeyUtil.decrypt(encrypted, VALID_KEY);

        assertEquals(longText, decrypted);
    }

    @Test
    @DisplayName("加密失败 - 明文为 null")
    void encrypt_shouldFail_withNullPlainText() {
        assertThrows(IllegalArgumentException.class, () -> {
            AesKeyUtil.encrypt(null, VALID_KEY);
        });
    }

    @Test
    @DisplayName("加密失败 - 密钥为 null")
    void encrypt_shouldFail_withNullKey() {
        assertThrows(IllegalArgumentException.class, () -> {
            AesKeyUtil.encrypt(TEST_TEXT, null);
        });
    }

    @Test
    @DisplayName("解密失败 - 密文为 null")
    void decrypt_shouldFail_withNullEncryptedText() {
        assertThrows(IllegalArgumentException.class, () -> {
            AesKeyUtil.decrypt(null, VALID_KEY);
        });
    }

    @Test
    @DisplayName("解密失败 - 密钥为 null")
    void decrypt_shouldFail_withNullKey() {
        final String encrypted = AesKeyUtil.encrypt(TEST_TEXT, VALID_KEY);

        assertThrows(IllegalArgumentException.class, () -> {
            AesKeyUtil.decrypt(encrypted, null);
        });
    }

    @Test
    @DisplayName("解密失败 - 格式错误的密文")
    void decrypt_shouldFail_withMalformedCipherText() {
        // 不是有效的Base64格式
        assertThrows(RuntimeException.class, () -> {
            AesKeyUtil.decrypt("这不是有效的密文!!!", VALID_KEY);
        });
    }

    @Test
    @DisplayName("解密失败 - 密文长度不足")
    void decrypt_shouldFail_withTooShortCipherText() {
        // Base64编码但长度不足（少于IV长度12字节）
        final String shortEncrypted = Base64.getEncoder().encodeToString(new byte[5]);

        assertThrows(RuntimeException.class, () -> {
            AesKeyUtil.decrypt(shortEncrypted, VALID_KEY);
        });
    }

    @Test
    @DisplayName("加密解密 - Unicode字符")
    void encryptDecrypt_shouldHandle_unicodeCharacters() {
        final String unicodeText = "中文测试 🎉 emoji";
        final String encrypted = AesKeyUtil.encrypt(unicodeText, VALID_KEY);
        final String decrypted = AesKeyUtil.decrypt(encrypted, VALID_KEY);

        assertEquals(unicodeText, decrypted);
    }
}