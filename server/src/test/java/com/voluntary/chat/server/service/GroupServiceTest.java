package com.voluntary.chat.server.service;

import com.voluntary.chat.common.dto.PageResult;
import com.voluntary.chat.common.exception.BusinessException;
import com.voluntary.chat.common.exception.ErrorCode;
import com.voluntary.chat.server.dto.request.CreateGroupRequest;
import com.voluntary.chat.server.dto.request.InviteMemberRequest;
import com.voluntary.chat.server.dto.request.UpdateGroupRequest;
import com.voluntary.chat.server.dto.response.CreateGroupResponse;
import com.voluntary.chat.server.dto.response.GroupMemberResponse;
import com.voluntary.chat.server.dto.response.GroupResponse;
import com.voluntary.chat.server.entity.GroupEntity;
import com.voluntary.chat.server.entity.GroupMember;
import com.voluntary.chat.server.entity.User;
import com.voluntary.chat.server.mapper.GroupMapper;
import com.voluntary.chat.server.mapper.GroupMemberMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * GroupService 单元测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GroupService 单元测试")
class GroupServiceTest {

    @Mock
    private GroupMapper groupMapper;

    @Mock
    private GroupMemberMapper groupMemberMapper;

    @Mock
    private UserService userService;

    @InjectMocks
    private GroupService groupService;

    private User mockUser1;
    private User mockUser2;
    private User mockUser3;

    private static final long OWNER_ID = 1001L;
    private static final long MEMBER_ID_1 = 1002L;
    private static final long MEMBER_ID_2 = 1003L;
    private static final long GROUP_ID = 2001L;

    @BeforeEach
    void setUp() {
        mockUser1 = new User();
        mockUser1.setId(OWNER_ID);
        mockUser1.setUsername("张三");
        mockUser1.setAvatar("http://example.com/avatar1.jpg");

        mockUser2 = new User();
        mockUser2.setId(MEMBER_ID_1);
        mockUser2.setUsername("李四");
        mockUser2.setAvatar("http://example.com/avatar2.jpg");

        mockUser3 = new User();
        mockUser3.setId(MEMBER_ID_2);
        mockUser3.setUsername("王五");
        mockUser3.setAvatar("http://example.com/avatar3.jpg");
    }

    // ==================== 创建群组 ====================

    @Test
    @DisplayName("创建群组成功")
    void createGroup_shouldSucceed() {
        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("技术交流群");
        request.setMemberIds(List.of(MEMBER_ID_1, MEMBER_ID_2));

        when(userService.findByIds(Set.of(MEMBER_ID_1, MEMBER_ID_2)))
                .thenReturn(Map.of(MEMBER_ID_1, mockUser2, MEMBER_ID_2, mockUser3));
        when(groupMapper.insert(any(GroupEntity.class))).thenAnswer(invocation -> {
            GroupEntity saved = invocation.getArgument(0);
            saved.setId(GROUP_ID);
            return 1;
        });

        CreateGroupResponse result = groupService.createGroup(OWNER_ID, request);

        assertEquals(GROUP_ID, result.getGroupId());
        assertEquals("技术交流群", result.getName());
        // 验证插入了3条记录（群主 + 2个成员）
        verify(groupMemberMapper, times(3)).insert(any(GroupMember.class));
    }

    @Test
    @DisplayName("创建群组-成员不存在时抛出异常")
    void createGroup_shouldThrow_whenMemberNotFound() {
        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("技术交流群");
        request.setMemberIds(List.of(9999L));

        when(userService.findByIds(Set.of(9999L))).thenReturn(Collections.emptyMap());
        when(groupMapper.insert(any(GroupEntity.class))).thenAnswer(invocation -> {
            GroupEntity saved = invocation.getArgument(0);
            saved.setId(GROUP_ID);
            return 1;
        });

        BusinessException ex = assertThrows(BusinessException.class,
                () -> groupService.createGroup(OWNER_ID, request));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("创建群组时排除创建者自己（如果memberIds包含创建者）")
    void createGroup_shouldExcludeOwnerFromMemberIds() {
        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("技术交流群");
        // memberIds 包含创建者自己，应该被排除
        request.setMemberIds(List.of(OWNER_ID, MEMBER_ID_1));

        when(userService.findByIds(Set.of(MEMBER_ID_1)))
                .thenReturn(Map.of(MEMBER_ID_1, mockUser2));
        when(groupMapper.insert(any(GroupEntity.class))).thenAnswer(invocation -> {
            GroupEntity saved = invocation.getArgument(0);
            saved.setId(GROUP_ID);
            return 1;
        });

        groupService.createGroup(OWNER_ID, request);

        // 验证只插入了2条（群主 + 1个成员），排除重复的OWNER_ID
        verify(groupMemberMapper, times(2)).insert(any(GroupMember.class));
    }

    // ==================== 获取群列表 ====================

    @Test
    @DisplayName("获取群列表成功")
    void getGroupList_shouldSucceed() {
        when(groupMemberMapper.selectGroupIdsByUserId(OWNER_ID)).thenReturn(List.of(GROUP_ID));
        when(groupMapper.selectCount(any())).thenReturn(1L);

        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setName("技术交流群");
        group.setOwnerId(OWNER_ID);
        when(groupMapper.selectList(any())).thenReturn(List.of(group));
        when(groupMemberMapper.countMembers(GROUP_ID)).thenReturn(10);

        PageResult<GroupResponse> result = groupService.getGroupList(OWNER_ID, 1, 20);

        assertEquals(1, result.getList().size());
        GroupResponse resp = result.getList().get(0);
        assertEquals(GROUP_ID, resp.getGroupId());
        assertEquals("技术交流群", resp.getName());
        assertEquals(10, resp.getMemberCount().intValue());
        assertEquals(OWNER_ID, resp.getOwnerId());
    }

    @Test
    @DisplayName("获取群列表-无群组时返回空列表")
    void getGroupList_shouldReturnEmpty_whenNoGroups() {
        when(groupMemberMapper.selectGroupIdsByUserId(OWNER_ID)).thenReturn(Collections.emptyList());

        PageResult<GroupResponse> result = groupService.getGroupList(OWNER_ID, 1, 20);

        assertTrue(result.getList().isEmpty());
        assertEquals(0, result.getTotal());
    }

    // ==================== 获取群成员 ====================

    @Test
    @DisplayName("获取群成员列表成功")
    void getGroupMembers_shouldSucceed() {
        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setName("技术交流群");

        when(groupMapper.selectById(GROUP_ID)).thenReturn(group);

        GroupMember ownerMember = new GroupMember();
        ownerMember.setGroupId(GROUP_ID);
        ownerMember.setUserId(OWNER_ID);
        ownerMember.setRole(2);

        GroupMember normalMember = new GroupMember();
        normalMember.setGroupId(GROUP_ID);
        normalMember.setUserId(MEMBER_ID_1);
        normalMember.setRole(0);
        normalMember.setCreateTime(java.time.LocalDateTime.now());

        when(groupMemberMapper.selectCount(any())).thenReturn(2L);
        when(groupMemberMapper.selectList(any())).thenReturn(List.of(ownerMember, normalMember));
        when(userService.findByIds(Set.of(OWNER_ID, MEMBER_ID_1)))
                .thenReturn(Map.of(OWNER_ID, mockUser1, MEMBER_ID_1, mockUser2));

        PageResult<GroupMemberResponse> result = groupService.getGroupMembers(GROUP_ID, 1, 50);

        assertEquals(2, result.getList().size());
        assertEquals(2L, result.getTotal());
        GroupMemberResponse ownerResp = result.getList().get(0);
        assertEquals(OWNER_ID, ownerResp.getUserId());
        assertEquals("OWNER", ownerResp.getRole());
    }

    @Test
    @DisplayName("获取群成员-群组不存在时抛出异常")
    void getGroupMembers_shouldThrow_whenGroupNotFound() {
        when(groupMapper.selectById(GROUP_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> groupService.getGroupMembers(GROUP_ID, 1, 50));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    // ==================== 修改群信息 ====================

    @Test
    @DisplayName("修改群信息成功")
    void updateGroup_shouldSucceed() {
        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setName("技术交流群");
        group.setOwnerId(OWNER_ID);

        when(groupMapper.selectById(GROUP_ID)).thenReturn(group);

        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setName("新群名");
        request.setAnnouncement("新群公告");
        request.setAnnouncementPinned(true);

        groupService.updateGroup(OWNER_ID, GROUP_ID, request);

        verify(groupMapper).updateById(group);
        assertEquals("新群名", group.getName());
        assertEquals("新群公告", group.getAnnouncement());
        assertTrue(group.getAnnouncementPinned());
    }

    @Test
    @DisplayName("修改群信息-仅群主可修改")
    void updateGroup_shouldThrow_whenNotOwner() {
        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setName("技术交流群");
        group.setOwnerId(OWNER_ID);

        when(groupMapper.selectById(GROUP_ID)).thenReturn(group);

        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setName("新群名");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> groupService.updateGroup(MEMBER_ID_1, GROUP_ID, request));
        assertEquals(ErrorCode.NO_PERMISSION, ex.getErrorCode());
    }

    // ==================== 邀请成员 ====================

    @Test
    @DisplayName("邀请成员成功")
    void inviteMembers_shouldSucceed() {
        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setMaxMemberCount(200);

        when(groupMapper.selectById(GROUP_ID)).thenReturn(group);
        // 邀请者是成员
        when(groupMemberMapper.selectRoleByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(2);
        // 被邀请用户存在
        when(userService.findByIds(Set.of(MEMBER_ID_1, MEMBER_ID_2)))
                .thenReturn(Map.of(MEMBER_ID_1, mockUser2, MEMBER_ID_2, mockUser3));
        // 当前成员数小于上限
        when(groupMemberMapper.countMembers(GROUP_ID)).thenReturn(50);
        // 被邀请人不在群中
        when(groupMemberMapper.selectRoleByGroupIdAndUserId(GROUP_ID, MEMBER_ID_1)).thenReturn(null);
        when(groupMemberMapper.selectRoleByGroupIdAndUserId(GROUP_ID, MEMBER_ID_2)).thenReturn(null);

        InviteMemberRequest request = new InviteMemberRequest();
        request.setUserIds(List.of(MEMBER_ID_1, MEMBER_ID_2));

        groupService.inviteMembers(OWNER_ID, GROUP_ID, request);

        verify(groupMemberMapper, times(2)).insert(any(GroupMember.class));
    }

    @Test
    @DisplayName("邀请成员-群成员已满")
    void inviteMembers_shouldThrow_whenGroupFull() {
        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setMaxMemberCount(200);

        when(groupMapper.selectById(GROUP_ID)).thenReturn(group);
        when(groupMemberMapper.selectRoleByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(2);
        // 已达上限
        when(groupMemberMapper.countMembers(GROUP_ID)).thenReturn(199);
        when(userService.findByIds(Set.of(MEMBER_ID_1, MEMBER_ID_2)))
                .thenReturn(Map.of(MEMBER_ID_1, mockUser2, MEMBER_ID_2, mockUser3));

        InviteMemberRequest request = new InviteMemberRequest();
        request.setUserIds(List.of(MEMBER_ID_1, MEMBER_ID_2));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> groupService.inviteMembers(OWNER_ID, GROUP_ID, request));
        assertEquals(ErrorCode.GROUP_MEMBER_FULL, ex.getErrorCode());
    }

    @Test
    @DisplayName("邀请成员-用户不存在")
    void inviteMembers_shouldThrow_whenUserNotFound() {
        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setMaxMemberCount(200);

        when(groupMapper.selectById(GROUP_ID)).thenReturn(group);
        when(groupMemberMapper.selectRoleByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(2);
        when(userService.findByIds(Set.of(9999L))).thenReturn(Collections.emptyMap());

        InviteMemberRequest request = new InviteMemberRequest();
        request.setUserIds(List.of(9999L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> groupService.inviteMembers(OWNER_ID, GROUP_ID, request));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    // ==================== 移除成员 ====================

    @Test
    @DisplayName("移除成员成功")
    void removeMember_shouldSucceed() {
        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setOwnerId(OWNER_ID);

        when(groupMapper.selectById(GROUP_ID)).thenReturn(group);
        when(groupMemberMapper.selectRoleByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(2);

        groupService.removeMember(OWNER_ID, GROUP_ID, MEMBER_ID_1);

        verify(groupMemberMapper).delete(any());
    }

    @Test
    @DisplayName("移除成员-不能移除群主")
    void removeMember_shouldThrow_whenRemoveOwner() {
        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setOwnerId(OWNER_ID);

        when(groupMapper.selectById(GROUP_ID)).thenReturn(group);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> groupService.removeMember(MEMBER_ID_1, GROUP_ID, OWNER_ID));
        assertEquals(ErrorCode.NO_PERMISSION, ex.getErrorCode());
    }

    @Test
    @DisplayName("移除成员-非群主/管理员无权限")
    void removeMember_shouldThrow_whenNoPermission() {
        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setOwnerId(OWNER_ID);

        when(groupMapper.selectById(GROUP_ID)).thenReturn(group);
        when(groupMemberMapper.selectRoleByGroupIdAndUserId(GROUP_ID, MEMBER_ID_1)).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> groupService.removeMember(MEMBER_ID_1, GROUP_ID, MEMBER_ID_2));
        assertEquals(ErrorCode.NO_PERMISSION, ex.getErrorCode());
    }

    // ==================== 退出群组 ====================

    @Test
    @DisplayName("退出群组成功")
    void leaveGroup_shouldSucceed() {
        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setOwnerId(OWNER_ID);

        when(groupMapper.selectById(GROUP_ID)).thenReturn(group);

        groupService.leaveGroup(MEMBER_ID_1, GROUP_ID);

        verify(groupMemberMapper).delete(any());
    }

    @Test
    @DisplayName("退出群组-群主不可退出")
    void leaveGroup_shouldThrow_whenOwnerLeaves() {
        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setOwnerId(OWNER_ID);

        when(groupMapper.selectById(GROUP_ID)).thenReturn(group);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> groupService.leaveGroup(OWNER_ID, GROUP_ID));
        assertEquals(ErrorCode.NO_PERMISSION, ex.getErrorCode());
    }

    // ==================== 转让群主 ====================

    @Test
    @DisplayName("转让群主成功")
    void transferOwner_shouldSucceed() {
        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setOwnerId(OWNER_ID);

        GroupMember oldOwnerMember = new GroupMember();
        oldOwnerMember.setId(1L);
        oldOwnerMember.setGroupId(GROUP_ID);
        oldOwnerMember.setUserId(OWNER_ID);
        oldOwnerMember.setRole(2);

        GroupMember newOwnerMember = new GroupMember();
        newOwnerMember.setId(2L);
        newOwnerMember.setGroupId(GROUP_ID);
        newOwnerMember.setUserId(MEMBER_ID_1);
        newOwnerMember.setRole(0);

        when(groupMapper.selectById(GROUP_ID)).thenReturn(group);
        when(groupMemberMapper.selectRoleByGroupIdAndUserId(GROUP_ID, MEMBER_ID_1)).thenReturn(0);
        when(groupMemberMapper.selectByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(oldOwnerMember);
        when(groupMemberMapper.selectByGroupIdAndUserId(GROUP_ID, MEMBER_ID_1)).thenReturn(newOwnerMember);

        groupService.transferOwner(OWNER_ID, GROUP_ID, MEMBER_ID_1);

        // 验证群 ownerId 更新
        verify(groupMapper).updateById(group);
        assertEquals(MEMBER_ID_1, group.getOwnerId());
        // 验证原群主角色变为 MEMBER
        assertEquals(0, oldOwnerMember.getRole());
        verify(groupMemberMapper).updateById(oldOwnerMember);
        // 验证目标角色变为 OWNER
        assertEquals(2, newOwnerMember.getRole());
        verify(groupMemberMapper).updateById(newOwnerMember);
    }

    @Test
    @DisplayName("转让群主-非群主无权限")
    void transferOwner_shouldThrow_whenNotOwner() {
        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setOwnerId(OWNER_ID);

        when(groupMapper.selectById(GROUP_ID)).thenReturn(group);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> groupService.transferOwner(MEMBER_ID_1, GROUP_ID, MEMBER_ID_2));
        assertEquals(ErrorCode.NO_PERMISSION, ex.getErrorCode());
    }

    @Test
    @DisplayName("转让群主-目标不在群中")
    void transferOwner_shouldThrow_whenTargetNotMember() {
        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setOwnerId(OWNER_ID);

        when(groupMapper.selectById(GROUP_ID)).thenReturn(group);
        when(groupMemberMapper.selectRoleByGroupIdAndUserId(GROUP_ID, MEMBER_ID_2)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> groupService.transferOwner(OWNER_ID, GROUP_ID, MEMBER_ID_2));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    // ==================== 解散群组 ====================

    @Test
    @DisplayName("解散群组成功")
    void dismissGroup_shouldSucceed() {
        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setOwnerId(OWNER_ID);
        group.setIsDeleted(0);

        when(groupMapper.selectById(GROUP_ID)).thenReturn(group);

        groupService.dismissGroup(OWNER_ID, GROUP_ID);

        // 验证群组被逻辑删除
        assertEquals(1, group.getIsDeleted().intValue());
        verify(groupMapper).updateById(group);
        // 验证所有成员被逻辑删除
        verify(groupMemberMapper).logicalDeleteByGroupId(GROUP_ID);
    }

    @Test
    @DisplayName("解散群组-非群主无权限")
    void dismissGroup_shouldThrow_whenNotOwner() {
        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setOwnerId(OWNER_ID);

        when(groupMapper.selectById(GROUP_ID)).thenReturn(group);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> groupService.dismissGroup(MEMBER_ID_1, GROUP_ID));
        assertEquals(ErrorCode.NO_PERMISSION, ex.getErrorCode());
    }

    // ==================== 设置/取消管理员 ====================

    @Test
    @DisplayName("设置管理员成功")
    void setAdmin_shouldSucceed() {
        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setOwnerId(OWNER_ID);

        GroupMember targetMember = new GroupMember();
        targetMember.setId(3L);
        targetMember.setGroupId(GROUP_ID);
        targetMember.setUserId(MEMBER_ID_1);
        targetMember.setRole(0);

        when(groupMapper.selectById(GROUP_ID)).thenReturn(group);
        when(groupMemberMapper.selectByGroupIdAndUserId(GROUP_ID, MEMBER_ID_1)).thenReturn(targetMember);

        groupService.setAdmin(OWNER_ID, GROUP_ID, MEMBER_ID_1, "SET");

        assertEquals(1, targetMember.getRole().intValue());
        verify(groupMemberMapper).updateById(targetMember);
    }

    @Test
    @DisplayName("取消管理员成功")
    void removeAdmin_shouldSucceed() {
        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setOwnerId(OWNER_ID);

        GroupMember targetMember = new GroupMember();
        targetMember.setId(3L);
        targetMember.setGroupId(GROUP_ID);
        targetMember.setUserId(MEMBER_ID_1);
        targetMember.setRole(1);

        when(groupMapper.selectById(GROUP_ID)).thenReturn(group);
        when(groupMemberMapper.selectByGroupIdAndUserId(GROUP_ID, MEMBER_ID_1)).thenReturn(targetMember);

        groupService.setAdmin(OWNER_ID, GROUP_ID, MEMBER_ID_1, "REMOVE");

        assertEquals(0, targetMember.getRole().intValue());
        verify(groupMemberMapper).updateById(targetMember);
    }

    @Test
    @DisplayName("设置管理员-非群主无权限")
    void setAdmin_shouldThrow_whenNotOwner() {
        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setOwnerId(OWNER_ID);

        when(groupMapper.selectById(GROUP_ID)).thenReturn(group);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> groupService.setAdmin(MEMBER_ID_1, GROUP_ID, MEMBER_ID_2, "SET"));
        assertEquals(ErrorCode.NO_PERMISSION, ex.getErrorCode());
    }

    @Test
    @DisplayName("设置管理员-目标不在群中")
    void setAdmin_shouldThrow_whenTargetNotMember() {
        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setOwnerId(OWNER_ID);

        when(groupMapper.selectById(GROUP_ID)).thenReturn(group);
        when(groupMemberMapper.selectByGroupIdAndUserId(GROUP_ID, MEMBER_ID_2)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> groupService.setAdmin(OWNER_ID, GROUP_ID, MEMBER_ID_2, "SET"));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("设置管理员-不能对群主操作")
    void setAdmin_shouldThrow_whenTargetIsOwner() {
        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setOwnerId(OWNER_ID);

        GroupMember ownerMember = new GroupMember();
        ownerMember.setId(1L);
        ownerMember.setGroupId(GROUP_ID);
        ownerMember.setUserId(OWNER_ID);
        ownerMember.setRole(2);

        when(groupMapper.selectById(GROUP_ID)).thenReturn(group);
        when(groupMemberMapper.selectByGroupIdAndUserId(GROUP_ID, OWNER_ID)).thenReturn(ownerMember);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> groupService.setAdmin(OWNER_ID, GROUP_ID, OWNER_ID, "SET"));
        assertEquals(ErrorCode.NO_PERMISSION, ex.getErrorCode());
    }

    // ==================== 设置群昵称 ====================

    @Test
    @DisplayName("设置群昵称成功")
    void setNickname_shouldSucceed() {
        GroupMember member = new GroupMember();
        member.setId(3L);
        member.setGroupId(GROUP_ID);
        member.setUserId(MEMBER_ID_1);
        member.setRole(0);

        when(groupMemberMapper.selectByGroupIdAndUserId(GROUP_ID, MEMBER_ID_1)).thenReturn(member);

        groupService.setNickname(MEMBER_ID_1, GROUP_ID, "我的群昵称");

        assertEquals("我的群昵称", member.getNickname());
        verify(groupMemberMapper).updateById(member);
    }

    @Test
    @DisplayName("设置群昵称-非成员无权限")
    void setNickname_shouldThrow_whenNotMember() {
        when(groupMemberMapper.selectByGroupIdAndUserId(GROUP_ID, MEMBER_ID_1)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> groupService.setNickname(MEMBER_ID_1, GROUP_ID, "昵称"));
        assertEquals(ErrorCode.NO_PERMISSION, ex.getErrorCode());
    }
}
