package com.voluntary.chat.server.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CorsConfig 单元测试")
class CorsConfigTest {

    @Test
    @DisplayName("非云端模式 - corsFilter 应允许所有来源")
    void corsFilter_shouldAllowAllOrigins_whenNotCloudMode() throws Exception {
        CorsConfig config = new CorsConfig();
        // 非云端模式：cloudModeEnabled = false
        ReflectionTestUtils.setField(config, "cloudModeEnabled", false);

        CorsFilter filter = config.corsFilter();
        assertNotNull(filter);

        // 验证CorsFilter内部配置
        CorsConfiguration corsConfig = extractCorsConfig(filter);
        assertNotNull(corsConfig);
        // 非云端模式使用 allowedOriginPatterns = ["*"]
        assertNotNull(corsConfig.getAllowedOriginPatterns());
        assertTrue(corsConfig.getAllowedOriginPatterns().contains("*"));
    }

    @Test
    @DisplayName("云端模式 - corsFilter 应允许配置的域名")
    void corsFilter_shouldAllowConfiguredOrigins_whenCloudModeWithOrigins() throws Exception {
        CorsConfig config = new CorsConfig();
        // 云端模式：cloudModeEnabled = true, allowedOrigins = "https://chat.example.com"
        ReflectionTestUtils.setField(config, "cloudModeEnabled", true);
        ReflectionTestUtils.setField(config, "allowedOrigins", "https://chat.example.com");

        CorsFilter filter = config.corsFilter();
        assertNotNull(filter);

        CorsConfiguration corsConfig = extractCorsConfig(filter);
        assertNotNull(corsConfig);
        assertNotNull(corsConfig.getAllowedOrigins());
        assertEquals(1, corsConfig.getAllowedOrigins().size());
        assertEquals("https://chat.example.com", corsConfig.getAllowedOrigins().get(0));
    }

    @Test
    @DisplayName("云端模式但未配置域名 - corsFilter 应允许所有来源")
    void corsFilter_shouldAllowAllOrigins_whenCloudModeWithoutOrigins() throws Exception {
        CorsConfig config = new CorsConfig();
        // 云端模式：cloudModeEnabled = true, allowedOrigins = ""（未配置）
        ReflectionTestUtils.setField(config, "cloudModeEnabled", true);
        ReflectionTestUtils.setField(config, "allowedOrigins", "");

        CorsFilter filter = config.corsFilter();
        assertNotNull(filter);

        CorsConfiguration corsConfig = extractCorsConfig(filter);
        assertNotNull(corsConfig);
        // allowedOrigins 为空字符串时退化为允许所有来源
        assertNotNull(corsConfig.getAllowedOriginPatterns());
        assertTrue(corsConfig.getAllowedOriginPatterns().contains("*"));
    }

    @Test
    @DisplayName("云端模式 - 多个域名配置")
    void corsFilter_shouldAllowMultipleOrigins_whenCloudMode() throws Exception {
        CorsConfig config = new CorsConfig();
        ReflectionTestUtils.setField(config, "cloudModeEnabled", true);
        ReflectionTestUtils.setField(config, "allowedOrigins", "https://a.com,https://b.com");

        CorsFilter filter = config.corsFilter();
        assertNotNull(filter);

        CorsConfiguration corsConfig = extractCorsConfig(filter);
        assertNotNull(corsConfig);
        assertNotNull(corsConfig.getAllowedOrigins());
        assertEquals(2, corsConfig.getAllowedOrigins().size());
        assertEquals("https://a.com", corsConfig.getAllowedOrigins().get(0));
        assertEquals("https://b.com", corsConfig.getAllowedOrigins().get(1));
    }

    @Test
    @DisplayName("CORS 配置包含标准HTTP方法")
    void corsConfig_shouldContainStandardHttpMethods() throws Exception {
        CorsConfig config = new CorsConfig();
        CorsFilter filter = config.corsFilter();
        CorsConfiguration corsConfig = extractCorsConfig(filter);

        assertNotNull(corsConfig.getAllowedMethods());
        assertTrue(corsConfig.getAllowedMethods().contains("GET"));
        assertTrue(corsConfig.getAllowedMethods().contains("POST"));
        assertTrue(corsConfig.getAllowedMethods().contains("PUT"));
        assertTrue(corsConfig.getAllowedMethods().contains("DELETE"));
        assertTrue(corsConfig.getAllowedMethods().contains("OPTIONS"));
    }

    /**
     * 从 CorsFilter 中提取 CorsConfiguration（通过反射访问私有字段）
     */
    private CorsConfiguration extractCorsConfig(CorsFilter filter) throws Exception {
        // CorsFilter 中 configSource 是私有字段 CorsConfigurationSource
        Field field = CorsFilter.class.getDeclaredField("configSource");
        field.setAccessible(true);
        Object source = field.get(filter);
        return ((UrlBasedCorsConfigurationSource) source).getCorsConfigurations().get("/**");
    }
}
