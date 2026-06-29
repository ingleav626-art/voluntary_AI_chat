package com.voluntary.chat.server.config;

import com.voluntary.chat.server.client.EmbeddingClient;
import com.voluntary.chat.server.client.OpenAiClient;
import com.voluntary.chat.server.client.VectorStoreClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 客户端 Bean 配置（Spring 模式专用）
 *
 * <p>
 * ai-core 的客户端类（OpenAiClient / EmbeddingClient / VectorStoreClient）
 * 已改为纯 POJO（无 @Component 注解），同时支持：
 * <ul>
 *   <li>POJO 模式（客户包）：LocalAiEngine 中手动 new</li>
 *   <li>Spring 模式（测试包/云端包）：本配置类提供 @Bean</li>
 * </ul>
 * </p>
 */
@Configuration
public class ClientBeansConfig {

    @Bean
    public OpenAiClient openAiClient(final AiConfig aiConfig) {
        return new OpenAiClient(aiConfig);
    }

    @Bean
    public EmbeddingClient embeddingClient(final AiConfig aiConfig) {
        return new EmbeddingClient(aiConfig);
    }

    @Bean
    public VectorStoreClient vectorStoreClient(final AiConfig aiConfig) {
        return new VectorStoreClient(aiConfig);
    }
}
