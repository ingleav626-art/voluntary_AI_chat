package org.example.client.server;

import com.voluntary.chat.server.config.SecurityConfig;
import jakarta.annotation.PostConstruct;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.HttpEncodingAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketServletAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.dao.PersistenceExceptionTranslationAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

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
        // JPA 相关（使用 MyBatis-Plus）
        HibernateJpaAutoConfiguration.class,
        PersistenceExceptionTranslationAutoConfiguration.class,

        // 数据库迁移工具（未使用）
        FlywayAutoConfiguration.class,
        LiquibaseAutoConfiguration.class,

        // 缓存（未使用）
        CacheAutoConfiguration.class,

        // JMX（已禁用）
        JmxAutoConfiguration.class,

        // 任务调度（未使用）
        TaskExecutionAutoConfiguration.class,
        TaskSchedulingAutoConfiguration.class
})
@ComponentScan(basePackages = { "com.voluntary.chat", "org.example.client.server" })
@Import(SecurityConfig.class)
@MapperScan("com.voluntary.chat.server.mapper")
public class EmbeddedServerStarter {

        private static final Logger LOG = LoggerFactory.getLogger(EmbeddedServerStarter.class);

        @PostConstruct
        public void init() {
                LOG.info("EmbeddedServerStarter 初始化完成, 已导入 SecurityConfig");
        }

        /**
         * ⚠️ TODO: 临时排除 Redis 自动配置（后续启用 Redis 后需删除此内部类）
         *
         * <p>
         * 当前 H2 模式下没有运行 Redis 服务，因此排除 Redis 自动配置，
         * 避免启动时因连不上 Redis 而报错。
         * </p>
         *
         * <p>
         * <b>后续启用 Redis 时需要：</b>
         * <ol>
         * <li>删除此内部配置类 {@code RedisExclusionConfig}</li>
         * <li>删除 application-h2.yml 中的 {@code spring.data.redis.enabled: false}</li>
         * </ol>
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
