package com.voluntary.chat.server.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voluntary.chat.server.config.AiConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 向量存储客户端
 * 支持 Milvus 和 Qdrant 向量数据库
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorStoreClient {

    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    private static final int REQUEST_TIMEOUT_SECONDS = 30;
    private static final int SUCCESS_STATUS_CODE = 200;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build();

    /**
     * 向量存储配置类
     */
    public static class VectorStoreConfig {
        private final Long memoryId;
        private final List<Double> vector;
        private final String summary;
        private final Long aiId;
        private final Long userId;

        public VectorStoreConfig(
                final Long memoryId,
                final List<Double> vector,
                final String summary,
                final Long aiId,
                final Long userId) {
            this.memoryId = memoryId;
            this.vector = vector;
            this.summary = summary;
            this.aiId = aiId;
            this.userId = userId;
        }

        public Long getMemoryId() {
            return memoryId;
        }

        public List<Double> getVector() {
            return vector;
        }

        public String getSummary() {
            return summary;
        }

        public Long getAiId() {
            return aiId;
        }

        public Long getUserId() {
            return userId;
        }
    }

    /**
     * 向量搜索配置类
     */
    public static class VectorSearchConfig {
        private final List<Double> queryVector;
        private final Long aiId;
        private final Long userId;
        private final int topK;
        private final double threshold;

        public VectorSearchConfig(
                final List<Double> queryVector,
                final Long aiId,
                final Long userId,
                final int topK,
                final double threshold) {
            this.queryVector = queryVector;
            this.aiId = aiId;
            this.userId = userId;
            this.topK = topK;
            this.threshold = threshold;
        }

        public List<Double> getQueryVector() {
            return queryVector;
        }

        public Long getAiId() {
            return aiId;
        }

        public Long getUserId() {
            return userId;
        }

        public int getTopK() {
            return topK;
        }

        public double getThreshold() {
            return threshold;
        }
    }

    /**
     * 存储向量
     */
    public boolean storeVector(final VectorStoreConfig config) {
        final AiConfig.VectorConfig vectorConfig = aiConfig.getVector();
        if (vectorConfig == null || !vectorConfig.getEnabled()) {
            log.debug("向量存储未启用，跳过存储");
            return false;
        }

        final String storeType = vectorConfig.getType();
        final String baseUrl = vectorConfig.getBaseUrl();
        final String collection = vectorConfig.getCollection();

        try {
            if ("milvus".equalsIgnoreCase(storeType)) {
                return storeToMilvus(config, baseUrl, collection);
            } else if ("qdrant".equalsIgnoreCase(storeType)) {
                return storeToQdrant(config, baseUrl, collection);
            } else {
                log.warn("未知的向量存储类型: {}", storeType);
                return false;
            }
        } catch (final IOException e) {
            log.error("向量存储IO异常: memoryId={}", config.getMemoryId(), e);
            return false;
        } catch (final InterruptedException e) {
            log.error("向量存储中断异常: memoryId={}", config.getMemoryId(), e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 搜索相似向量
     */
    public List<Map<String, Object>> searchSimilar(final VectorSearchConfig config) {
        final AiConfig.VectorConfig vectorConfig = aiConfig.getVector();
        if (vectorConfig == null || !vectorConfig.getEnabled()) {
            log.debug("向量存储未启用，返回空结果");
            return new ArrayList<>();
        }

        final String storeType = vectorConfig.getType();
        final String baseUrl = vectorConfig.getBaseUrl();
        final String collection = vectorConfig.getCollection();

        try {
            if ("milvus".equalsIgnoreCase(storeType)) {
                return searchFromMilvus(config, baseUrl, collection);
            } else if ("qdrant".equalsIgnoreCase(storeType)) {
                return searchFromQdrant(config, baseUrl, collection);
            } else {
                log.warn("未知的向量存储类型: {}", storeType);
                return new ArrayList<>();
            }
        } catch (final IOException e) {
            log.error("向量搜索IO异常", e);
            return new ArrayList<>();
        } catch (final InterruptedException e) {
            log.error("向量搜索中断异常", e);
            Thread.currentThread().interrupt();
            return new ArrayList<>();
        }
    }

    /**
     * 删除向量
     */
    public boolean deleteVector(final Long memoryId) {
        final AiConfig.VectorConfig vectorConfig = aiConfig.getVector();
        if (vectorConfig == null || !vectorConfig.getEnabled()) {
            return true;
        }

        final String storeType = vectorConfig.getType();
        final String baseUrl = vectorConfig.getBaseUrl();
        final String collection = vectorConfig.getCollection();

        try {
            if ("milvus".equalsIgnoreCase(storeType)) {
                return deleteFromMilvus(memoryId, baseUrl, collection);
            } else if ("qdrant".equalsIgnoreCase(storeType)) {
                return deleteFromQdrant(memoryId, baseUrl, collection);
            }
            return false;
        } catch (final IOException e) {
            log.error("向量删除IO异常: memoryId={}", memoryId, e);
            return false;
        } catch (final InterruptedException e) {
            log.error("向量删除中断异常: memoryId={}", memoryId, e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // === Milvus 操作 ===

    private boolean storeToMilvus(
            final VectorStoreConfig config,
            final String baseUrl,
            final String collection) throws IOException, InterruptedException {

        final Map<String, Object> data = new HashMap<>();
        data.put("collection_name", collection);
        data.put("data", List.of(Map.of(
                "id", config.getMemoryId(),
                "vector", config.getVector(),
                "ai_id", config.getAiId(),
                "user_id", config.getUserId(),
                "summary", config.getSummary())));

        final String requestBody = objectMapper.writeValueAsString(data);

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v2/vectordb/entities/insert"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .build();

        final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == SUCCESS_STATUS_CODE) {
            log.debug("Milvus 向量存储成功: memoryId={}", config.getMemoryId());
            return true;
        }

        log.error("Milvus 向量存储失败: status={}", response.statusCode());
        return false;
    }

    private List<Map<String, Object>> searchFromMilvus(
            final VectorSearchConfig config,
            final String baseUrl,
            final String collection) throws IOException, InterruptedException {

        final Map<String, Object> data = new HashMap<>();
        data.put("collection_name", collection);
        data.put("data", List.of(config.getQueryVector()));
        data.put("anns_field", "vector");
        data.put("top_k", config.getTopK());
        data.put("filter", buildMilvusFilter(config.getAiId(), config.getUserId()));

        final String requestBody = objectMapper.writeValueAsString(data);

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v2/vectordb/entities/search"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .build();

        final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != SUCCESS_STATUS_CODE) {
            log.error("Milvus 搜索失败: status={}", response.statusCode());
            return new ArrayList<>();
        }

        return parseSearchResults(response.body(), config.getThreshold());
    }

    private boolean deleteFromMilvus(final Long memoryId, final String baseUrl, final String collection)
            throws IOException, InterruptedException {
        final Map<String, Object> data = new HashMap<>();
        data.put("collection_name", collection);
        data.put("id", memoryId);

        final String requestBody = objectMapper.writeValueAsString(data);

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v2/vectordb/entities/delete"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .build();

        final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == SUCCESS_STATUS_CODE;
    }

    private String buildMilvusFilter(final Long aiId, final Long userId) {
        return "ai_id == " + aiId + " and user_id == " + userId;
    }

    // === Qdrant 操作 ===

    private boolean storeToQdrant(
            final VectorStoreConfig config,
            final String baseUrl,
            final String collection) throws IOException, InterruptedException {

        final Map<String, Object> point = new HashMap<>();
        point.put("id", config.getMemoryId());
        point.put("vector", config.getVector());
        point.put("payload", Map.of(
                "ai_id", config.getAiId(),
                "user_id", config.getUserId(),
                "summary", config.getSummary()));

        final Map<String, Object> data = new HashMap<>();
        data.put("points", List.of(point));

        final String requestBody = objectMapper.writeValueAsString(data);

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/collections/" + collection + "/points"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .build();

        final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == SUCCESS_STATUS_CODE) {
            log.debug("Qdrant 向量存储成功: memoryId={}", config.getMemoryId());
            return true;
        }

        log.error("Qdrant 向量存储失败: status={}", response.statusCode());
        return false;
    }

    private List<Map<String, Object>> searchFromQdrant(
            final VectorSearchConfig config,
            final String baseUrl,
            final String collection) throws IOException, InterruptedException {

        final Map<String, Object> data = new HashMap<>();
        data.put("vector", config.getQueryVector());
        data.put("limit", config.getTopK());
        data.put("score_threshold", config.getThreshold());
        data.put("filter", Map.of(
                "must", List.of(
                        Map.of("key", "ai_id", "match", Map.of("value", config.getAiId())),
                        Map.of("key", "user_id", "match", Map.of("value", config.getUserId())))));

        final String requestBody = objectMapper.writeValueAsString(data);

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/collections/" + collection + "/points/search"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .build();

        final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != SUCCESS_STATUS_CODE) {
            log.error("Qdrant 搜索失败: status={}", response.statusCode());
            return new ArrayList<>();
        }

        return parseQdrantSearchResults(response.body());
    }

    private boolean deleteFromQdrant(final Long memoryId, final String baseUrl, final String collection)
            throws IOException, InterruptedException {
        final Map<String, Object> data = new HashMap<>();
        data.put("points", List.of(memoryId));

        final String requestBody = objectMapper.writeValueAsString(data);

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/collections/" + collection + "/points/delete"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .build();

        final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == SUCCESS_STATUS_CODE;
    }

    // === 结果解析 ===

    private List<Map<String, Object>> parseSearchResults(final String body, final double threshold) throws IOException {
        final List<Map<String, Object>> results = new ArrayList<>();
        final JsonNode root = objectMapper.readTree(body);
        final JsonNode data = root.path("data");

        if (data.isArray()) {
            for (final JsonNode item : data) {
                final double distance = item.path("distance").asDouble();
                // Milvus 返回的是距离，需要转换为相似度（1 - distance）
                final double similarity = 1.0 - distance;

                if (similarity >= threshold) {
                    final Map<String, Object> result = new HashMap<>();
                    result.put("id", item.path("id").asLong());
                    result.put("score", similarity);
                    result.put("summary", item.path("summary").asText());
                    results.add(result);
                }
            }
        }

        return results;
    }

    private List<Map<String, Object>> parseQdrantSearchResults(final String body) throws IOException {
        final List<Map<String, Object>> results = new ArrayList<>();
        final JsonNode root = objectMapper.readTree(body);
        final JsonNode resultArray = root.path("result");

        if (resultArray.isArray()) {
            for (final JsonNode item : resultArray) {
                final Map<String, Object> result = new HashMap<>();
                result.put("id", item.path("id").asLong());
                result.put("score", item.path("score").asDouble());
                result.put("summary", item.path("payload").path("summary").asText());
                results.add(result);
            }
        }

        return results;
    }
}