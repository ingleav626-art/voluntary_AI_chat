package org.example.client.service;

import java.util.concurrent.CompletableFuture;

import org.example.client.model.ApiResponse;
import org.example.client.model.ChangePasswordRequest;
import org.example.client.model.ChangePhoneRequest;
import org.example.client.model.LoginResponse;
import org.example.client.model.PageResult;
import org.example.client.model.UserInfo;
import org.example.client.util.TokenStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

    @BeforeEach
    void setUp() {
        // 保存一个假的 LoginResponse，避免 NullPointerException
        final LoginResponse response = new LoginResponse();
        response.setAccessToken("test-token");
        response.setRefreshToken("test-refresh-token");
        response.setExpiresIn(3600L);
        final UserInfo user = new UserInfo();
        user.setUserId(1L);
        user.setUsername("testuser");
        user.setPhone("13800138000");
        response.setUser(user);
        TokenStorage.save(response, false);
    }

    @AfterEach
    void tearDown() {
        TokenStorage.clear();
    }

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

    // ========== 新增方法测试 ==========

    @Test
    @DisplayName("updateProfileFull 返回非空 CompletableFuture")
    void testUpdateProfileFullReturnsFuture() {
        final UserService userService = UserService.getInstance();
        final UserInfo profile = new UserInfo();
        profile.setUsername("张三");
        profile.setBio("程序员");
        profile.setGender(1);
        profile.setAge(25);

        final CompletableFuture<ApiResponse<Void>> future = userService.updateProfileFull(profile);

        assertNotNull(future, "updateProfileFull 应返回非空 Future");
    }

    @Test
    @DisplayName("updateProfileFull 空对象不抛异常")
    void testUpdateProfileFullEmptyObject() {
        final UserService userService = UserService.getInstance();
        final UserInfo profile = new UserInfo();

        assertDoesNotThrow(() ->
                userService.updateProfileFull(profile));
    }

    @Test
    @DisplayName("changePassword 返回非空 CompletableFuture")
    void testChangePasswordReturnsFuture() {
        final UserService userService = UserService.getInstance();
        final ChangePasswordRequest request = new ChangePasswordRequest("123456", "newpass123", "newpass123");

        final CompletableFuture<ApiResponse<Void>> future = userService.changePassword(request);

        assertNotNull(future, "changePassword 应返回非空 Future");
    }

    @Test
    @DisplayName("changePassword null请求不抛异常")
    void testChangePasswordNullRequest() {
        final UserService userService = UserService.getInstance();
        // null 请求会导致 JsonUtils.toJson 返回 null，但不应抛异常
        assertDoesNotThrow(() ->
                userService.changePassword(null));
    }

    @Test
    @DisplayName("changePhone 返回非空 CompletableFuture")
    void testChangePhoneReturnsFuture() {
        final UserService userService = UserService.getInstance();
        final ChangePhoneRequest request = new ChangePhoneRequest("123456", "13900139000", "654321");

        final CompletableFuture<ApiResponse<Void>> future = userService.changePhone(request);

        assertNotNull(future, "changePhone 应返回非空 Future");
    }

    @Test
    @DisplayName("changePhone null请求不抛异常")
    void testChangePhoneNullRequest() {
        final UserService userService = UserService.getInstance();
        assertDoesNotThrow(() ->
                userService.changePhone(null));
    }
}
