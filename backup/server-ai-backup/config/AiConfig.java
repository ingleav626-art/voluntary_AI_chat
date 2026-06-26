package com.voluntary.chat.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 模块配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiConfig {

    /** API Key 加密密钥（32字节，必须通过配置或环境变量提供） */
    private String encryptionKey;

    /** 默认温度参数 */
    private Double defaultTemperature = 0.7;

    /** 默认最大 token 数 */
    private Integer defaultMaxTokens = 2048;

    /** 对话上下文配置 */
    private ContextConfig context = new ContextConfig();

    /** 记忆配置 */
    private MemoryConfig memory = new MemoryConfig();

    /** Embedding 配置 */
    private EmbeddingConfig embedding = new EmbeddingConfig();

    /** 向量存储配置 */
    private VectorConfig vector = new VectorConfig();

    /** 模型提供商配置 */
    private java.util.Map<String, ProviderConfig> providers = new java.util.HashMap<>();

    @Data
    public static class ContextConfig {
        /** 最大历史对话轮数 */
        private Integer maxHistoryRounds = 10;
        /** 最大记忆数量 */
        private Integer maxMemoryCount = 3;
        /** 记忆相似度阈值 */
        private Double memorySimilarityThreshold = 0.7;
    }

    @Data
    public static class MemoryConfig {
        /** 摘要触发阈值（对话轮数） */
        private Integer summarizeThreshold = 20;
        /** 摘要最大长度 */
        private Integer maxSummaryLength = 500;
    }

    @Data
    public static class EmbeddingConfig {
        /** Embedding 提供商 */
        private String provider = "openai";
        /** Embedding 模型 */
        private String model = "text-embedding-3-small";
        /** 向量维度 */
        private Integer dimension = 1536;
    }

    @Data
    public static class VectorConfig {
        /** 是否启用向量存储 */
        private Boolean enabled = false;
        /** 向量存储类型（milvus/qdrant） */
        private String type = "milvus";
        /** Base URL */
        private String baseUrl = "http://localhost:19530";
        /** Collection 名称 */
        private String collection = "ai_memory";
    }

    @Data
    public static class ProviderConfig {
        /** Base URL */
        private String baseUrl;
        /** 默认模型 */
        private String defaultModel;
    }
}