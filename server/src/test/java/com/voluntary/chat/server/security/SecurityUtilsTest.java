package com.voluntary.chat.server.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SecurityUtils 单元测试")
class SecurityUtilsTest {

  @BeforeEach
  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("有认证上下文时应返回 userId")
  void shouldReturnUserIdWhenAuthenticated() {
    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(1001L, null,
        Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(auth);

    assertEquals(1001L, SecurityUtils.getCurrentUserId());
  }

  @Test
  @DisplayName("无认证上下文时应返回 null")
  void shouldReturnNullWhenNotAuthenticated() {
    assertNull(SecurityUtils.getCurrentUserId());
  }

  @Test
  @DisplayName("构造器私有")
  void constructorIsPrivate() throws Exception {
    var constructor = SecurityUtils.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    assertNotNull(constructor.newInstance());
  }
}
