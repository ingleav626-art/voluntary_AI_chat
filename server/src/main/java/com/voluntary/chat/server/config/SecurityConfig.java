package com.voluntary.chat.server.config;

import com.voluntary.chat.server.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /** 云端模式是否启用 */
    @Value("${cloud.mode.enabled:false}")
    private boolean cloudModeEnabled;

    private static final String[] PUBLIC_URLS = {
            "/api/auth/**",
            "/files/**",
            "/avatars/**",
            "/chat-files/**",
            "/ws/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        LOG.info("初始化 SecurityFilterChain, 云端模式: {}, 公开URL: {}", cloudModeEnabled,
                java.util.Arrays.toString(PUBLIC_URLS));

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // 云端模式下禁用 actuator 端点
                    if (cloudModeEnabled) {
                        auth.requestMatchers("/actuator/**").denyAll();
                    } else {
                        auth.requestMatchers("/actuator/**").permitAll();
                    }
                    auth
                            .requestMatchers(PUBLIC_URLS).permitAll()
                            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // 云端模式添加安全响应头
        if (cloudModeEnabled) {
            http.headers(headers -> headers
                    .contentTypeOptions(c -> {
                    })
                    .frameOptions(f -> f.deny())
                    .httpStrictTransportSecurity(h -> h
                            .includeSubDomains(true)
                            .maxAgeInSeconds(31536000)));
            LOG.info("云端模式安全头已启用");
        }

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}