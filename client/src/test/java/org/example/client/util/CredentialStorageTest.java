package org.example.client.util;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CredentialStorage 单元测试（AES-256-GCM 加密版）
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("CredentialStorage 测试")
class CredentialStorageTest {

    /** 临时测试目录，隔离密钥和凭证文件 */
    @TempDir
    static Path tempTestDir;

    private Path credentialFile;

    @BeforeAll
    static void setupClass() {
        // 用临时目录隔离，避免与用户本机 ~/.ai-chat 冲突
        System.setProperty("app.token.dir", tempTestDir.toAbsolutePath().toString());
    }

    @BeforeEach
    void setUp() throws Exception {
        // 获取凭证文件路径
        credentialFile = tempTestDir.resolve("credential.dat");
        // 确保测试前文件不存在
        Files.deleteIfExists(credentialFile);
        // 清理密钥文件，确保每次测试使用新密钥
        final Path keyFile = tempTestDir.resolve(".storage-key");
        Files.deleteIfExists(keyFile);
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(credentialFile);
    }

    // ======================== save 测试 ========================

    @Test
    @DisplayName("save - 正确保存凭证到文件")
    void save_shouldWriteCredentialFile() throws Exception {
        CredentialStorage.save("13800138000", "password123");
        assertTrue(Files.exists(credentialFile));

        // 文件内容是 AES-GCM 加密的，不可直接解码；验证 load 可以正常读取
        final String[] loaded = CredentialStorage.load();
        assertNotNull(loaded);
        assertEquals("13800138000", loaded[0]);
        assertEquals("password123", loaded[1]);
    }

    @Test
    @DisplayName("save - 创建父目录")
    void save_shouldCreateParentDirectories() throws Exception {
        CredentialStorage.save("13800138000", "password123");
        assertTrue(Files.exists(credentialFile.getParent()));
    }

    @Test
    @DisplayName("save - 覆盖已有文件")
    void save_shouldOverwriteExistingFile() throws Exception {
        CredentialStorage.save("13800138000", "oldpassword");
        CredentialStorage.save("13900139000", "newpassword");

        final String[] credentials = CredentialStorage.load();
        assertNotNull(credentials);
        assertEquals("13900139000", credentials[0]);
        assertEquals("newpassword", credentials[1]);
    }

    @Test
    @DisplayName("save - phone 为 null 时抛出 NullPointerException")
    void save_shouldThrowWhenPhoneIsNull() {
        assertThrows(NullPointerException.class, () -> CredentialStorage.save(null, "password"));
    }

    @Test
    @DisplayName("save - password 为 null 时抛出 NullPointerException")
    void save_shouldThrowWhenPasswordIsNull() {
        assertThrows(NullPointerException.class, () -> CredentialStorage.save("13800138000", null));
    }

    @Test
    @DisplayName("save - 空字符串手机号和密码可以保存")
    void save_shouldAllowEmptyStrings() {
        assertDoesNotThrow(() -> CredentialStorage.save("", ""));
        final String[] credentials = CredentialStorage.load();
        assertNotNull(credentials);
        assertEquals("", credentials[0]);
        assertEquals("", credentials[1]);
    }

    @Test
    @DisplayName("save - 特殊字符密码可以正确保存和加载")
    void save_shouldHandleSpecialCharacters() {
        final String specialPassword = "p@$$w0rd!#%^&*()_+-=[]{}|;':\",./<>?";
        CredentialStorage.save("13800138000", specialPassword);

        final String[] credentials = CredentialStorage.load();
        assertNotNull(credentials);
        assertEquals(specialPassword, credentials[1]);
    }

    @Test
    @DisplayName("save - Unicode 字符密码可以正确保存和加载")
    void save_shouldHandleUnicodeCharacters() {
        final String unicodePassword = "密码测试ABC";
        CredentialStorage.save("13800138000", unicodePassword);

        final String[] credentials = CredentialStorage.load();
        assertNotNull(credentials);
        assertEquals(unicodePassword, credentials[1]);
    }

    // ======================== load 测试 ========================

    @Test
    @DisplayName("load - 文件不存在时返回 null")
    void load_shouldReturnNullWhenFileNotExists() {
        final String[] result = CredentialStorage.load();
        assertNull(result);
    }

    @Test
    @DisplayName("load - 正确加载已保存的凭证")
    void load_shouldReturnSavedCredentials() {
        CredentialStorage.save("13800138000", "password123");

        final String[] result = CredentialStorage.load();
        assertNotNull(result);
        assertEquals(2, result.length);
        assertEquals("13800138000", result[0]);
        assertEquals("password123", result[1]);
    }

    @Test
    @DisplayName("load - 文件内容被篡改时返回 null")
    void load_shouldReturnNullWhenFileIsCorrupted() throws Exception {
        Files.createDirectories(credentialFile.getParent());
        Files.writeString(credentialFile, "this-is-not-valid-aes-gcm-data");

        final String[] result = CredentialStorage.load();
        assertNull(result);
    }

    @Test
    @DisplayName("load - 多次调用返回相同结果")
    void load_shouldReturnSameResultOnMultipleCalls() {
        CredentialStorage.save("13800138000", "password123");

        final String[] result1 = CredentialStorage.load();
        final String[] result2 = CredentialStorage.load();

        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals(result1[0], result2[0]);
        assertEquals(result1[1], result2[1]);
    }

    // ======================== clear 测试 ========================

    @Test
    @DisplayName("clear - 删除凭证文件")
    void clear_shouldDeleteCredentialFile() {
        CredentialStorage.save("13800138000", "password123");
        assertTrue(Files.exists(credentialFile));

        CredentialStorage.clear();
        assertFalse(Files.exists(credentialFile));
    }

    @Test
    @DisplayName("clear - 文件不存在时不抛异常")
    void clear_shouldNotThrowWhenFileNotExists() {
        assertFalse(Files.exists(credentialFile));
        assertDoesNotThrow(() -> CredentialStorage.clear());
    }

    @Test
    @DisplayName("clear - 清除后 load 返回 null")
    void clear_shouldMakeLoadReturnNull() {
        CredentialStorage.save("13800138000", "password123");
        assertNotNull(CredentialStorage.load());

        CredentialStorage.clear();
        assertNull(CredentialStorage.load());
    }

    // ======================== 集成场景测试 ========================

    @Test
    @DisplayName("完整流程 - 保存、加载、清除")
    void fullFlow_shouldWorkCorrectly() {
        // 保存
        CredentialStorage.save("13800138000", "mypassword");

        // 加载验证
        final String[] loaded = CredentialStorage.load();
        assertNotNull(loaded);
        assertEquals("13800138000", loaded[0]);
        assertEquals("mypassword", loaded[1]);

        // 清除
        CredentialStorage.clear();
        assertNull(CredentialStorage.load());
    }

    @Test
    @DisplayName("完整流程 - 更新凭证后加载最新值")
    void fullFlow_shouldLoadUpdatedCredentials() {
        CredentialStorage.save("13800138000", "oldpass");
        CredentialStorage.save("13900139000", "newpass");

        final String[] loaded = CredentialStorage.load();
        assertNotNull(loaded);
        assertEquals("13900139000", loaded[0]);
        assertEquals("newpass", loaded[1]);
    }

    @Test
    @DisplayName("save - 长密码可以正确保存和加载")
    void save_shouldHandleLongPassword() {
        final String longPassword = "a".repeat(1000);
        CredentialStorage.save("13800138000", longPassword);

        final String[] loaded = CredentialStorage.load();
        assertNotNull(loaded);
        assertEquals(longPassword, loaded[1]);
    }

    @Test
    @DisplayName("load - 密码中包含 | 字符时正确解析")
    void load_shouldHandleMultipleSeparators() {
        final String passwordWithPipe = "pass|word|123";
        CredentialStorage.save("13800138000", passwordWithPipe);

        final String[] loaded = CredentialStorage.load();
        assertNotNull(loaded);
        assertEquals("13800138000", loaded[0]);
        assertEquals(passwordWithPipe, loaded[1]);
    }
}
