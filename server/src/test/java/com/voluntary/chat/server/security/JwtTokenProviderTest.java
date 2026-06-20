package com.voluntary.chat.server.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtTokenProvider 单元测试")
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    private static final String TEST_SECRET = "dGVzdC1zZWNyZXQtZm9yLXRlc3Rpbmctb25seS1rZXktMTIzNDU2Nzg5MA==";
    private static final long ACCESS_TOKEN_EXPIRATION = 7200000L;
    private static final long REFRESH_TOKEN_EXPIRATION = 604800000L;
    private static final Long TEST_USER_ID = 1001L;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(TEST_SECRET, ACCESS_TOKEN_EXPIRATION, REFRESH_TOKEN_EXPIRATION);
    }

    @Test
    @DisplayName("生成Access Token成功")
    void generateAccessToken_shouldReturnNonNullToken() {
        String token = jwtTokenProvider.generateAccessToken(TEST_USER_ID);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    @DisplayName("生成Refresh Token成功")
    void generateRefreshToken_shouldReturnNonNullToken() {
        String token = jwtTokenProvider.generateRefreshToken(TEST_USER_ID);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    @DisplayName("Access Token验证通过")
    void validateToken_withValidAccessToken_shouldReturnTrue() {
        String token = jwtTokenProvider.generateAccessToken(TEST_USER_ID);
        assertTrue(jwtTokenProvider.validateToken(token));
    }

    @Test
    @DisplayName("Refresh Token验证通过")
    void validateToken_withValidRefreshToken_shouldReturnTrue() {
        String token = jwtTokenProvider.generateRefreshToken(TEST_USER_ID);
        assertTrue(jwtTokenProvider.validateToken(token));
    }

    @Test
    @DisplayName("无效Token验证失败")
    void validateToken_withInvalidToken_shouldReturnFalse() {
        assertFalse(jwtTokenProvider.validateToken("invalid.token.here"));
    }

    @Test
    @DisplayName("空Token验证失败")
    void validateToken_withEmptyToken_shouldReturnFalse() {
        assertFalse(jwtTokenProvider.validateToken(""));
    }

    @Test
    @DisplayName("从Access Token提取UserId")
    void getUserIdFromToken_withAccessToken_shouldReturnCorrectUserId() {
        String token = jwtTokenProvider.generateAccessToken(TEST_USER_ID);
        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        assertEquals(TEST_USER_ID, userId);
    }

    @Test
    @DisplayName("从Refresh Token提取UserId")
    void getUserIdFromToken_withRefreshToken_shouldReturnCorrectUserId() {
        String token = jwtTokenProvider.generateRefreshToken(TEST_USER_ID);
        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        assertEquals(TEST_USER_ID, userId);
    }

    @Test
    @DisplayName("Access Token不是Refresh Token")
    void isRefreshToken_withAccessToken_shouldReturnFalse() {
        String token = jwtTokenProvider.generateAccessToken(TEST_USER_ID);
        assertFalse(jwtTokenProvider.isRefreshToken(token));
    }

    @Test
    @DisplayName("Refresh Token是Refresh Token")
    void isRefreshToken_withRefreshToken_shouldReturnTrue() {
        String token = jwtTokenProvider.generateRefreshToken(TEST_USER_ID);
        assertTrue(jwtTokenProvider.isRefreshToken(token));
    }

    @Test
    @DisplayName("不同Secret生成的Token验证失败")
    void validateToken_withDifferentSecret_shouldReturnFalse() {
        JwtTokenProvider otherProvider = new JwtTokenProvider(
                "b3RoZXItc2VjcmV0LWZvci10ZXN0aW5nLW9ubHkta2V5LTEyMzQ1Njc4OTA=", ACCESS_TOKEN_EXPIRATION, REFRESH_TOKEN_EXPIRATION);
        String token = jwtTokenProvider.generateAccessToken(TEST_USER_ID);
        assertFalse(otherProvider.validateToken(token));
    }
}
