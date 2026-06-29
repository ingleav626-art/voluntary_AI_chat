package com.voluntary.chat.server.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voluntary.chat.server.config.AiConfig;
import lombok.extern.slf4j.Slf4j;

/**
 * OpenAI 兼容 API 客户端
 * 支持 OpenAI / DeepSeek / 通义千问 / 智谱等模型
 */
@Slf4j
public class OpenAiClient {

    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiClient(final AiConfig aiConfig) {
        this.aiConfig = aiConfig;
    }

    private static final int SUCCESS_STATUS_CODE = 200;

    /**
     * 同步对话配置类
     */
    public static class ChatConfig {
        private final String baseUrl;
        private final String apiKey;
        private final String model;
        private final List<Map<String, String>> messages;
        private final Double temperature;
        private final Integer maxTokens;

        public ChatConfig(
                final String baseUrl,
                final String apiKey,
                final String model,
                final List<Map<String, String>> messages,
                final Double temperature,
                final Integer maxTokens) {
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.model = model;
            this.messages = messages;
            this.temperature = temperature;
            this.maxTokens = maxTokens;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public String getModel() {
            return model;
        }

        public List<Map<String, String>> getMessages() {
            return messages;
        }

        public Double getTemperature() {
            return temperature;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }
    }

    /**
     * 同步对话（等待完整响应）
     */
    public String chatCompletion(final ChatConfig config) {

        final Map<String, Object> request = buildChatRequest(
                config.getModel(),
                config.getMessages(),
                config.getTemperature(),
                config.getMaxTokens(),
                false);

        try {
            final URL url = new URL(config.getBaseUrl() + "/chat/completions");
            final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
            conn.setDoOutput(true);

            final String requestBody = objectMapper.writeValueAsString(request);
            conn.getOutputStream().write(requestBody.getBytes(StandardCharsets.UTF_8));

            final int responseCode = conn.getResponseCode();
            if (responseCode != SUCCESS_STATUS_CODE) {
                final String errorBody = readErrorBody(conn);
                log.error("AI API 调用失败: code={}, body={}", responseCode, errorBody);
                throw new RuntimeException("AI API 调用失败: " + errorBody);
            }

            final String responseBody = readResponseBody(conn);
            final JsonNode root = objectMapper.readTree(responseBody);
            final JsonNode choices = root.path("choices");

            if (choices.isArray() && choices.size() > 0) {
                return choices.get(0).path("message").path("content").asText();
            }

            return "";
        } catch (final IOException e) {
            log.error("AI API 调用IO异常", e);
            throw new RuntimeException("AI API 调用IO异常", e);
        }
    }

    /**
     * 流式对话配置类
     */
    public static class StreamConfig {
        private final String baseUrl;
        private final String apiKey;
        private final String model;
        private final List<Map<String, String>> messages;
        private final Double temperature;
        private final Integer maxTokens;
        private final Consumer<String> onChunk;
        private final Consumer<String> onComplete;

        public StreamConfig(
                final String baseUrl,
                final String apiKey,
                final String model,
                final List<Map<String, String>> messages,
                final Double temperature,
                final Integer maxTokens,
                final Consumer<String> onChunk,
                final Consumer<String> onComplete) {
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.model = model;
            this.messages = messages;
            this.temperature = temperature;
            this.maxTokens = maxTokens;
            this.onChunk = onChunk;
            this.onComplete = onComplete;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public String getModel() {
            return model;
        }

        public List<Map<String, String>> getMessages() {
            return messages;
        }

        public Double getTemperature() {
            return temperature;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        public Consumer<String> getOnChunk() {
            return onChunk;
        }

        public Consumer<String> getOnComplete() {
            return onComplete;
        }
    }

    /**
     * 流式对话（逐 chunk 推送）
     */
    public void streamChatCompletion(final StreamConfig config) {
        final Map<String, Object> request = buildChatRequest(
                config.getModel(),
                config.getMessages(),
                config.getTemperature(),
                config.getMaxTokens(),
                true);

        HttpURLConnection conn = null;
        try {
            final URL url = new URL(config.getBaseUrl() + "/chat/completions");
            final Proxy proxy = getProxy();
            conn = (HttpURLConnection) (proxy != null ? url.openConnection(proxy) : url.openConnection());
            
            log.info("AI API 连接: url={}, proxy={}", url, proxy != null ? proxy.address() : "无代理");
            
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setConnectTimeout(30000);  // 30秒连接超时
            conn.setReadTimeout(60000);     // 60秒读取超时
            conn.setDoOutput(true);

            final String requestBody = objectMapper.writeValueAsString(request);
            conn.getOutputStream().write(requestBody.getBytes(StandardCharsets.UTF_8));

            final int responseCode = conn.getResponseCode();
            if (responseCode != SUCCESS_STATUS_CODE) {
                final String errorBody = readErrorBody(conn);
                log.error("AI API 流式调用失败: code={}, body={}", responseCode, errorBody);
                throw new RuntimeException("AI API 流式调用失败: " + errorBody);
            }

            final StringBuilder fullContent = new StringBuilder();
            try (final BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        final String data = line.substring(6);
                        if ("[DONE]".equals(data)) {
                            break;
                        }

                        final JsonNode root = objectMapper.readTree(data);
                        final JsonNode choices = root.path("choices");

                        if (choices.isArray() && choices.size() > 0) {
                            final JsonNode delta = choices.get(0).path("delta");
                            final String content = delta.path("content").asText("");

                            if (!content.isEmpty()) {
                                fullContent.append(content);
                                config.getOnChunk().accept(content);
                            }
                        }
                    }
                }
            }

            config.getOnComplete().accept(fullContent.toString());

        } catch (final IOException e) {
            log.error("AI API 流式调用IO异常", e);
            throw new RuntimeException("AI API 流式调用IO异常", e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 构建 Chat Request
     */
    private Map<String, Object> buildChatRequest(
            final String model,
            final List<Map<String, String>> messages,
            final Double temperature,
            final Integer maxTokens,
            final boolean stream) {

        final Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        request.put("messages", messages);
        request.put("stream", stream);

        if (temperature != null) {
            request.put("temperature", temperature);
        }
        if (maxTokens != null) {
            request.put("max_tokens", maxTokens);
        }

        return request;
    }

    /**
     * 读取响应体
     */
    private String readResponseBody(final HttpURLConnection conn) throws IOException {
        try (final BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            final StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            return body.toString();
        }
    }

    /**
     * 读取错误响应体
     */
    private String readErrorBody(final HttpURLConnection conn) throws IOException {
        final InputStream errorStream = conn.getErrorStream();
        if (errorStream == null) {
            return "";
        }
        try (final BufferedReader reader = new BufferedReader(
                new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {
            final StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            return body.toString();
        }
    }

    /**
     * 获取 HTTP 代理（从 JVM 系统属性或环境变量）
     */
    private Proxy getProxy() {
        // 诊断：打印所有代理相关的属性和环境变量
        log.info("【代理诊断】系统属性: disableProxy={}, https.proxyHost={}, https.proxyPort={}", 
            System.getProperty("disableProxy"), 
            System.getProperty("https.proxyHost"), 
            System.getProperty("https.proxyPort"));
        log.info("【代理诊断】环境变量: NO_PROXY={}, DISABLE_PROXY={}, HTTPS_PROXY={}, HTTP_PROXY={}", 
            System.getenv("NO_PROXY"), 
            System.getenv("DISABLE_PROXY"), 
            System.getenv("HTTPS_PROXY"), 
            System.getenv("HTTP_PROXY"));
        
        // 优先检查是否强制禁用代理
        String disableProxy = System.getProperty("disableProxy");
        if (disableProxy == null) {
            disableProxy = System.getenv("NO_PROXY");
        }
        if (disableProxy == null) {
            disableProxy = System.getenv("DISABLE_PROXY");
        }
        if ("true".equalsIgnoreCase(disableProxy) || "1".equals(disableProxy) || "*".equals(disableProxy)) {
            log.info("已禁用代理，直连访问");
            return null;
        }
        
        // 检查 JVM 系统属性
        String host = System.getProperty("https.proxyHost");
        String portStr = System.getProperty("https.proxyPort");
        
        // 如果系统属性明确设置为空，表示禁用代理
        if (host != null && host.isEmpty()) {
            log.info("JVM 系统属性禁用代理，直连访问");
            return null;
        }
        
        if (host != null && !host.isEmpty()) {
            int port = portStr != null ? Integer.parseInt(portStr) : 7890;
            log.info("使用 JVM 配置的 HTTP 代理: {}:{}", host, port);
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
        }
        
        // 其次使用环境变量
        String proxyUrl = System.getenv("HTTPS_PROXY");
        if (proxyUrl == null || proxyUrl.isEmpty()) {
            proxyUrl = System.getenv("HTTP_PROXY");
        }
        
        if (proxyUrl == null || proxyUrl.isEmpty()) {
            log.info("未配置 HTTP 代理，直连访问");
            return null;
        }
        
        try {
            // 解析代理地址（格式: http://host:port 或 host:port）
            String parsedUrl = proxyUrl;
            int port = 7890;  // 默认端口
            
            if (parsedUrl.startsWith("http://") || parsedUrl.startsWith("https://")) {
                parsedUrl = parsedUrl.replaceFirst("^https?://", "");
            }
            
            if (parsedUrl.contains(":")) {
                final String[] parts = parsedUrl.split(":");
                host = parts[0];
                port = Integer.parseInt(parts[1]);
            } else {
                host = parsedUrl;
            }
            
            log.info("使用环境变量配置的 HTTP 代理: {}:{}", host, port);
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
        } catch (Exception e) {
            log.warn("解析代理地址失败: {}", proxyUrl, e);
            return null;
        }
    }

    /**
     * 获取模型提供商的 Base URL
     */
    public String getBaseUrl(final String provider) {
        final AiConfig.ProviderConfig config = aiConfig.getProviders().get(provider);
        if (config != null && config.getBaseUrl() != null) {
            return config.getBaseUrl();
        }

        // 默认 Base URL
        switch (provider.toLowerCase()) {
            case "openai":
                return "https://api.openai.com/v1";
            case "deepseek":
                return "https://api.deepseek.com/v1";
            case "qwen":
                return "https://dashscope.aliyuncs.com/compatible-mode/v1";
            case "zhipu":
                return "https://open.bigmodel.cn/api/paas/v4";
            default:
                throw new IllegalArgumentException("未知的模型提供商: " + provider);
        }
    }
}