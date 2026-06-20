package org.example.client.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.example.client.config.ClientConfig;
import org.example.client.model.ApiResponse;
import org.example.client.model.LoginRequest;
import org.example.client.model.LoginResponse;
import org.example.client.model.RegisterRequest;
import org.example.client.model.RegisterResponse;
import org.example.client.model.SmsSendRequest;
import org.example.client.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 认证服务
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class AuthService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);

    private static final AuthService INSTANCE = new AuthService();

    private static final String LOGIN_PATH = "/auth/login";

    /** 注册接口路径 */
    private static final String REGISTER_PATH = "/auth/register";

    /** 发送验证码接口路径 */
    private static final String SMS_SEND_PATH = "/auth/sms/send";

    /** HTTP 状态码 - 成功 */
    private static final int HTTP_OK = 200;

    /** 业务错误码 - 账号或密码错误 */
    private static final int CODE_WRONG_CREDENTIALS = 1004;

    /** 业务错误码 - 账号锁定 */
    private static final int CODE_ACCOUNT_LOCKED = 1005;

    /** 业务错误码 - 手机号已注册 */
    private static final int CODE_PHONE_REGISTERED = 1001;

    /** 业务错误码 - 验证码错误或已过期 */
    private static final int CODE_SMS_INVALID = 1002;

    /** 业务错误码 - 用户名已存在 */
    private static final int CODE_USERNAME_EXISTS = 1003;

    /** HTTP 状态码 - 请求参数错误 */
    private static final int HTTP_BAD_REQUEST = 400;

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
                .thenApply(this::handleResponse)
                .exceptionally(this::handleError);
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
            final ApiResponse<RegisterResponse> apiResponse =
                    JsonUtils.fromJson(response.body(),
                            JsonUtils.getMapper().getTypeFactory()
                                    .constructParametricType(ApiResponse.class, RegisterResponse.class));

            if (apiResponse != null && apiResponse.isSuccess()) {
                LOG.info("注册成功: userId={}",
                        apiResponse.getData().getUser() != null
                                ? apiResponse.getData().getUser().getUserId() : null);
            } else {
                LOG.warn("注册失败: code={}, message={}",
                        apiResponse != null ? apiResponse.getCode() : null,
                        apiResponse != null ? apiResponse.getMessage() : null);
            }

            return apiResponse;
        }

        LOG.error("注册请求失败: statusCode={}", statusCode);
        return createRegisterErrorResponse(statusCode, "网络请求失败");
    }

    private ApiResponse<RegisterResponse> handleRegisterError(final Throwable ex) {
        LOG.error("注册请求异常", ex);
        return createRegisterErrorResponse(HTTP_SERVER_ERROR, "网络连接失败，请检查网络");
    }

    private ApiResponse<RegisterResponse> createRegisterErrorResponse(final int code, final String message) {
        final ApiResponse<RegisterResponse> response = new ApiResponse<>();
        response.setCode(code);
        response.setMessage(mapRegisterErrorMessage(code, message));
        return response;
    }

    private String mapRegisterErrorMessage(final int code, final String defaultMessage) {
        switch (code) {
            case CODE_PHONE_REGISTERED:
                return "手机号已注册";
            case CODE_SMS_INVALID:
                return "验证码错误或已过期";
            case CODE_USERNAME_EXISTS:
                return "用户名已存在";
            case HTTP_BAD_REQUEST:
                return "请求参数错误，请检查输入";
            case HTTP_SERVER_ERROR:
                return "服务器异常，请稍后重试";
            default:
                return defaultMessage;
        }
    }

    @SuppressWarnings("unchecked")
    private ApiResponse<Void> handleSmsResponse(final HttpResponse<String> response) {
        final int statusCode = response.statusCode();

        if (statusCode == HTTP_OK) {
            final ApiResponse<Void> apiResponse =
                    JsonUtils.fromJson(response.body(),
                            JsonUtils.getMapper().getTypeFactory()
                                    .constructParametricType(ApiResponse.class, Void.class));

            if (apiResponse != null && apiResponse.isSuccess()) {
                LOG.info("验证码发送成功");
            } else {
                LOG.warn("验证码发送失败: code={}, message={}",
                        apiResponse != null ? apiResponse.getCode() : null,
                        apiResponse != null ? apiResponse.getMessage() : null);
            }

            return apiResponse;
        }

        LOG.error("验证码请求失败: statusCode={}", statusCode);
        return createSmsErrorResponse(statusCode, "网络请求失败");
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

    private ApiResponse<LoginResponse> handleResponse(final HttpResponse<String> response) {
        final int statusCode = response.statusCode();

        if (statusCode == HTTP_OK) {
            final ApiResponse<LoginResponse> apiResponse =
                    JsonUtils.fromJson(response.body(),
                            JsonUtils.getMapper().getTypeFactory()
                                    .constructParametricType(ApiResponse.class, LoginResponse.class));

            if (apiResponse != null && apiResponse.isSuccess()) {
                LOG.info("登录成功: userId={}", apiResponse.getData().getUser().getUserId());
            } else {
                LOG.warn("登录失败: code={}, message={}",
                        apiResponse != null ? apiResponse.getCode() : null,
                        apiResponse != null ? apiResponse.getMessage() : null);
            }

            return apiResponse;
        }

        LOG.error("登录请求失败: statusCode={}", statusCode);
        return createErrorResponse(statusCode, "网络请求失败");
    }

    private ApiResponse<LoginResponse> handleError(final Throwable ex) {
        LOG.error("登录请求异常", ex);
        return createErrorResponse(HTTP_SERVER_ERROR, "网络连接失败，请检查网络");
    }

    private ApiResponse<LoginResponse> createErrorResponse(final int code, final String message) {
        final ApiResponse<LoginResponse> response = new ApiResponse<>();
        response.setCode(code);
        response.setMessage(mapErrorMessage(code, message));
        return response;
    }

    private String mapErrorMessage(final int code, final String defaultMessage) {
        switch (code) {
            case CODE_WRONG_CREDENTIALS:
                return "账号或密码错误";
            case CODE_ACCOUNT_LOCKED:
                return "登录次数过多，账号已锁定";
            case HTTP_BAD_REQUEST:
                return "请求参数错误，请检查输入";
            case HTTP_SERVER_ERROR:
                return "服务器异常，请稍后重试";
            default:
                return defaultMessage;
        }
    }

    private String maskPhone(final String phone) {
        if (phone == null || phone.length() < PHONE_MIN_LENGTH) {
            return "***";
        }
        return phone.substring(0, PHONE_PREFIX_LENGTH) + "****" + phone.substring(phone.length() - PHONE_SUFFIX_LENGTH);
    }
}

