package com.voluntary.chat.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * CORS 跨域配置
 *
 * <p>
 * 云端模式下从配置读取允许的来源域名列表，其他模式默认允许所有来源。
 * </p>
 */
@Configuration
public class CorsConfig {

    private static final Logger LOG = LoggerFactory.getLogger(CorsConfig.class);

    /** 云端模式是否启用 */
    @Value("${cloud.mode.enabled:false}")
    private boolean cloudModeEnabled = false;

    /** 允许的来源域名（云端模式生效） */
    @Value("${cors.allowed-origins:}")
    private String allowedOrigins = "";

    /** CORS 缓存时间（秒） */
    @Value("${cors.max-age:3600}")
    private long maxAge = 3600L;

    @Bean
    public CorsFilter corsFilter() {
        final CorsConfiguration config = new CorsConfiguration();

        if (cloudModeEnabled && !allowedOrigins.isBlank()) {
            // 云端模式：只允许配置的域名
            final List<String> origins = List.of(allowedOrigins.split(","));
            config.setAllowedOrigins(origins);
            LOG.info("云端模式CORS配置: 允许来源={}", origins);
        } else {
            // 本地/热点模式：允许所有来源
            config.setAllowedOriginPatterns(List.of("*"));
            LOG.info("非云端模式CORS配置: 允许所有来源");
        }

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(maxAge);

        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}