package org.example.client.server;

import com.voluntary.chat.server.config.SecurityConfig;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@EnableAutoConfiguration
@ComponentScan(basePackages = {"com.voluntary.chat", "org.example.client.server"})
@Import(SecurityConfig.class)
public class EmbeddedServerStarter {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedServerStarter.class);

    @PostConstruct
    public void init() {
        LOG.info("EmbeddedServerStarter 初始化完成, 已导入 SecurityConfig");
    }
}
