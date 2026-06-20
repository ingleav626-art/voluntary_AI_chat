package org.example.client.util;

import org.example.client.model.LoginResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TokenStorage 单元测试
 */
@DisplayName("TokenStorage 测试")
class TokenStorageTest {

    private static final Path TOKEN_FILE = Path.of(System.getProperty("user.home"), ".ai-chat", "token.dat");

    @BeforeEach
    void setUp() throws Exception {
        TokenStorage.clear();
    }

    @AfterEach
    void tearDown() throws Exception {
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

        TokenStorage.save(response);
        assertTrue(Files.exists(TOKEN_FILE));

        TokenStorage.clear();
        assertFalse(Files.exists(TOKEN_FILE));
    }

    @Test
    @DisplayName("保存 null 抛出异常")
    void testSaveNull() {
        assertThrows(NullPointerException.class, () -> TokenStorage.save(null));
    }
}