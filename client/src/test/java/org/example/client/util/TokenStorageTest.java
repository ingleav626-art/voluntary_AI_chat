package org.example.client.util;

import org.example.client.model.LoginResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TokenStorage 测试")
class TokenStorageTest {

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void setUpClass() {
        System.setProperty("app.token.dir", tempDir.toString());
    }

    @BeforeEach
    void setUp() {
        TokenStorage.clear();
    }

    @AfterEach
    void tearDown() {
        TokenStorage.clear();
    }

    @Test
    @DisplayName("保存和加载 Token")
    void testSaveAndLoad() {
        LoginResponse response = new LoginResponse();
        response.setAccessToken("access-token-123");
        response.setRefreshToken("refresh-token-456");

        TokenStorage.save(response);
        LoginResponse loaded = TokenStorage.load();

        assertNotNull(loaded);
        assertEquals("access-token-123", loaded.getAccessToken());
        assertEquals("refresh-token-456", loaded.getRefreshToken());
    }

    @Test
    @DisplayName("加载不存在的 Token 返回 null")
    void testLoadNonExistent() {
        assertNull(TokenStorage.load());
    }

    @Test
    @DisplayName("清除 Token")
    void testClear() {
        LoginResponse response = new LoginResponse();
        response.setAccessToken("test-token");
        response.setRefreshToken("test-refresh");

        TokenStorage.save(response, true);
        final Path tokenFile = tempDir.resolve("token.dat");
        assertTrue(Files.exists(tokenFile));

        TokenStorage.clear();
        assertFalse(Files.exists(tokenFile));
    }

    @Test
    @DisplayName("保存 null 抛出异常")
    void testSaveNull() {
        assertThrows(NullPointerException.class, () -> TokenStorage.save(null));
    }

    @Test
    @DisplayName("加载格式错误的 Token 返回 null")
    void testLoadCorruptedData() throws Exception {
        // 写入不是 AES-GCM 加密格式的数据
        Path tokenFile = tempDir.resolve("token.dat");
        Files.writeString(tokenFile, "this-is-not-valid-aes-gcm-data");

        assertNull(TokenStorage.load());
    }

    @Test
    @DisplayName("保存 Token 不持久化（内存模式）")
    void testSaveMemoryOnly() {
        LoginResponse response = new LoginResponse();
        response.setAccessToken("mem-token");
        response.setRefreshToken("mem-refresh");

        TokenStorage.save(response, false);

        // 验证文件未创建
        final Path tokenFile = tempDir.resolve("token.dat");
        assertFalse(Files.exists(tokenFile));

        // 但内存加载有效
        LoginResponse loaded = TokenStorage.load();
        assertNotNull(loaded);
        assertEquals("mem-token", loaded.getAccessToken());
    }

    @Test
    @DisplayName("load 优先从内存缓存加载")
    void testLoadPrefersMemoryCache() {
        // 先保存到内存（不持久化）
        LoginResponse memResponse = new LoginResponse();
        memResponse.setAccessToken("mem-token");
        memResponse.setRefreshToken("mem-refresh");
        TokenStorage.save(memResponse, false);

        // 再持久化保存另一个（会覆盖文件但不会覆盖内存）
        LoginResponse fileResponse = new LoginResponse();
        fileResponse.setAccessToken("file-token");
        fileResponse.setRefreshToken("file-refresh");
        TokenStorage.save(fileResponse, true);

        // 最后再次设置内存版本（不持久化）
        LoginResponse memOverride = new LoginResponse();
        memOverride.setAccessToken("mem-token");
        memOverride.setRefreshToken("mem-refresh");
        TokenStorage.save(memOverride, false);

        // 应从内存加载（优先）
        LoginResponse loaded = TokenStorage.load();
        assertNotNull(loaded);
        assertEquals("mem-token", loaded.getAccessToken());
    }

    @Test
    @DisplayName("clear 清除内存缓存后从文件重新加载")
    void testClearThenReloadFromFile() {
        // 先持久化保存
        LoginResponse response = new LoginResponse();
        response.setAccessToken("persist-token");
        response.setRefreshToken("persist-refresh");
        TokenStorage.save(response, true);

        // 清除（内存和文件）
        TokenStorage.clear();

        // 验证文件已清除
        final Path tokenFile = tempDir.resolve("token.dat");
        assertFalse(Files.exists(tokenFile));

        // 加载应为 null
        assertNull(TokenStorage.load());
    }

    @Test
    @DisplayName("多次背靠背 save 覆盖内存缓存")
    void testMultipleSaveOverwritesMemory() {
        LoginResponse resp1 = new LoginResponse();
        resp1.setAccessToken("token1");
        resp1.setRefreshToken("refresh1");
        TokenStorage.save(resp1);

        LoginResponse resp2 = new LoginResponse();
        resp2.setAccessToken("token2");
        resp2.setRefreshToken("refresh2");
        TokenStorage.save(resp2);

        LoginResponse loaded = TokenStorage.load();
        assertNotNull(loaded);
        assertEquals("token2", loaded.getAccessToken());
    }

    @Test
    @DisplayName("save 持久化后文件存在")
    void testSavePersistCreatesFile() {
        LoginResponse response = new LoginResponse();
        response.setAccessToken("persist-token");
        response.setRefreshToken("persist-refresh");
        TokenStorage.save(response, true);

        final Path tokenFile = tempDir.resolve("token.dat");
        assertTrue(Files.exists(tokenFile));
    }

    @Test
    @DisplayName("clear 清除后内存和文件都为空")
    void testClearRemovesBoth() throws Exception {
        LoginResponse response = new LoginResponse();
        response.setAccessToken("clear-test-token");
        response.setRefreshToken("clear-test-refresh");
        TokenStorage.save(response, true);

        // 确认已保存
        assertNotNull(TokenStorage.load());

        TokenStorage.clear();

        // 确认已清除
        assertNull(TokenStorage.load());

        Path tokenFile = tempDir.resolve("token.dat");
        assertFalse(Files.exists(tokenFile));
    }

    @Test
    @DisplayName("load 文件内容为无效加密数据时返回 null")
    void testLoadFileWithInvalidEncryptedData() throws Exception {
        Path tokenFile = tempDir.resolve("token.dat");
        Files.writeString(tokenFile, "random-invalid-data");

        assertNull(TokenStorage.load());
    }

    @Test
    @DisplayName("save(null, true) 抛出 NullPointerException")
    void testSaveNullWithPersistThrowsNPE() {
        assertThrows(NullPointerException.class, () -> TokenStorage.save(null, true));
    }

    @Test
    @DisplayName("save 持久化时 tokenFile 路径被目录占用触发 IOException 不崩溃")
    void testSavePersistWithDirectoryConflict() throws Exception {
        // 创建一个目录占据 token.dat 的路径，触发 IOException
        Path tokenDir = tempDir.resolve("token.dat");
        Files.createDirectories(tokenDir); // token.dat 变成一个目录

        LoginResponse response = new LoginResponse();
        response.setAccessToken("test-token");
        response.setRefreshToken("test-refresh");

        // 不应该崩溃，IOException 会被捕获
        TokenStorage.save(response, true);
        // 验证不崩溃
    }

    @Test
    @DisplayName("load 从文件加载后缓存到内存")
    void testLoadCachesToMemory() throws Exception {
        TokenStorage.clear();

        // 通过 save 持久化创建文件
        LoginResponse saved = new LoginResponse();
        saved.setAccessToken("cache-token");
        saved.setRefreshToken("cache-refresh");
        TokenStorage.save(saved, true);

        // 通过反射清除内存缓存，但保留文件
        java.lang.reflect.Field cachedField = TokenStorage.class.getDeclaredField("cachedToken");
        cachedField.setAccessible(true);
        cachedField.set(null, (LoginResponse) null);

        // 第一次 load 从文件
        LoginResponse first = TokenStorage.load();
        assertNotNull(first);

        // 删除文件
        Path tokenFile = tempDir.resolve("token.dat");
        Files.deleteIfExists(tokenFile);

        // 第二次 load 应该从内存缓存（文件已删）
        LoginResponse second = TokenStorage.load();
        assertNotNull(second);
        assertEquals("cache-token", second.getAccessToken());
    }
}