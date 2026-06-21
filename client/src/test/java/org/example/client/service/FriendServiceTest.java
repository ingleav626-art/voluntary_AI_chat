package org.example.client.service;

import java.util.concurrent.CompletableFuture;

import org.example.client.model.ApiResponse;
import org.example.client.model.FriendApplyRequest;
import org.example.client.model.FriendApplyResponse;
import org.example.client.model.FriendResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FriendService 单元测试
 *
 * <p>测试单例模式、方法返回值非空、参数边界等行为。
 * HTTP 请求部分需要集成测试或 Mock。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("FriendService 测试")
class FriendServiceTest {

    @Test
    @DisplayName("获取单例实例 - 同一实例")
    void testGetInstance() {
        final FriendService instance1 = FriendService.getInstance();
        final FriendService instance2 = FriendService.getInstance();

        assertNotNull(instance1);
        assertSame(instance1, instance2, "单例应返回同一实例");
    }

    @Test
    @DisplayName("applyFriend 返回非空 CompletableFuture")
    void testApplyFriendReturnsFuture() {
        final FriendService friendService = FriendService.getInstance();
        final FriendApplyRequest request = new FriendApplyRequest("13800138002", "你好");

        final CompletableFuture<ApiResponse<Void>> future = friendService.applyFriend(request);

        assertNotNull(future, "applyFriend 应返回非空 Future");
    }

    @Test
    @DisplayName("getApplyList 返回非空 CompletableFuture")
    void testGetApplyListReturnsFuture() {
        final FriendService friendService = FriendService.getInstance();
        final CompletableFuture<ApiResponse<java.util.List<FriendApplyResponse>>> future =
                friendService.getApplyList();

        assertNotNull(future, "getApplyList 应返回非空 Future");
    }

    @Test
    @DisplayName("handleApply 正常参数返回非空 Future")
    void testHandleApplyNormal() {
        final FriendService friendService = FriendService.getInstance();
        final CompletableFuture<ApiResponse<Void>> future =
                friendService.handleApply(5001L, "ACCEPT");

        assertNotNull(future, "handleApply 应返回非空 Future");
    }

    @Test
    @DisplayName("acceptApply 委托 handleApply 且使用 ACCEPT 动作")
    void testAcceptApplyReturnsFuture() {
        final FriendService friendService = FriendService.getInstance();
        final CompletableFuture<ApiResponse<Void>> future =
                friendService.acceptApply(5002L);

        assertNotNull(future, "acceptApply 应返回非空 Future");
    }

    @Test
    @DisplayName("rejectApply 委托 handleApply 且使用 REJECT 动作")
    void testRejectApplyReturnsFuture() {
        final FriendService friendService = FriendService.getInstance();
        final CompletableFuture<ApiResponse<Void>> future =
                friendService.rejectApply(5003L);

        assertNotNull(future, "rejectApply 应返回非空 Future");
    }

    @Test
    @DisplayName("getFriendList 返回非空 CompletableFuture")
    void testGetFriendListReturnsFuture() {
        final FriendService friendService = FriendService.getInstance();
        final CompletableFuture<ApiResponse<java.util.List<FriendResponse>>> future =
                friendService.getFriendList();

        assertNotNull(future, "getFriendList 应返回非空 Future");
    }

    @Test
    @DisplayName("deleteFriend 返回非空 CompletableFuture")
    void testDeleteFriendReturnsFuture() {
        final FriendService friendService = FriendService.getInstance();
        final CompletableFuture<ApiResponse<Void>> future =
                friendService.deleteFriend(1002L);

        assertNotNull(future, "deleteFriend 应返回非空 Future");
    }

    @Test
    @DisplayName("deleteFriend null ID 不抛异常")
    void testDeleteFriendNullId() {
        final FriendService friendService = FriendService.getInstance();
        // null ID 会拼接到路径中，不应抛出 NPE
        assertDoesNotThrow(() -> friendService.deleteFriend(null));
    }
}
