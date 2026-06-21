package org.example.client.service;

import java.util.concurrent.CompletableFuture;

import org.example.client.model.ApiResponse;
import org.example.client.model.PageResult;
import org.example.client.model.UserInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserService 单元测试
 *
 * <p>测试单例模式、方法返回值非空、参数边界等行为。
 * HTTP 请求部分需要集成测试或 Mock。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("UserService 测试")
class UserServiceTest {

    @Test
    @DisplayName("获取单例实例 - 同一实例")
    void testGetInstance() {
        final UserService instance1 = UserService.getInstance();
        final UserService instance2 = UserService.getInstance();

        assertNotNull(instance1);
        assertSame(instance1, instance2, "单例应返回同一实例");
    }

    @Test
    @DisplayName("getProfile 返回非空 CompletableFuture")
    void testGetProfileReturnsFuture() {
        final UserService userService = UserService.getInstance();
        final CompletableFuture<ApiResponse<UserInfo>> future = userService.getProfile();

        assertNotNull(future, "getProfile 应返回非空 Future");
    }

    @Test
    @DisplayName("updateProfile 正常参数返回非空 Future")
    void testUpdateProfileNormal() {
        final UserService userService = UserService.getInstance();
        final CompletableFuture<ApiResponse<Void>> future =
                userService.updateProfile("张三", "http://avatar.jpg", "程序员");

        assertNotNull(future, "updateProfile 应返回非空 Future");
    }

    @Test
    @DisplayName("updateProfile null 参数不抛异常")
    void testUpdateProfileNullParams() {
        final UserService userService = UserService.getInstance();
        // null 参数应被转换为空字符串，不应抛出异常
        assertDoesNotThrow(() ->
                userService.updateProfile(null, null, null));
    }

    @Test
    @DisplayName("searchUsers 返回非空 CompletableFuture")
    void testSearchUsersReturnsFuture() {
        final UserService userService = UserService.getInstance();
        final CompletableFuture<ApiResponse<PageResult<UserInfo>>> future =
                userService.searchUsers("张三", 1, 20);

        assertNotNull(future, "searchUsers 应返回非空 Future");
    }

    @Test
    @DisplayName("searchUsers 空关键词不抛异常")
    void testSearchUsersEmptyKeyword() {
        final UserService userService = UserService.getInstance();
        assertDoesNotThrow(() ->
                userService.searchUsers("", 1, 20));
    }

    @Test
    @DisplayName("searchUsers 第0页不抛异常")
    void testSearchUsersZeroPage() {
        final UserService userService = UserService.getInstance();
        assertDoesNotThrow(() ->
                userService.searchUsers("张三", 0, 20));
    }
}
