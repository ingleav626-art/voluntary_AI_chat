package org.example.client.service;

import java.net.http.HttpRequest;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.example.client.model.ApiResponse;
import org.example.client.model.FriendApplyHandleRequest;
import org.example.client.model.FriendApplyRequest;
import org.example.client.model.FriendApplyResponse;
import org.example.client.model.FriendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 好友服务
 *
 * <p>封装好友申请、好友列表、删除好友等 REST 接口。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class FriendService extends BaseHttpService {

    private static final Logger LOG = LoggerFactory.getLogger(FriendService.class);

    private static final FriendService INSTANCE = new FriendService();

    /** 好友模块基础路径 */
    private static final String FRIEND_PATH = "/friend";

    /** 好友申请接口路径 */
    private static final String FRIEND_APPLY_PATH = FRIEND_PATH + "/apply";

    /** 好友申请列表接口路径 */
    private static final String FRIEND_APPLY_LIST_PATH = FRIEND_PATH + "/apply/list";

    /** 好友列表接口路径 */
    private static final String FRIEND_LIST_PATH = FRIEND_PATH + "/list";

    /** 处理动作 - 同意 */
    private static final String ACTION_ACCEPT = "ACCEPT";

    private FriendService() {
        // 单例模式，禁止外部实例化
    }

    public static FriendService getInstance() {
        return INSTANCE;
    }

    /**
     * 发送好友申请
     *
     * @param request 申请请求
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<Void>> applyFriend(final FriendApplyRequest request) {
        final HttpRequest httpRequest = buildPostRequest(FRIEND_APPLY_PATH, request).build();

        LOG.info("发送好友申请: targetPhone={}", request.getTargetPhone());

        return sendRequest(httpRequest, getTypeFactory().constructParametricType(
                ApiResponse.class, Void.class));
    }

    /**
     * 获取好友申请列表
     *
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<List<FriendApplyResponse>>> getApplyList() {
        final HttpRequest request = buildGetRequest(FRIEND_APPLY_LIST_PATH).build();

        LOG.info("获取好友申请列表");

        return sendRequest(request, getTypeFactory().constructParametricType(
                ApiResponse.class, getTypeFactory().constructCollectionType(
                        List.class, FriendApplyResponse.class)));
    }

    /**
     * 处理好友申请
     *
     * @param applyId 申请ID
     * @param action  处理动作：ACCEPT / REJECT
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<Void>> handleApply(final Long applyId, final String action) {
        final String path = FRIEND_APPLY_PATH + "/" + applyId + "/handle";
        final FriendApplyHandleRequest request = new FriendApplyHandleRequest(action);
        final HttpRequest httpRequest = buildPostRequest(path, request).build();

        LOG.info("处理好友申请: applyId={}, action={}", applyId, action);

        return sendRequest(httpRequest, getTypeFactory().constructParametricType(
                ApiResponse.class, Void.class));
    }

    /**
     * 同意好友申请
     *
     * @param applyId 申请ID
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<Void>> acceptApply(final Long applyId) {
        return handleApply(applyId, ACTION_ACCEPT);
    }

    /**
     * 拒绝好友申请
     *
     * @param applyId 申请ID
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<Void>> rejectApply(final Long applyId) {
        return handleApply(applyId, "REJECT");
    }

    /**
     * 获取好友列表
     *
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<List<FriendResponse>>> getFriendList() {
        final HttpRequest request = buildGetRequest(FRIEND_LIST_PATH).build();

        LOG.info("获取好友列表");

        return sendRequest(request, getTypeFactory().constructParametricType(
                ApiResponse.class, getTypeFactory().constructCollectionType(
                        List.class, FriendResponse.class)));
    }

    /**
     * 删除好友
     *
     * @param friendId 好友ID
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<Void>> deleteFriend(final Long friendId) {
        final String path = FRIEND_PATH + "/" + friendId;
        final HttpRequest httpRequest = buildDeleteRequest(path).build();

        LOG.info("删除好友: friendId={}", friendId);

        return sendRequest(httpRequest, getTypeFactory().constructParametricType(
                ApiResponse.class, Void.class));
    }
}
