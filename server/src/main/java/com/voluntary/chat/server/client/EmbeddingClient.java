package com.voluntary.chat.server.client;

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
 * Embedding 向量生成客户端
 * 支持 OpenAI 兼容的 Embedding API
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingClient {

    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    private static final int REQUEST_TIMEOUT_SECONDS = 30;
    private static final int SUCCESS_STATUS_CODE = 200;
    private static final int MAX_TEXT_LENGTH = 50;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build();

    /**
     * 生成文本 Embedding 向量
     */
    public List<Double> createEmbedding(final String text, final String apiKey) {
        final AiConfig.EmbeddingConfig embeddingConfig = aiConfig.getEmbedding();
        final String provider = embeddingConfig.getProvider();

        // 获取提供商配置
        final AiConfig.ProviderConfig providerConfig = aiConfig.getProviders().get(provider);
        if (providerConfig == null) {
            log.warn("未找到 Embedding 提供商配置: {}", provider);
            return null;
        }

        final String baseUrl = providerConfig.getBaseUrl();
        final String model = embeddingConfig.getModel();

        try {
            // 构建请求
            final Map<String, Object> request = new HashMap<>();
            request.put("model", model);
            request.put("input", text);

            final String requestBody = objectMapper.writeValueAsString(request);

            final HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/embeddings"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                    .build();

            final HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != SUCCESS_STATUS_CODE) {
                log.error("Embedding API 调用失败: status={}, body={}", response.statusCode(), response.body());
                return null;
            }

            // 解析响应
            final JsonNode root = objectMapper.readTree(response.body());
            final JsonNode data = root.path("data");

            if (data.isArray() && data.size() > 0) {
                final JsonNode embedding = data.get(0).path("embedding");
                final List<Double> vector = new ArrayList<>();
                for (final JsonNode node : embedding) {
                    vector.add(node.asDouble());
                }
                log.debug("Embedding 生成成功: dimension={}", vector.size());
                return vector;
            }

            log.warn("Embedding 响应格式异常: {}", response.body());
            return null;

        } catch (final java.io.IOException e) {
            log.error("Embedding 生成IO异常: text={}", text.substring(0, Math.min(MAX_TEXT_LENGTH, text.length())), e);
            return null;
        } catch (final InterruptedException e) {
            log.error("Embedding 生成中断异常: text={}", text.substring(0, Math.min(MAX_TEXT_LENGTH, text.length())), e);
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * 批量生成 Embedding 向量
     */
    public List<List<Double>> createEmbeddings(final List<String> texts, final String apiKey) {
        final List<List<Double>> results = new ArrayList<>();
        for (final String text : texts) {
            final List<Double> embedding = createEmbedding(text, apiKey);
            results.add(embedding);
        }
        return results;
    }
}