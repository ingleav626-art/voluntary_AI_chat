package org.example.client.service;

import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import org.example.client.config.ClientConfig;
import org.example.client.model.ApiResponse;
import org.example.client.model.ConversationInfo;
import org.example.client.model.ImageUploadResponse;
import org.example.client.model.MarkReadRequest;
import org.example.client.model.MessageInfo;
import org.example.client.model.PageResult;
import org.example.client.model.SendMessageRequest;
import org.example.client.model.SendMessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 会话与消息服务
 *
 * <p>
 * 封装会话列表、聊天记录、消息发送、已读上报、撤回等 REST 接口。
 * </p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class ChatService extends BaseHttpService {

        private static final Logger LOG = LoggerFactory.getLogger(ChatService.class);

        private static final ChatService INSTANCE = new ChatService();

        /** 会话列表接口路径 */
        private static final String CONVERSATION_LIST_PATH = "/conversation/list";

        /** 聊天记录接口路径 */
        private static final String MESSAGE_HISTORY_PATH = "/message/history";

        /** 发送消息接口路径 */
        private static final String MESSAGE_SEND_PATH = "/message/send";

        /** 消息已读接口路径 */
        private static final String MESSAGE_READ_PATH = "/message/read";

        /** 消息撤回接口路径 */
        private static final String MESSAGE_RECALL_PATH = "/message/recall";

        /** 图片上传接口路径 */
        private static final String MESSAGE_UPLOAD_IMAGE_PATH = "/message/upload/image";

        /** 默认每页数量 */
        private static final int DEFAULT_PAGE_SIZE = 20;

        private ChatService() {
                // 单例模式，禁止外部实例化
        }

        public static ChatService getInstance() {
                return INSTANCE;
        }

        /**
         * 获取会话列表（支持搜索）
         *
         * @param keyword 搜索关键词，为空返回全部
         * @param page    页码
         * @param size    每页数量
         * @return 异步结果
         */
        public CompletableFuture<ApiResponse<PageResult<ConversationInfo>>> getConversations(
                        final String keyword, final int page, final int size) {
                final StringBuilder path = new StringBuilder(CONVERSATION_LIST_PATH)
                                .append("?page=").append(page)
                                .append("&size=").append(size);
                if (keyword != null && !keyword.isBlank()) {
                        path.append("&keyword=").append(java.net.URLEncoder.encode(keyword.trim(),
                                        java.nio.charset.StandardCharsets.UTF_8));
                }
                final HttpRequest request = buildGetRequest(path.toString()).build();

                LOG.info("获取会话列表: keyword={}, page={}, size={}", keyword, page, size);

                return sendRequest(request, getTypeFactory().constructParametricType(
                                ApiResponse.class, getTypeFactory().constructParametricType(
                                                PageResult.class, ConversationInfo.class)));
        }

        /**
         * 获取会话列表
         *
         * @param page 页码
         * @param size 每页数量
         * @return 异步结果
         */
        public CompletableFuture<ApiResponse<PageResult<ConversationInfo>>> getConversations(
                        final int page, final int size) {
                return getConversations(null, page, size);
        }

        /**
         * 获取会话列表（默认分页，支持搜索）
         *
         * @param keyword 搜索关键词，为空返回全部
         * @return 异步结果
         */
        public CompletableFuture<ApiResponse<PageResult<ConversationInfo>>> getConversations(
                        final String keyword) {
                return getConversations(keyword, 1, DEFAULT_PAGE_SIZE);
        }

        /**
         * 获取聊天记录
         *
         * @param sessionId 会话ID
         * @param page      页码
         * @param size      每页数量
         * @return 异步结果
         */
        public CompletableFuture<ApiResponse<PageResult<MessageInfo>>> getHistory(
                        final String sessionId, final int page, final int size) {
                final String path = MESSAGE_HISTORY_PATH + "?sessionId=" + sessionId
                                + "&page=" + page + "&size=" + size;
                final HttpRequest request = buildGetRequest(path).build();

                LOG.info("获取聊天记录: sessionId={}, page={}, size={}", sessionId, page, size);

                return sendRequest(request, getTypeFactory().constructParametricType(
                                ApiResponse.class, getTypeFactory().constructParametricType(
                                                PageResult.class, MessageInfo.class)));
        }

        /**
         * 发送消息（REST 备用通道）
         *
         * @param request 发送请求
         * @return 异步结果
         */
        public CompletableFuture<ApiResponse<SendMessageResponse>> sendMessage(
                        final SendMessageRequest request) {
                final HttpRequest httpRequest = buildPostRequest(MESSAGE_SEND_PATH, request).build();

                LOG.info("发送消息: sessionId={}", request.getSessionId());

                return sendRequest(httpRequest, getTypeFactory().constructParametricType(
                                ApiResponse.class, SendMessageResponse.class));
        }

        /**
         * 消息已读上报
         *
         * @param request 已读请求
         * @return 异步结果
         */
        public CompletableFuture<ApiResponse<Void>> markRead(final MarkReadRequest request) {
                final HttpRequest httpRequest = buildPostRequest(MESSAGE_READ_PATH, request).build();

                LOG.info("消息已读上报: sessionId={}, count={}",
                                request.getSessionId(),
                                request.getMessageIds() != null ? request.getMessageIds().size() : 0);

                return sendRequest(httpRequest, getTypeFactory().constructParametricType(
                                ApiResponse.class, Void.class));
        }

        /**
         * 撤回消息
         *
         * @param messageId 消息ID
         * @return 异步结果
         */
        public CompletableFuture<ApiResponse<Void>> recallMessage(final Long messageId) {
                final HttpRequest httpRequest = buildPostRequest(
                                MESSAGE_RECALL_PATH, java.util.Map.of("messageId", String.valueOf(messageId))).build();

                return sendRequest(httpRequest, getTypeFactory().constructParametricType(
                                ApiResponse.class, Void.class));
        }

        /**
         * 获取会话列表（默认每页20条）
         *
         * @return 异步结果
         */
        public CompletableFuture<ApiResponse<PageResult<ConversationInfo>>> getConversations() {
                return getConversations(1, DEFAULT_PAGE_SIZE);
        }

        /**
         * 上传图片
         * 使用 multipart/form-data 格式上传，服务端返回图片URL
         *
         * @param filePath 图片文件路径
         * @return 异步结果，包含图片URL等信息
         */
        public CompletableFuture<ApiResponse<ImageUploadResponse>> uploadImage(final Path filePath) {
                try {
                        final byte[] fileBytes = Files.readAllBytes(filePath);
                        final String boundary = "----Boundary" + System.currentTimeMillis();
                        final String url = ClientConfig.getInstance().getBaseUrl() + MESSAGE_UPLOAD_IMAGE_PATH;

                        // 构建 multipart/form-data 请求体
                        final StringBuilder sb = new StringBuilder();
                        sb.append("--").append(boundary).append("\r\n");
                        sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                                        .append(filePath.getFileName().toString()).append("\"\r\n");
                        sb.append("Content-Type: ").append(Files.probeContentType(filePath)).append("\r\n\r\n");

                        final byte[] header = sb.toString().getBytes();
                        final byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes();

                        final java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                        bos.write(header);
                        bos.write(fileBytes);
                        bos.write(footer);
                        final byte[] body = bos.toByteArray();

                        final HttpRequest.Builder builder = HttpRequest.newBuilder()
                                        .uri(java.net.URI.create(url))
                                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                                        .timeout(java.time.Duration
                                                        .ofSeconds(ClientConfig.getInstance().getReadTimeout()))
                                        .POST(HttpRequest.BodyPublishers.ofByteArray(body));

                        // 添加认证头
                        final org.example.client.model.LoginResponse loginResponse = org.example.client.util.TokenStorage
                                        .load();
                        if (loginResponse != null && loginResponse.getAccessToken() != null
                                        && !loginResponse.getAccessToken().isEmpty()) {
                                builder.header("Authorization", "Bearer " + loginResponse.getAccessToken());
                        }

                        final HttpRequest request = builder.build();
                        LOG.info("上传图片: fileName={}", filePath.getFileName());

                        return sendRequest(request, getTypeFactory().constructParametricType(
                                        ApiResponse.class, ImageUploadResponse.class));
                } catch (final java.io.IOException e) {
                        LOG.error("读取图片文件失败: {}", filePath, e);
                        final CompletableFuture<ApiResponse<ImageUploadResponse>> failed = new CompletableFuture<>();
                        failed.completeExceptionally(e);
                        return failed;
                }
        }
}
