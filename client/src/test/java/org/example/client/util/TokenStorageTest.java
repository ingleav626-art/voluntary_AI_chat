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
import java.util.Base64;

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
        // 写入只有一部分的数据（Base64编码后少于2个分隔部分）
        String badData = Base64.getEncoder().encodeToString("only-one-part".getBytes());
        Path tokenFile = tempDir.resolve("token.dat");
        Files.writeString(tokenFile, badData);

        assertNull(TokenStorage.load());
    }

}
