package com.voluntary.chat.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 模块 Spring 配置属性绑定（仅 server 模块使用）
 *
 * <p>
 * 继承 {@link AiConfig} 并添加 {@code @ConfigurationProperties} 注解，
 * 使 application.yml 中 {@code ai.*} 属性自动绑定到 AiConfig 对象。
 * </p>
 */
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiConfigProperties extends AiConfig {

}