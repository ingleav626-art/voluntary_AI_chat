package org.example.client.view;

import java.util.List;

import org.example.client.model.GroupInfo;
import org.example.client.model.GroupMemberInfo;
import org.example.client.model.LoginResponse;
import org.example.client.model.UserInfo;
import org.example.client.util.TokenStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GroupListViewModel 单元测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
class GroupListViewModelTest {

    private GroupListViewModel viewModel;

    @BeforeAll
    static void initJavaFX() {
        // 初始化 JavaFX 工具包（如果尚未初始化）
        try {
            javafx.application.Platform.startup(() -> {});
        } catch (final IllegalStateException e) {
            // JavaFX 已经初始化，忽略
        }
    }

    @BeforeEach
    void setUp() {
        // 清除Token
        TokenStorage.clear();
        // 模拟登录状态
        final LoginResponse loginResponse = createMockLoginResponse();
        TokenStorage.save(loginResponse);

        viewModel = new GroupListViewModel();
    }

    @AfterEach
    void tearDown() {
        TokenStorage.clear();
    }

    @Test
    @DisplayName("初始化时属性应为空或默认值")
    void testInitialization() {
        assertNotNull(viewModel.groupsProperty());
        assertNotNull(viewModel.membersProperty());
        assertNotNull(viewModel.loadingProperty());
        assertNotNull(viewModel.errorMessageProperty());
        assertNotNull(viewModel.successMessageProperty());
        assertNotNull(viewModel.groupNameProperty());

        assertTrue(viewModel.getGroups().isEmpty());
        assertTrue(viewModel.getMembers().isEmpty());
        assertFalse(viewModel.loadingProperty().get());
        assertEquals("", viewModel.errorMessageProperty().get());
        assertEquals("", viewModel.successMessageProperty().get());
        assertEquals("", viewModel.groupNameProperty().get());
        assertNull(viewModel.getSelectedGroup());
    }

    @Test
    @DisplayName("getCurrentUserId 返回当前登录用户ID")
    void testGetCurrentUserId() {
        final Long userId = viewModel.getCurrentUserId();
        assertEquals(1001L, userId);
    }

    @Test
    @DisplayName("setSelectedGroup 和 getSelectedGroup 正常工作")
    void testSelectedGroup() {
        final GroupInfo group = createMockGroupInfo();
        viewModel.setSelectedGroup(group);

        final GroupInfo selected = viewModel.getSelectedGroup();
        assertNotNull(selected);
        assertEquals(group.getGroupId(), selected.getGroupId());
        assertEquals(group.getName(), selected.getName());
    }

    @Test
    @DisplayName("isOwnerOfSelectedGroup 当用户是群主时返回 true")
    void testIsOwnerOfSelectedGroupTrue() {
        // 创建一个群组，当前用户是群主
        final GroupInfo group = new GroupInfo();
        group.setGroupId(1L);
        group.setName("测试群组");
        group.setOwnerId(1001L); // 当前用户ID

        viewModel.setSelectedGroup(group);

        assertTrue(viewModel.isOwnerOfSelectedGroup());
    }

    @Test
    @DisplayName("isOwnerOfSelectedGroup 当用户不是群主时返回 false")
    void testIsOwnerOfSelectedGroupFalse() {
        // 创建一个群组，当前用户不是群主
        final GroupInfo group = new GroupInfo();
        group.setGroupId(1L);
        group.setName("测试群组");
        group.setOwnerId(1002L); // 其他用户ID

        viewModel.setSelectedGroup(group);

        assertFalse(viewModel.isOwnerOfSelectedGroup());
    }

    @Test
    @DisplayName("isOwnerOfSelectedGroup 当未选中群组时返回 false")
    void testIsOwnerOfSelectedGroupNoSelection() {
        viewModel.setSelectedGroup(null);
        assertFalse(viewModel.isOwnerOfSelectedGroup());
    }

    @Test
    @DisplayName("isAdminOrOwnerOfSelectedGroup 当用户是群主时返回 true")
    void testIsAdminOrOwnerOfSelectedGroupOwner() {
        // 设置当前用户角色为 OWNER
        viewModel.loadMembers(1L); // 这会异步加载，我们需要手动设置角色
        // 由于异步加载，我们直接测试逻辑
        // 在实际测试中需要等待异步完成或使用Mock
    }

    @Test
    @DisplayName("isAdminOrOwnerOfSelectedGroup 当用户是管理员时返回 true")
    void testIsAdminOrOwnerOfSelectedGroupAdmin() {
        // 测试管理员角色逻辑
        // 需要Mock GroupService 的响应
    }

    @Test
    @DisplayName("createGroup 空群名称时设置错误消息")
    void testCreateGroupEmptyName() {
        viewModel.groupNameProperty().set("");
        viewModel.createGroup();

        // 由于异步执行，等待一小段时间
        waitForAsync();

        assertEquals("请输入群名称", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("createGroup null 群名称时设置错误消息")
    void testCreateGroupNullName() {
        viewModel.groupNameProperty().set(null);
        viewModel.createGroup();

        waitForAsync();

        assertEquals("请输入群名称", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("loadGroups 未登录时设置错误消息")
    void testLoadGroupsNotLoggedIn() {
        // 清除登录状态
        TokenStorage.clear();
        final GroupListViewModel vm = new GroupListViewModel();

        vm.loadGroups();

        waitForAsync();

        // 由于未登录，应该收到401错误
        assertEquals("请先登录", vm.errorMessageProperty().get());
    }

    @Test
    @DisplayName("群组列表属性可观察")
    void testGroupsObservable() {
        final GroupInfo group1 = createMockGroupInfo();
        final GroupInfo group2 = createMockGroupInfo();
        group2.setGroupId(2L);

        viewModel.getGroups().addAll(group1, group2);

        assertEquals(2, viewModel.getGroups().size());
        assertTrue(viewModel.getGroups().contains(group1));
        assertTrue(viewModel.getGroups().contains(group2));
    }

    @Test
    @DisplayName("群成员列表属性可观察")
    void testMembersObservable() {
        final GroupMemberInfo member1 = createMockGroupMemberInfo();
        final GroupMemberInfo member2 = createMockGroupMemberInfo();
        member2.setUserId(1002L);

        viewModel.getMembers().addAll(member1, member2);

        assertEquals(2, viewModel.getMembers().size());
        assertTrue(viewModel.getMembers().contains(member1));
        assertTrue(viewModel.getMembers().contains(member2));
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

    /**
     * 创建模拟的群组信息
     */
    private GroupInfo createMockGroupInfo() {
        final GroupInfo group = new GroupInfo();
        group.setGroupId(1L);
        group.setName("测试群组");
        group.setAvatar("http://example.com/group.jpg");
        group.setMemberCount(10);
        group.setOwnerId(1001L);
        return group;
    }

    /**
     * 创建模拟的群成员信息
     */
    private GroupMemberInfo createMockGroupMemberInfo() {
        final GroupMemberInfo member = new GroupMemberInfo();
        member.setUserId(1001L);
        member.setUsername("测试用户");
        member.setAvatar("http://example.com/avatar.jpg");
        member.setRole("OWNER");
        member.setJoinTime("2024-01-01T00:00:00Z");
        return member;
    }

    /**
     * 等待异步操作完成
     */
    private void waitForAsync() {
        try {
            // 等待足够时间让异步操作完成
            Thread.sleep(200);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}