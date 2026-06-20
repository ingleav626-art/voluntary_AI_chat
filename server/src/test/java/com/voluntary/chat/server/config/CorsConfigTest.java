package com.voluntary.chat.server.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.filter.CorsFilter;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CorsConfig 单元测试")
class CorsConfigTest {

    @Test
    @DisplayName("corsFilter Bean 可正常创建")
    void corsFilterShouldBeCreated() {
        CorsConfig config = new CorsConfig();
        CorsFilter filter = config.corsFilter();

        assertNotNull(filter);
        assertTrue(filter instanceof CorsFilter);
    }

    @Test
    @DisplayName("CorsConfig 可实例化")
    void shouldInstantiate() {
        CorsConfig config = new CorsConfig();
        assertNotNull(config);
    }
}
