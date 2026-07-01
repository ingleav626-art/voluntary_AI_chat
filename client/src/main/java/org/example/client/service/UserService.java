package org.example.client.service;

import java.net.http.HttpRequest;
import java.util.concurrent.CompletableFuture;

import org.example.client.model.ApiResponse;
import org.example.client.model.ChangePasswordRequest;
import org.example.client.model.ChangePhoneRequest;
import org.example.client.model.PageResult;
import org.example.client.model.UserInfo;
import org.example.client.util.TokenStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 用户服务
 *
 * <p>
 * 封装用户信息查询、修改、搜索等 REST 接口。
 * </p>
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

        /** 修改密码接口路径 */
        private static final String USER_PASSWORD_PATH = "/user/password";

        /** 发送密码修改验证码接口路径 */
        private static final String USER_PASSWORD_SMS_PATH = "/user/password/sms";

        /** 修改手机号接口路径 */
        private static final String USER_PHONE_PATH = "/user/phone";

        /** 头像上传接口路径 */
        private static final String AVATAR_UPLOAD_PATH = "/file/upload/avatar";

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
         * @param avatar   头像URL
         * @param bio      个人简介
         * @return 异步结果
         */
        public CompletableFuture<ApiResponse<Void>> updateProfile(
                        final String username, final String avatar, final String bio) {
                final HttpRequest request = buildPostRequest(USER_PROFILE_PATH,
                                java.util.Map.of(
                                                "username", username != null ? username : "",
                                                "avatar", avatar != null ? avatar : "",
                                                "bio", bio != null ? bio : ""))
                                .build();

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
         * @param page    页码
         * @param size    每页数量
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

        /**
         * 修改用户信息（完整版）
         *
         * @param profile 用户信息对象
         * @return 异步结果
         */
        public CompletableFuture<ApiResponse<Void>> updateProfileFull(final UserInfo profile) {
                final String url = org.example.client.config.ClientConfig.getInstance().getBaseUrl()
                                + USER_PROFILE_PATH;

                // 构建请求体，只包含非空字段
                final java.util.Map<String, Object> body = new java.util.HashMap<>();
                if (profile.getUsername() != null) {
                        body.put("username", profile.getUsername());
                }
                if (profile.getAvatar() != null) {
                        body.put("avatar", profile.getAvatar());
                }
                if (profile.getBio() != null) {
                        body.put("bio", profile.getBio());
                }
                if (profile.getGender() != null) {
                        body.put("gender", profile.getGender());
                }
                if (profile.getAge() != null) {
                        body.put("age", profile.getAge());
                }
                if (profile.getBirthday() != null) {
                        body.put("birthday", profile.getBirthday().toString());
                }
                if (profile.getDetailBio() != null) {
                        body.put("detailBio", profile.getDetailBio());
                }

                final String json = org.example.client.util.JsonUtils.toJson(body);
                final HttpRequest putRequest = HttpRequest.newBuilder()
                                .uri(java.net.URI.create(url))
                                .header("Content-Type", "application/json")
                                .header("Authorization", "Bearer " + TokenStorage.load().getAccessToken())
                                .timeout(java.time.Duration.ofSeconds(
                                                org.example.client.config.ClientConfig.getInstance().getReadTimeout()))
                                .method("PUT", HttpRequest.BodyPublishers.ofString(json != null ? json : "{}"))
                                .build();

                LOG.info("修改用户信息（完整版）: username={}", profile.getUsername());

                return sendRequest(putRequest, getTypeFactory().constructParametricType(
                                ApiResponse.class, Void.class));
        }

        /**
         * 发送密码修改验证码（自动获取当前用户手机号）
         *
         * @return 异步结果
         */
        public CompletableFuture<ApiResponse<Void>> sendPasswordSms() {
                final String url = org.example.client.config.ClientConfig.getInstance().getBaseUrl()
                                + USER_PASSWORD_SMS_PATH;

                final HttpRequest postRequest = HttpRequest.newBuilder()
                                .uri(java.net.URI.create(url))
                                .header("Content-Type", "application/json")
                                .header("Authorization", "Bearer " + TokenStorage.load().getAccessToken())
                                .timeout(java.time.Duration.ofSeconds(
                                                org.example.client.config.ClientConfig.getInstance().getReadTimeout()))
                                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                                .build();

                LOG.info("发送密码修改验证码");

                return sendRequest(postRequest, getTypeFactory().constructParametricType(
                                ApiResponse.class, Void.class));
        }

        /**
         * 修改密码
         *
         * @param request 修改密码请求
         * @return 异步结果
         */
        public CompletableFuture<ApiResponse<Void>> changePassword(final ChangePasswordRequest request) {
                final String url = org.example.client.config.ClientConfig.getInstance().getBaseUrl()
                                + USER_PASSWORD_PATH;

                final String json = org.example.client.util.JsonUtils.toJson(request);
                final HttpRequest putRequest = HttpRequest.newBuilder()
                                .uri(java.net.URI.create(url))
                                .header("Content-Type", "application/json")
                                .header("Authorization", "Bearer " + TokenStorage.load().getAccessToken())
                                .timeout(java.time.Duration.ofSeconds(
                                                org.example.client.config.ClientConfig.getInstance().getReadTimeout()))
                                .method("PUT", HttpRequest.BodyPublishers.ofString(json != null ? json : "{}"))
                                .build();

                LOG.info("修改密码");

                return sendRequest(putRequest, getTypeFactory().constructParametricType(
                                ApiResponse.class, Void.class));
        }

        /**
         * 修改手机号
         *
         * @param request 修改手机号请求
         * @return 异步结果
         */
        public CompletableFuture<ApiResponse<Void>> changePhone(final ChangePhoneRequest request) {
                final String url = org.example.client.config.ClientConfig.getInstance().getBaseUrl()
                                + USER_PHONE_PATH;

                final String json = org.example.client.util.JsonUtils.toJson(request);
                final HttpRequest putRequest = HttpRequest.newBuilder()
                                .uri(java.net.URI.create(url))
                                .header("Content-Type", "application/json")
                                .header("Authorization", "Bearer " + TokenStorage.load().getAccessToken())
                                .timeout(java.time.Duration.ofSeconds(
                                                org.example.client.config.ClientConfig.getInstance().getReadTimeout()))
                                .method("PUT", HttpRequest.BodyPublishers.ofString(json != null ? json : "{}"))
                                .build();

                LOG.info("修改手机号: newPhone={}", request != null ? request.getNewPhone() : "null");

                return sendRequest(putRequest, getTypeFactory().constructParametricType(
                                ApiResponse.class, Void.class));
        }

        /**
         * 上传用户头像
         *
         * <p>
         * 调用专用头像上传接口 {@code POST /api/file/upload/avatar}，返回头像相对路径 URL。
         * </p>
         *
         * @param filePath 图片文件路径
         * @return 异步结果，data 为头像相对路径 URL（如 /avatars/xxx.jpg）
         */
        public CompletableFuture<ApiResponse<String>> uploadAvatar(final java.nio.file.Path filePath) {
                LOG.info("【头像上传-UserService】开始上传头像: filePath={}", filePath);
                try {
                        final byte[] fileBytes = java.nio.file.Files.readAllBytes(filePath);
                        LOG.info("【头像上传-UserService】文件读取成功: size={}", fileBytes.length);
                        final String boundary = "----AvatarBoundary" + System.currentTimeMillis();
                        final String url = org.example.client.config.ClientConfig.getInstance().getBaseUrl()
                                        + AVATAR_UPLOAD_PATH;
                        LOG.info("【头像上传-UserService】构建请求 URL: {}", url);

                        // 探测 MIME 类型
                        String contentType = java.nio.file.Files.probeContentType(filePath);
                        if (contentType == null || contentType.isEmpty()) {
                                final String fileName = filePath.getFileName().toString().toLowerCase();
                                if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                                        contentType = "image/jpeg";
                                } else if (fileName.endsWith(".gif")) {
                                        contentType = "image/gif";
                                } else if (fileName.endsWith(".webp")) {
                                        contentType = "image/webp";
                                } else {
                                        contentType = "image/png";
                                }
                        }

                        // 构建 multipart/form-data 请求体
                        LOG.info("【头像上传-UserService】构建 multipart/form-data 请求体: contentType={}", contentType);
                        final StringBuilder bodyBuilder = new StringBuilder();
                        bodyBuilder.append("--").append(boundary).append("\r\n");
                        bodyBuilder.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                                        .append(filePath.getFileName().toString()).append("\"\r\n");
                        bodyBuilder.append("Content-Type: ").append(contentType).append("\r\n\r\n");

                        final byte[] headerBytes = bodyBuilder.toString()
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        final byte[] footerBytes = ("\r\n--" + boundary + "--\r\n")
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);

                        final byte[] multipartBody = new byte[headerBytes.length + fileBytes.length
                                        + footerBytes.length];
                        System.arraycopy(headerBytes, 0, multipartBody, 0, headerBytes.length);
                        System.arraycopy(fileBytes, 0, multipartBody, headerBytes.length, fileBytes.length);
                        System.arraycopy(footerBytes, 0, multipartBody,
                                        headerBytes.length + fileBytes.length, footerBytes.length);

                        final HttpRequest httpRequest = HttpRequest.newBuilder()
                                        .uri(java.net.URI.create(url))
                                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                                        .header("Authorization", "Bearer " + TokenStorage.load().getAccessToken())
                                        .timeout(java.time.Duration.ofSeconds(30))
                                        .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                                        .build();

                        LOG.info("【头像上传-UserService】发送 HTTP 请求: url={}, fileSize={}", url, fileBytes.length);

                        final CompletableFuture<ApiResponse<String>> result = sendRequest(httpRequest,
                                        getTypeFactory().constructParametricType(
                                                        ApiResponse.class, String.class));

                        result.thenAccept(response -> {
                                LOG.info("【头像上传-UserService】收到响应: success={}, message={}, data={}",
                                                response != null ? response.isSuccess() : "null",
                                                response != null ? response.getMessage() : "null",
                                                response != null && response.getData() != null ? response.getData()
                                                                : "null");
                        }).exceptionally(ex -> {
                                LOG.error("【头像上传-UserService】请求失败: {}", ex.getMessage(), ex);
                                return null;
                        });

                        return result;
                } catch (final java.io.IOException e) {
                        LOG.error("读取头像文件失败: {}", e.getMessage(), e);
                        return CompletableFuture.failedFuture(e);
                }
        }
}
