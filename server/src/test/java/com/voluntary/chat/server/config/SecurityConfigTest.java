package com.voluntary.chat.server.config;

import com.voluntary.chat.server.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DisplayName("SecurityConfig 单元测试")
class SecurityConfigTest {

    @Test
    @DisplayName("构造 SecurityConfig")
    void shouldInstantiate() {
        JwtAuthenticationFilter mockFilter = mock(JwtAuthenticationFilter.class);
        SecurityConfig config = new SecurityConfig(mockFilter);
        assertNotNull(config);
    }

    @Test
    @DisplayName("passwordEncoder 应返回 BCryptPasswordEncoder")
    void passwordEncoderShouldReturnBCrypt() {
        JwtAuthenticationFilter mockFilter = mock(JwtAuthenticationFilter.class);
        SecurityConfig config = new SecurityConfig(mockFilter);
        PasswordEncoder encoder = config.passwordEncoder();
        assertNotNull(encoder);
        assertTrue(encoder instanceof BCryptPasswordEncoder);
    }

    @Test
    @DisplayName("BCryptPasswordEncoder 应正确编码和验证")
    void encoderShouldEncodeAndMatch() {
        JwtAuthenticationFilter mockFilter = mock(JwtAuthenticationFilter.class);
        SecurityConfig config = new SecurityConfig(mockFilter);
        PasswordEncoder encoder = config.passwordEncoder();

        String raw = "testPassword123";
        String encoded = encoder.encode(raw);
        assertNotNull(encoded);
        assertTrue(encoded.startsWith("$2a$") || encoded.startsWith("$2b$"));
        assertTrue(encoder.matches(raw, encoded));
        assertFalse(encoder.matches("wrongPassword", encoded));
    }
}
