package org.example.client.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.example.client.model.ApiResponse;
import org.example.client.model.CreateGroupRequest;
import org.example.client.model.CreateGroupResponse;
import org.example.client.model.GroupInfo;
import org.example.client.model.GroupMemberInfo;
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
 * GroupService 单元测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
class GroupServiceTest {

    private GroupService groupService;

    @BeforeEach
    void setUp() {
        groupService = GroupService.getInstance();
        // 清除Token，确保测试环境干净
        TokenStorage.clear();
    }

    @AfterEach
    void tearDown() {
        TokenStorage.clear();
    }

    @Test
    @DisplayName("getInstance 返回单例")
    void testGetInstance() {
        final GroupService instance1 = GroupService.getInstance();
        final GroupService instance2 = GroupService.getInstance();

        assertNotNull(instance1);
        assertNotNull(instance2);
        assertEquals(instance1, instance2);
    }

    @Test
    @DisplayName("getGroupList 未登录时返回401错误")
    void testGetGroupListNotLoggedIn() {
        // 未登录状态
        TokenStorage.clear();

        final CompletableFuture<ApiResponse<PageResult<GroupInfo>>> future =
                groupService.getGroupList(1, 20);

        assertNotNull(future);

        final ApiResponse<PageResult<GroupInfo>> response = future.join();
        assertNotNull(response);
        assertEquals(401, response.getCode());
        assertEquals("请先登录", response.getMessage());
        assertNull(response.getData());
    }

    @Test
    @DisplayName("getGroupList 已登录时返回非空 Future")
    void testGetGroupListLoggedIn() {
        // 模拟登录状态
        final LoginResponse loginResponse = createMockLoginResponse();
        TokenStorage.save(loginResponse);

        final CompletableFuture<ApiResponse<PageResult<GroupInfo>>> future =
                groupService.getGroupList(1, 20);

        assertNotNull(future);
        // 注意：由于没有真实的服务端，这里只能验证Future不为空
        // 实际测试中需要Mock服务端响应
    }

    @Test
    @DisplayName("createGroup 未登录时返回401错误")
    void testCreateGroupNotLoggedIn() {
        // 未登录状态
        TokenStorage.clear();

        final CreateGroupRequest request = new CreateGroupRequest();
        request.setName("测试群组");
        request.setMemberIds(List.of());

        final CompletableFuture<ApiResponse<CreateGroupResponse>> future =
                groupService.createGroup(request);

        assertNotNull(future);

        final ApiResponse<CreateGroupResponse> response = future.join();
        assertNotNull(response);
        assertEquals(401, response.getCode());
        assertEquals("请先登录", response.getMessage());
        assertNull(response.getData());
    }

    @Test
    @DisplayName("createGroup 已登录时返回非空 Future")
    void testCreateGroupLoggedIn() {
        // 模拟登录状态
        final LoginResponse loginResponse = createMockLoginResponse();
        TokenStorage.save(loginResponse);

        final CreateGroupRequest request = new CreateGroupRequest();
        request.setName("测试群组");
        request.setMemberIds(List.of(1002L, 1003L));

        final CompletableFuture<ApiResponse<CreateGroupResponse>> future =
                groupService.createGroup(request);

        assertNotNull(future);
    }

    @Test
    @DisplayName("getGroupMembers 返回非空 Future")
    void testGetGroupMembers() {
        final CompletableFuture<ApiResponse<PageResult<GroupMemberInfo>>> future =
                groupService.getGroupMembers(1L, 1, 50);

        assertNotNull(future);
    }

    @Test
    @DisplayName("inviteMembers 返回非空 Future")
    void testInviteMembers() {
        final CompletableFuture<ApiResponse<Void>> future =
                groupService.inviteMembers(1L, List.of(1002L, 1003L));

        assertNotNull(future);
    }

    @Test
    @DisplayName("removeMember 返回非空 Future")
    void testRemoveMember() {
        final CompletableFuture<ApiResponse<Void>> future =
                groupService.removeMember(1L, 1002L);

        assertNotNull(future);
    }

    @Test
    @DisplayName("leaveGroup 返回非空 Future")
    void testLeaveGroup() {
        final CompletableFuture<ApiResponse<Void>> future =
                groupService.leaveGroup(1L);

        assertNotNull(future);
    }

    @Test
    @DisplayName("transferOwner 返回非空 Future")
    void testTransferOwner() {
        final CompletableFuture<ApiResponse<Void>> future =
                groupService.transferOwner(1L, 1002L);

        assertNotNull(future);
    }

    @Test
    @DisplayName("dismissGroup 返回非空 Future")
    void testDismissGroup() {
        final CompletableFuture<ApiResponse<Void>> future =
                groupService.dismissGroup(1L);

        assertNotNull(future);
    }

    @Test
    @DisplayName("setAdmin 返回非空 Future")
    void testSetAdmin() {
        final CompletableFuture<ApiResponse<Void>> future =
                groupService.setAdmin(1L, 1002L, "SET");

        assertNotNull(future);
    }

    @Test
    @DisplayName("updateGroup 返回非空 Future")
    void testUpdateGroup() {
        final CompletableFuture<ApiResponse<Void>> future =
                groupService.updateGroup(1L, "新群名", "新公告", true);

        assertNotNull(future);
    }

    @Test
    @DisplayName("setNickname 返回非空 Future")
    void testSetNickname() {
        final CompletableFuture<ApiResponse<Void>> future =
                groupService.setNickname(1L, "我的昵称");

        assertNotNull(future);
    }

    /**
     * 创建模拟的登录响应
     */
    private LoginResponse createMockLoginResponse() {
        final LoginResponse response = new LoginResponse();
        response.setAccessToken("mock-access-token");
        response.setRefreshToken("mock-refresh-token");
        response.setExpiresIn(7200L);

        final UserInfo user = new UserInfo();
        user.setUserId(1001L);
        user.setUsername("测试用户");
        user.setPhone("138****8000");
        user.setAvatar("http://example.com/avatar.jpg");
        response.setUser(user);

        return response;
    }
}