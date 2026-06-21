package org.example.client.service;

import java.net.http.HttpRequest;
import java.util.concurrent.CompletableFuture;

import org.example.client.model.ApiResponse;
import org.example.client.model.PageResult;
import org.example.client.model.UserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 用户服务
 *
 * <p>封装用户信息查询、修改、搜索等 REST 接口。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class UserService extends BaseHttpService {

    private static final Logger LOG = LoggerFactory.getLogger(UserService.class);

    private static final UserService INSTANCE = new UserService();

    /** 用户信息接口路径 */
    private static final String USER_PROFILE_PATH = "/user/profile";

    /** 用户搜索接口路径 */
    private static final String USER_SEARCH_PATH = "/user/search";

    private UserService() {
        // 单例模式，禁止外部实例化
    }

    public static UserService getInstance() {
        return INSTANCE;
    }

    /**
     * 获取当前用户信息
     *
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<UserInfo>> getProfile() {
        final HttpRequest request = buildGetRequest(USER_PROFILE_PATH).build();

        LOG.info("获取用户信息");

        return sendRequest(request, getTypeFactory().constructParametricType(
                ApiResponse.class, UserInfo.class));
    }

    /**
     * 修改用户信息
     *
     * @param username 用户名
     * @param avatar 头像URL
     * @param bio 个人简介
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<Void>> updateProfile(
            final String username, final String avatar, final String bio) {
        final HttpRequest request = buildPostRequest(USER_PROFILE_PATH,
                java.util.Map.of(
                        "username", username != null ? username : "",
                        "avatar", avatar != null ? avatar : "",
                        "bio", bio != null ? bio : "")).build();

        // PUT 请求需要重新构建
        final String url = org.example.client.config.ClientConfig.getInstance().getBaseUrl()
                + USER_PROFILE_PATH;
        final String json = org.example.client.util.JsonUtils.toJson(
                java.util.Map.of(
                        "username", username != null ? username : "",
                        "avatar", avatar != null ? avatar : "",
                        "bio", bio != null ? bio : ""));
        final HttpRequest putRequest = HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(java.time.Duration.ofSeconds(
                        org.example.client.config.ClientConfig.getInstance().getReadTimeout()))
                .method("PUT", HttpRequest.BodyPublishers.ofString(json != null ? json : "{}"))
                .build();

        LOG.info("修改用户信息: username={}", username);

        return sendRequest(putRequest, getTypeFactory().constructParametricType(
                ApiResponse.class, Void.class));
    }

    /**
     * 搜索用户
     *
     * @param keyword 搜索关键词
     * @param page 页码
     * @param size 每页数量
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<PageResult<UserInfo>>> searchUsers(
            final String keyword, final int page, final int size) {
        final String path = USER_SEARCH_PATH + "?keyword=" + keyword
                + "&page=" + page + "&size=" + size;
        final HttpRequest request = buildGetRequest(path).build();

        LOG.info("搜索用户: keyword={}", keyword);

        return sendRequest(request, getTypeFactory().constructParametricType(
                ApiResponse.class, getTypeFactory().constructParametricType(
                        PageResult.class, UserInfo.class)));
    }
}
