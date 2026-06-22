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
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@EnableAutoConfiguration
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
