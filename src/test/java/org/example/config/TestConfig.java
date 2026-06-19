package org.example.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 测试配置类
 * 提供测试环境所需的 Bean 配置
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@TestConfiguration
public class TestConfig {

    /**
     * 测试用密码编码器
     *
     * @return PasswordEncoder 实例
     */
    @Bean
    @Primary
    public PasswordEncoder testPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
