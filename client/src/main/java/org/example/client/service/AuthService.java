package org.example.client.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.example.client.config.ClientConfig;
import org.example.client.model.ApiResponse;
import org.example.client.model.ForgotPasswordRequest;
import org.example.client.model.LoginRequest;
import org.example.client.model.LoginResponse;
import org.example.client.model.RegisterRequest;
import org.example.client.model.RegisterResponse;
import org.example.client.model.RefreshTokenRequest;
import org.example.client.model.RefreshTokenResponse;
import org.example.client.model.SmsSendRequest;
import org.example.client.util.ErrorCodeRegistry;
import org.example.client.util.JsonUtils;
import org.example.client.util.TokenStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 认证服务
 *
 * <p>
 * 错误码映射统一由 {@link org.example.client.util.ErrorCodeRegistry} 管理，
 * 本类仅负责请求发送和响应解析。
 * </p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class AuthService extends BaseHttpService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);

    private static final AuthService INSTANCE = new AuthService();

    private static final String LOGIN_PATH = "/auth/login";

    /** 注册接口路径 */
    private static final String REGISTER_PATH = "/auth/register";

    /** 发送验证码接口路径 */
    private static final String SMS_SEND_PATH = "/auth/sms/send";

    /** 忘记密码接口路径 */
    private static final String FORGOT_PASSWORD_PATH = "/auth/forgot-password";

    /** HTTP 状态码 - 成功 */
    private static final int HTTP_OK = 200;

    /** HTTP 状态码 - 服务器错误 */
    private static final int HTTP_SERVER_ERROR = 500;

    /** 手机号脱敏 - 前缀保留位数 */
    private static final int PHONE_PREFIX_LENGTH = 3;

    /** 手机号脱敏 - 后缀保留位数 */
    private static final int PHONE_SUFFIX_LENGTH = 4;

    /** 手机号脱敏 - 最小长度 */
    private static final int PHONE_MIN_LENGTH = 7;

    private final HttpClient httpClient;

    private AuthService() {
        final ClientConfig config = ClientConfig.getInstance();
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getConnectTimeout()))
                .build();
    }

    public static AuthService getInstance() {
        return INSTANCE;
    }

    /**
     * 检查用户是否已登录
     *
     * <p>通过检查 TokenStorage 中是否存在有效的 Token 来判断登录状态。</p>
     *
     * @return true 如果已登录，false 如果未登录
     */
    public boolean isLoggedIn() {
        final org.example.client.model.LoginResponse token = TokenStorage.load();
        return token != null && token.getAccessToken() != null;
    }

    /**
     * 异步登录
     *
     * @param request 登录请求
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<LoginResponse>> login(final LoginRequest request) {
        final String url = ClientConfig.getInstance().getBaseUrl() + LOGIN_PATH;
        final String body = JsonUtils.toJson(request);

        LOG.info("发送登录请求: phone={}", maskPhone(request.getPhone()));

        final HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(ClientConfig.getInstance().getReadTimeout()))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::handleLoginResponse)
                .exceptionally(this::handleLoginError);
    }

    /**
     * 异步刷新令牌
     *
     * @param request 刷新令牌请求
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<RefreshTokenResponse>> refreshToken(final RefreshTokenRequest request) {
        final String url = ClientConfig.getInstance().getBaseUrl() + "/auth/refresh";
        final String body = JsonUtils.toJson(request);

        LOG.info("发送刷新令牌请求");

        final HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(ClientConfig.getInstance().getReadTimeout()))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::handleRefreshTokenResponse)
                .exceptionally(this::handleRefreshTokenError);
    }

    /**
     * 异步注册
     *
     * @param request 注册请求
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<RegisterResponse>> register(final RegisterRequest request) {
        final String url = ClientConfig.getInstance().getBaseUrl() + REGISTER_PATH;
        final String body = JsonUtils.toJson(request);

        LOG.info("发送注册请求: phone={}", maskPhone(request.getPhone()));

        final HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(ClientConfig.getInstance().getReadTimeout()))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::handleRegisterResponse)
                .exceptionally(this::handleRegisterError);
    }

    /**
     * 异步发送短信验证码
     *
     * @param request 发送验证码请求
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<Void>> sendSmsCode(final SmsSendRequest request) {
        final String url = ClientConfig.getInstance().getBaseUrl() + SMS_SEND_PATH;
        final String body = JsonUtils.toJson(request);

        LOG.info("发送验证码请求: phone={}", maskPhone(request.getPhone()));

        final HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(ClientConfig.getInstance().getReadTimeout()))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::handleSmsResponse)
                .exceptionally(this::handleSmsError);
    }

    private ApiResponse<RegisterResponse> handleRegisterResponse(final HttpResponse<String> response) {
        final int statusCode = response.statusCode();

        if (statusCode == HTTP_OK) {
            final ApiResponse<RegisterResponse> apiResponse = JsonUtils.fromJson(response.body(),
                    JsonUtils.getMapper().getTypeFactory()
                            .constructParametricType(ApiResponse.class, RegisterResponse.class));

            if (apiResponse != null && apiResponse.isSuccess()) {
                LOG.info("注册成功: userId={}",
                        apiResponse.getData().getUser() != null
                                ? apiResponse.getData().getUser().getUserId()
                                : null);
            } else if (apiResponse != null) {
                // 通过 ErrorCodeRegistry 映射用户友好提示
                final String friendlyMessage = ErrorCodeRegistry.getMessage(
                        apiResponse.getCode(), apiResponse.getMessage());
                LOG.warn("注册失败: code={}, backendMsg={}, displayMsg={}",
                        apiResponse.getCode(), apiResponse.getMessage(), friendlyMessage);
                apiResponse.setMessage(friendlyMessage);
            }

            return apiResponse;
        }

        LOG.error("注册请求失败: statusCode={}", statusCode);
        return createRegisterErrorResponse(statusCode, ErrorCodeRegistry.getMessage(statusCode));
    }

    private ApiResponse<RegisterResponse> handleRegisterError(final Throwable ex) {
        LOG.error("注册请求异常", ex);
        return createRegisterErrorResponse(HTTP_SERVER_ERROR, "网络连接失败，请检查网络");
    }

    private ApiResponse<RegisterResponse> createRegisterErrorResponse(final int code, final String message) {
        final ApiResponse<RegisterResponse> response = new ApiResponse<>();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }

    @SuppressWarnings("unchecked")
    private ApiResponse<Void> handleSmsResponse(final HttpResponse<String> response) {
        final int statusCode = response.statusCode();

        if (statusCode == HTTP_OK) {
            final ApiResponse<Void> apiResponse = JsonUtils.fromJson(response.body(),
                    JsonUtils.getMapper().getTypeFactory()
                            .constructParametricType(ApiResponse.class, Void.class));

            if (apiResponse != null && apiResponse.isSuccess()) {
                LOG.info("验证码发送成功");
            } else if (apiResponse != null) {
                final String friendlyMessage = ErrorCodeRegistry.getMessage(
                        apiResponse.getCode(), apiResponse.getMessage());
                LOG.warn("验证码发送失败: code={}, backendMsg={}, displayMsg={}",
                        apiResponse.getCode(), apiResponse.getMessage(), friendlyMessage);
                apiResponse.setMessage(friendlyMessage);
            }

            return apiResponse;
        }

        LOG.error("验证码请求失败: statusCode={}", statusCode);
        return createSmsErrorResponse(statusCode, ErrorCodeRegistry.getMessage(statusCode));
    }

    private ApiResponse<Void> handleSmsError(final Throwable ex) {
        LOG.error("验证码请求异常", ex);
        return createSmsErrorResponse(HTTP_SERVER_ERROR, "网络连接失败，请检查网络");
    }

    private ApiResponse<Void> createSmsErrorResponse(final int code, final String message) {
        final ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }

    private ApiResponse<LoginResponse> handleLoginResponse(final HttpResponse<String> response) {
        final int statusCode = response.statusCode();

        if (statusCode == HTTP_OK) {
            final ApiResponse<LoginResponse> apiResponse = JsonUtils.fromJson(response.body(),
                    JsonUtils.getMapper().getTypeFactory()
                            .constructParametricType(ApiResponse.class, LoginResponse.class));

            if (apiResponse != null && apiResponse.isSuccess()) {
                LOG.info("登录成功: userId={}", apiResponse.getData().getUser().getUserId());
            } else if (apiResponse != null) {
                final String friendlyMessage = ErrorCodeRegistry.getMessage(
                        apiResponse.getCode(), apiResponse.getMessage());
                LOG.warn("登录失败: code={}, backendMsg={}, displayMsg={}",
                        apiResponse.getCode(), apiResponse.getMessage(), friendlyMessage);
                apiResponse.setMessage(friendlyMessage);
            }

            return apiResponse;
        }

        LOG.error("登录请求失败: statusCode={}", statusCode);
        return createErrorResponse(statusCode, ErrorCodeRegistry.getMessage(statusCode));
    }

    private ApiResponse<LoginResponse> handleLoginError(final Throwable ex) {
        LOG.error("登录请求异常", ex);
        return createErrorResponse(HTTP_SERVER_ERROR, "网络连接失败，请检查网络");
    }

    private ApiResponse<RefreshTokenResponse> handleRefreshTokenResponse(final HttpResponse<String> response) {
        final int statusCode = response.statusCode();

        if (statusCode == HTTP_OK) {
            final ApiResponse<RefreshTokenResponse> apiResponse = JsonUtils.fromJson(response.body(),
                    JsonUtils.getMapper().getTypeFactory()
                            .constructParametricType(ApiResponse.class, RefreshTokenResponse.class));

            if (apiResponse != null && apiResponse.isSuccess()) {
                LOG.info("刷新令牌成功");
            } else if (apiResponse != null) {
                final String friendlyMessage = ErrorCodeRegistry.getMessage(
                        apiResponse.getCode(), apiResponse.getMessage());
                LOG.warn("刷新令牌失败: code={}, backendMsg={}, displayMsg={}",
                        apiResponse.getCode(), apiResponse.getMessage(), friendlyMessage);
                apiResponse.setMessage(friendlyMessage);
            }

            return apiResponse;
        }

        LOG.error("刷新令牌请求失败: statusCode={}", statusCode);
        return createRefreshTokenErrorResponse(statusCode, ErrorCodeRegistry.getMessage(statusCode));
    }

    private ApiResponse<RefreshTokenResponse> handleRefreshTokenError(final Throwable ex) {
        LOG.error("刷新令牌请求异常", ex);
        return createRefreshTokenErrorResponse(HTTP_SERVER_ERROR, "网络连接失败，请检查网络");
    }

    private ApiResponse<RefreshTokenResponse> createRefreshTokenErrorResponse(final int code, final String message) {
        final ApiResponse<RefreshTokenResponse> response = new ApiResponse<>();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }

    protected ApiResponse<LoginResponse> createErrorResponse(final int code, final String message) {
        final ApiResponse<LoginResponse> response = new ApiResponse<>();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }

    private String maskPhone(final String phone) {
        if (phone == null || phone.length() < PHONE_MIN_LENGTH) {
            return "***";
        }
        return phone.substring(0, PHONE_PREFIX_LENGTH) + "****" + phone.substring(phone.length() - PHONE_SUFFIX_LENGTH);
    }

    /**
     * 异步忘记密码（重置密码）
     *
     * @param request 忘记密码请求
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<Void>> forgotPassword(final ForgotPasswordRequest request) {
        final String url = ClientConfig.getInstance().getBaseUrl() + FORGOT_PASSWORD_PATH;
        final String body = JsonUtils.toJson(request);

        LOG.info("发送忘记密码请求: phone={}", maskPhone(request.getPhone()));

        final HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(ClientConfig.getInstance().getReadTimeout()))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::handleForgotPasswordResponse)
                .exceptionally(this::handleForgotPasswordError);
    }

    @SuppressWarnings("unchecked")
    private ApiResponse<Void> handleForgotPasswordResponse(final HttpResponse<String> response) {
        final int statusCode = response.statusCode();

        if (statusCode == HTTP_OK) {
            final ApiResponse<Void> apiResponse = JsonUtils.fromJson(response.body(),
                    JsonUtils.getMapper().getTypeFactory()
                            .constructParametricType(ApiResponse.class, Void.class));

            if (apiResponse != null && apiResponse.isSuccess()) {
                LOG.info("密码重置成功");
            } else if (apiResponse != null) {
                final String friendlyMessage = ErrorCodeRegistry.getMessage(
                        apiResponse.getCode(), apiResponse.getMessage());
                LOG.warn("密码重置失败: code={}, backendMsg={}, displayMsg={}",
                        apiResponse.getCode(), apiResponse.getMessage(), friendlyMessage);
                apiResponse.setMessage(friendlyMessage);
            }

            return apiResponse;
        }

        LOG.error("忘记密码请求失败: statusCode={}", statusCode);
        return createForgotPasswordErrorResponse(statusCode, ErrorCodeRegistry.getMessage(statusCode));
    }

    private ApiResponse<Void> handleForgotPasswordError(final Throwable ex) {
        LOG.error("忘记密码请求异常", ex);
        return createForgotPasswordErrorResponse(HTTP_SERVER_ERROR, "网络连接失败，请检查网络");
    }

    private ApiResponse<Void> createForgotPasswordErrorResponse(final int code, final String message) {
        final ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }
}
