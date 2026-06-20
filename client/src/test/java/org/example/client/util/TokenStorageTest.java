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

/**
 * TokenStorage 单元测试
 */
@DisplayName("TokenStorage 测试")
class TokenStorageTest {

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void setUpClass() {
        // 指定临时目录作为 Token 存储路径，避免污染用户主目录
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
        final LoginResponse response = new LoginResponse();
        response.setAccessToken("access-token-123");
        response.setRefreshToken("refresh-token-456");

        TokenStorage.save(response);
        final LoginResponse loaded = TokenStorage.load();

        assertNotNull(loaded);
        assertEquals("access-token-123", loaded.getAccessToken());
        assertEquals("refresh-token-456", loaded.getRefreshToken());
    }

    @Test
    @DisplayName("加载不存在的 Token")
    void testLoadNonExistent() {
        final LoginResponse loaded = TokenStorage.load();
        assertNull(loaded);
    }

    @Test
    @DisplayName("清除 Token")
    void testClear() {
        final LoginResponse response = new LoginResponse();
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
}
