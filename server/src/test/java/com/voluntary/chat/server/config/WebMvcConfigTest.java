package com.voluntary.chat.server.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebMvcConfig 测试")
class WebMvcConfigTest {

    @Mock
    private ResourceHandlerRegistry registry;

    @Mock
    private ResourceHandlerRegistration registration;

    private WebMvcConfig config;

    @BeforeEach
    void setUp() {
        config = new WebMvcConfig();
        // 手动注入 @Value 字段（单元测试无 Spring 上下文）
        ReflectionTestUtils.setField(config, "uploadDir", "uploads/chat/images");
    }

    @Test
    @DisplayName("addResourceHandlers 正确映射文件路径")
    void addResourceHandlers_shouldMapFileUrl() {
        when(registry.addResourceHandler("/files/**")).thenReturn(registration);
        when(registration.addResourceLocations(anyString())).thenReturn(registration);

        config.addResourceHandlers(registry);

        verify(registry).addResourceHandler("/files/**");
        verify(registration).addResourceLocations("file:uploads/chat/images/");
    }
}
