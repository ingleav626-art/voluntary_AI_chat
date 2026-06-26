package org.example.client.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voluntary.chat.server.config.SecurityConfig;
import com.voluntary.chat.server.security.JwtTokenProvider;
import com.voluntary.chat.server.service.AiChatService;
import com.voluntary.chat.server.service.AiGroupConfigService;
import com.voluntary.chat.server.websocket.AiWebSocketHandler;
import com.voluntary.chat.server.websocket.JwtHandshakeInterceptor;
import jakarta.annotation.PostConstruct;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.dao.PersistenceExceptionTranslationAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 内嵌后端启动器（优化启动速度）
 *
 * <p>
 * 通过排除不必要的自动配置，减少启动时间：
 * <ul>
 * <li>JPA/Hibernate（使用MyBatis-Plus）</li>
 * <li>数据库迁移工具（Flyway/Liquibase）</li>
 * <li>缓存自动配置（未使用）</li>
 * <li>JMX（已禁用）</li>
 * <li>任务调度（未使用）</li>
 * </ul>
 * </p>
 */
@Configuration
@EnableAutoConfiguration(exclude = {
                HibernateJpaAutoConfiguration.class,
                PersistenceExceptionTranslationAutoConfiguration.class,

                // 数据库迁移工具（未使用）
                FlywayAutoConfiguration.class,
                LiquibaseAutoConfiguration.class,

                // 缓存（未使用）
                CacheAutoConfiguration.class,

                // JMX（已禁用）
                JmxAutoConfiguration.class,

                // 安全（客户包使用自定义 SecurityConfig，不需要 Spring Boot 默认安全）
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class,

                // 任务调度（未使用）
                TaskExecutionAutoConfiguration.class,
                TaskSchedulingAutoConfiguration.class
})
@ComponentScan(basePackages = { "com.voluntary.chat.server", "org.example.client.server" })
@MapperScan("com.voluntary.chat.server.mapper")
public class EmbeddedServerStarter {

        private static final Logger LOG = LoggerFactory.getLogger(EmbeddedServerStarter.class);

        @PostConstruct
        public void init() {
                LOG.info("EmbeddedServerStarter 初始化完成, 已导入 SecurityConfig");
        }

        /**
         * AI 任务执行器（AiChatService 异步 AI 对话需要）
         * server 模块的 AsyncConfig 不在客户包内，因此在本启动器中声明。
         */
        @Bean("aiTaskExecutor")
        public TaskExecutor aiTaskExecutor() {
                final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
                executor.setCorePoolSize(2);
                executor.setMaxPoolSize(4);
                executor.setQueueCapacity(50);
                executor.setKeepAliveSeconds(60);
                executor.setThreadNamePrefix("ai-task-");
                executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
                executor.setWaitForTasksToCompleteOnShutdown(true);
                executor.setAwaitTerminationSeconds(10);
                executor.initialize();
                return executor;
        }

        /**
         * AiWebSocketHandler 是 ai-core 提供的无注解抽象处理器，需要手动声明为 Bean。
         * 当 server 模块的 ChatWebSocketHandler（子类）存在时，此 Bean 不会注册。
         */
        @Bean
        @ConditionalOnMissingBean(name = "chatWebSocketHandler")
        public AiWebSocketHandler aiWebSocketHandler(
                        final AiChatService aiChatService,
                        final AiGroupConfigService aiGroupConfigService,
                        final ObjectMapper objectMapper) {
                return new AiWebSocketHandler(aiChatService, aiGroupConfigService, objectMapper);
        }

        /**
         * JwtHandshakeInterceptor 在 ai-core 中有 @Component 注解，但当 server 模块同时存在时，
         * 类加载器会加载 server 版的（无 @Component）。此 @Bean 确保 client 包中总有一个可用的实例。
         */
        @Bean
        @ConditionalOnMissingBean(JwtHandshakeInterceptor.class)
        public JwtHandshakeInterceptor jwtHandshakeInterceptor(final JwtTokenProvider jwtTokenProvider) {
                return new JwtHandshakeInterceptor(jwtTokenProvider);
        }

        /**
         * ⚠️ TODO: 临时排除 Redis 自动配置（后续启用 Redis 后需删除此内部类）
         *
         * <p>
         * 当前 H2 模式下没有运行 Redis 服务，因此排除 Redis 自动配置，
         * 避免启动时因连不上 Redis 而报错。
         * </p>
         */
        @Configuration
        @ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "false", matchIfMissing = false)
        @ImportAutoConfiguration(exclude = {
                        RedisAutoConfiguration.class,
                        RedisReactiveAutoConfiguration.class,
                        RedisRepositoriesAutoConfiguration.class
        })
        public static class RedisExclusionConfig {
        }
}
