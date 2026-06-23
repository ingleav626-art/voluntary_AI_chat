package org.example.client.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.example.client.config.ClientConfig;
import org.example.client.model.ApiResponse;
import org.example.client.util.ErrorCodeRegistry;
import org.example.client.util.JsonUtils;
import org.example.client.util.TokenStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP 基础服务
 *
 * <p>
 * 封装通用 HTTP 请求逻辑，自动注入 JWT Token。
 * </p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public abstract class BaseHttpService {

    private static final Logger LOG = LoggerFactory.getLogger(BaseHttpService.class);

    /** HTTP 状态码 - 成功 */
    private static final int HTTP_OK = 200;

    /** HTTP 状态码 - 未认证 */
    private static final int HTTP_UNAUTHORIZED = 401;

    /** HTTP 状态码 - 无权限 */
    private static final int HTTP_FORBIDDEN = 403;

    /** Authorization 请求头前缀 */
    private static final String AUTH_HEADER_PREFIX = "Bearer ";

    /** Authorization 请求头名称 */
    private static final String AUTH_HEADER = "Authorization";

    /** Content-Type 请求头名称 */
    private static final String CONTENT_TYPE_HEADER = "Content-Type";

    /** JSON Content-Type */
    private static final String JSON_CONTENT_TYPE = "application/json";

    /** 共享 HttpClient 实例 */
    private final HttpClient httpClient;

    /**
     * 获取共享 HttpClient 实例（供子类使用）
     *
     * @return HttpClient 实例
     */
    protected HttpClient getHttpClient() {
        return httpClient;
    }

    protected BaseHttpService() {
        final ClientConfig config = ClientConfig.getInstance();
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getConnectTimeout()))
                .build();
    }

    /**
     * 构建 GET 请求
     *
     * @param path 接口路径
     * @return HttpRequest 构建器
     */
    protected HttpRequest.Builder buildGetRequest(final String path) {
        final String url = ClientConfig.getInstance().getBaseUrl() + path;
        final HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header(CONTENT_TYPE_HEADER, JSON_CONTENT_TYPE)
                .timeout(Duration.ofSeconds(ClientConfig.getInstance().getReadTimeout()))
                .GET();
        addAuthHeader(builder);
        return builder;
    }

    /**
     * 构建 POST 请求
     *
     * @param path 接口路径
     * @param body 请求体对象
     * @return HttpRequest 构建器
     */
    protected HttpRequest.Builder buildPostRequest(final String path, final Object body) {
        final String url = ClientConfig.getInstance().getBaseUrl() + path;
        final String json = JsonUtils.toJson(body);
        final HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header(CONTENT_TYPE_HEADER, JSON_CONTENT_TYPE)
                .timeout(Duration.ofSeconds(ClientConfig.getInstance().getReadTimeout()))
                .POST(HttpRequest.BodyPublishers.ofString(json != null ? json : "{}"));
        addAuthHeader(builder);
        return builder;
    }

    /**
     * 构建 DELETE 请求
     *
     * @param path 接口路径
     * @return HttpRequest 构建器
     */
    protected HttpRequest.Builder buildDeleteRequest(final String path) {
        final String url = ClientConfig.getInstance().getBaseUrl() + path;
        final HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header(CONTENT_TYPE_HEADER, JSON_CONTENT_TYPE)
                .timeout(Duration.ofSeconds(ClientConfig.getInstance().getReadTimeout()))
                .DELETE();
        addAuthHeader(builder);
        return builder;
    }

    /**
     * 构建 PUT 请求
     *
     * @param path 接口路径
     * @param body 请求体对象
     * @return HttpRequest 构建器
     */
    protected HttpRequest.Builder buildPutRequest(final String path, final Object body) {
        final String url = ClientConfig.getInstance().getBaseUrl() + path;
        final String json = JsonUtils.toJson(body);
        final HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header(CONTENT_TYPE_HEADER, JSON_CONTENT_TYPE)
                .timeout(Duration.ofSeconds(ClientConfig.getInstance().getReadTimeout()))
                .PUT(HttpRequest.BodyPublishers.ofString(json != null ? json : "{}"));
        addAuthHeader(builder);
        return builder;
    }

    /**
     * 添加 Authorization 请求头
     *
     * @param builder 请求构建器
     */
    private void addAuthHeader(final HttpRequest.Builder builder) {
        final org.example.client.model.LoginResponse token = TokenStorage.load();
        if (token != null && token.getAccessToken() != null) {
            builder.header(AUTH_HEADER, AUTH_HEADER_PREFIX + token.getAccessToken());
        }
    }

    /**
     * 检查登录状态
     *
     * <p>在调用需要认证的接口前，先检查用户是否已登录。</p>
     *
     * @return true 如果已登录，false 如果未登录
     */
    protected boolean checkLoginStatus() {
        final org.example.client.model.LoginResponse token = TokenStorage.load();
        return token != null && token.getAccessToken() != null;
    }

    /**
     * 创建未登录错误响应
     *
     * @param <T> 响应数据泛型
     * @return API 响应
     */
    protected <T> ApiResponse<T> createNotLoggedInResponse() {
        LOG.warn("用户未登录，无法访问需要认证的接口");
        return createErrorResponse(HTTP_UNAUTHORIZED, "请先登录");
    }

    /**
     * 发送请求并解析响应
     *
     * @param request      HTTP 请求
     * @param responseType 响应数据类型
     * @param <T>          响应数据泛型
     * @return 异步结果
     */
    protected <T> CompletableFuture<ApiResponse<T>> sendRequest(
            final HttpRequest request,
            final com.fasterxml.jackson.databind.JavaType responseType) {
        final CompletableFuture<ApiResponse<T>> future = httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> handleResponse(response, responseType));
        return future.exceptionally(ex -> {
            LOG.error("请求异常", ex);
            final ApiResponse<T> errorResponse = new ApiResponse<>();
            errorResponse.setCode(500);
            errorResponse.setMessage("网络连接失败，请检查网络");
            return errorResponse;
        });
    }

    /**
     * 处理 HTTP 响应
     *
     * <p>
     * 统一错误码映射逻辑：
     * 1. HTTP 200 时检查业务码，非200业务码通过 ErrorCodeRegistry 映射
     * 2. 非 HTTP 200 时尝试解析后端错误体，再通过 ErrorCodeRegistry 映射
     * 3. 401/403 特殊处理
     * </p>
     *
     * @param response     HTTP 响应
     * @param responseType 响应数据类型
     * @param <T>          响应数据泛型
     * @return API 响应
     */
    private <T> ApiResponse<T> handleResponse(
            final HttpResponse<String> response,
            final com.fasterxml.jackson.databind.JavaType responseType) {
        final int statusCode = response.statusCode();

        if (statusCode == HTTP_OK) {
            final ApiResponse<T> apiResponse = JsonUtils.fromJson(response.body(), responseType);
            if (apiResponse == null) {
                LOG.error("响应解析失败: body={}", response.body());
                return createErrorResponse(HTTP_OK, "响应解析失败");
            }
            // 业务码非200时，通过 ErrorCodeRegistry 映射用户友好提示
            if (!apiResponse.isSuccess()) {
                final String friendlyMessage = ErrorCodeRegistry.getMessage(
                        apiResponse.getCode(), apiResponse.getMessage());
                LOG.warn("业务错误: code={}, backendMsg={}, displayMsg={}",
                        apiResponse.getCode(), apiResponse.getMessage(), friendlyMessage);
                apiResponse.setMessage(friendlyMessage);
            }
            return apiResponse;
        }

        if (statusCode == HTTP_UNAUTHORIZED) {
            LOG.warn("Token 已失效，需要重新登录");
            return createErrorResponse(HTTP_UNAUTHORIZED, ErrorCodeRegistry.getMessage(HTTP_UNAUTHORIZED));
        }

        if (statusCode == HTTP_FORBIDDEN) {
            LOG.warn("请求被拒绝: 403 Forbidden，可能未登录或 Token 无效");
            return createErrorResponse(HTTP_FORBIDDEN, ErrorCodeRegistry.getMessage(HTTP_FORBIDDEN));
        }

        // 尝试从响应体中解析后端返回的具体错误消息
        final ApiResponse<T> errorBody = JsonUtils.fromJson(
                response.body(), getTypeFactory().constructParametricType(ApiResponse.class, Void.class));
        if (errorBody != null && errorBody.getMessage() != null && !errorBody.getMessage().isEmpty()) {
            final String friendlyMessage = ErrorCodeRegistry.getMessage(
                    errorBody.getCode(), errorBody.getMessage());
            LOG.error("请求失败: statusCode={}, errorCode={}, backendMsg={}, displayMsg={}",
                    statusCode, errorBody.getCode(), errorBody.getMessage(), friendlyMessage);
            errorBody.setMessage(friendlyMessage);
            return errorBody;
        }

        LOG.error("请求失败: statusCode={}", statusCode);
        return createErrorResponse(statusCode, ErrorCodeRegistry.getMessage(statusCode));
    }

    /**
     * 处理请求异常
     *
     * @param ex  异常
     * @param <T> 响应数据泛型
     * @return API 响应
     */
    private <T> ApiResponse<T> handleError(final Throwable ex) {
        LOG.error("请求异常", ex);
        return createErrorResponse(500, "网络连接失败，请检查网络");
    }

    /**
     * 创建错误响应
     *
     * @param code    错误码
     * @param message 错误消息
     * @param <T>     响应数据泛型
     * @return API 响应
     */
    protected <T> ApiResponse<T> createErrorResponse(final int code, final String message) {
        final ApiResponse<T> response = new ApiResponse<>();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }

    /**
     * 获取 ObjectMapper 的类型工厂
     *
     * @return 类型工厂
     */
    protected com.fasterxml.jackson.databind.type.TypeFactory getTypeFactory() {
        return JsonUtils.getMapper().getTypeFactory();
    }
}
