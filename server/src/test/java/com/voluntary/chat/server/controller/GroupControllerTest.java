package com.voluntary.chat.server.controller;

import com.voluntary.chat.common.dto.PageResult;
import com.voluntary.chat.common.exception.BusinessException;
import com.voluntary.chat.common.exception.ErrorCode;
import com.voluntary.chat.server.common.GlobalExceptionHandler;
import com.voluntary.chat.server.dto.request.AdminActionRequest;
import com.voluntary.chat.server.dto.request.CreateGroupRequest;
import com.voluntary.chat.server.dto.request.InviteMemberRequest;
import com.voluntary.chat.server.dto.request.SetNicknameRequest;
import com.voluntary.chat.server.dto.request.TransferOwnerRequest;
import com.voluntary.chat.server.dto.request.UpdateGroupRequest;
import com.voluntary.chat.server.dto.response.CreateGroupResponse;
import com.voluntary.chat.server.dto.response.GroupMemberResponse;
import com.voluntary.chat.server.dto.response.GroupResponse;
import com.voluntary.chat.server.service.GroupCoreService;
import com.voluntary.chat.server.service.GroupMemberService;
import com.voluntary.chat.server.service.GroupRoleService;
import com.voluntary.chat.server.service.UserService;
import com.voluntary.chat.server.websocket.ChatWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GroupController 测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("GroupController 测试")
class GroupControllerTest {

        private MockMvc mockMvc;
        private GroupCoreService groupCoreService;
        private GroupMemberService groupMemberService;
        private GroupRoleService groupRoleService;

        private static final long USER_ID = 1L;
        private static final long GROUP_ID = 2001L;

        @BeforeEach
        void setUp() {
                groupCoreService = mock(GroupCoreService.class);
                groupMemberService = mock(GroupMemberService.class);
                groupRoleService = mock(GroupRoleService.class);
                ChatWebSocketHandler webSocketHandler = mock(ChatWebSocketHandler.class);
                UserService userService = mock(UserService.class);
                GroupController controller = new GroupController(
                        groupCoreService, groupMemberService, groupRoleService, userService);
                try {
                    java.lang.reflect.Field wsField = GroupController.class.getDeclaredField("webSocketHandler");
                    wsField.setAccessible(true);
                    wsField.set(controller, webSocketHandler);
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
                mockMvc = MockMvcBuilders
                                .standaloneSetup(controller)
                                .setControllerAdvice(new GlobalExceptionHandler())
                                .build();

                SecurityContextHolder.clearContext();
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(USER_ID, null,
                                Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(auth);
        }

        @Test
        @DisplayName("POST /api/group/create - 创建群组成功")
        void createGroup_shouldReturnOk() throws Exception {
                CreateGroupResponse mockResp = CreateGroupResponse.builder()
                                .groupId(GROUP_ID)
                                .name("技术交流群")
                                .build();
                when(groupCoreService.createGroup(eq(USER_ID), any(CreateGroupRequest.class))).thenReturn(mockResp);

                mockMvc.perform(post("/api/group/create")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"技术交流群\",\"memberIds\":[1002,1003]}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.message").value("创建成功"))
                                .andExpect(jsonPath("$.data.groupId").value(GROUP_ID))
                                .andExpect(jsonPath("$.data.name").value("技术交流群"));

                verify(groupCoreService).createGroup(eq(USER_ID), any(CreateGroupRequest.class));
        }

        @Test
        @DisplayName("POST /api/group/create - 参数校验失败（名称为空）")
        void createGroup_shouldReturnBadRequest_whenNameBlank() throws Exception {
                mockMvc.perform(post("/api/group/create")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"\",\"memberIds\":[1002]}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("DELETE /api/group/{groupId}/members/{targetUserId} - 移除成员成功")
        void removeMember_shouldReturnOk() throws Exception {
                doNothing().when(groupMemberService).removeMember(USER_ID, GROUP_ID, 1002L);

                mockMvc.perform(delete("/api/group/{groupId}/members/{targetUserId}", GROUP_ID, 1002L))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.message").value("已移除"));

                verify(groupMemberService).removeMember(USER_ID, GROUP_ID, 1002L);
        }

        @Test
        @DisplayName("POST /api/group/{groupId}/leave - 退出群组成功")
        void leaveGroup_shouldReturnOk() throws Exception {
                doNothing().when(groupMemberService).leaveGroup(USER_ID, GROUP_ID);

                mockMvc.perform(post("/api/group/{groupId}/leave", GROUP_ID))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.message").value("已退出"));

                verify(groupMemberService).leaveGroup(USER_ID, GROUP_ID);
        }

        @Test
        @DisplayName("POST /api/group/{groupId}/invite - 邀请成员成功")
        void inviteMembers_shouldReturnOk() throws Exception {
                doNothing().when(groupMemberService).inviteMembers(eq(USER_ID), eq(GROUP_ID), any(InviteMemberRequest.class));

                mockMvc.perform(post("/api/group/{groupId}/invite", GROUP_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"userIds\":[1002,1003]}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.message").value("邀请成功"));

                verify(groupMemberService).inviteMembers(eq(USER_ID), eq(GROUP_ID), any(InviteMemberRequest.class));
        }

        @Test
        @DisplayName("GET /api/group/list - 获取群列表成功")
        void getGroupList_shouldReturnOk() throws Exception {
                GroupResponse mockResp = GroupResponse.builder()
                                .groupId(GROUP_ID)
                                .name("技术交流群")
                                .memberCount(10)
                                .ownerId(USER_ID)
                                .build();
                PageResult<GroupResponse> pageResult = new PageResult<>(List.of(mockResp), 1, 1, 20);
                when(groupCoreService.getGroupList(USER_ID, 1, 20)).thenReturn(pageResult);

                mockMvc.perform(get("/api/group/list")
                                .param("page", "1")
                                .param("size", "20"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.list[0].name").value("技术交流群"));

                verify(groupCoreService).getGroupList(USER_ID, 1, 20);
        }

        @Test
        @DisplayName("GET /api/group/{groupId}/members - 获取群成员成功")
        void getGroupMembers_shouldReturnOk() throws Exception {
                GroupMemberResponse mockMember = GroupMemberResponse.builder()
                                .userId(1002L)
                                .username("李四")
                                .role("MEMBER")
                                .joinTime(LocalDateTime.now())
                                .build();
                PageResult<GroupMemberResponse> pageResult = new PageResult<>(List.of(mockMember), 1, 1, 50);
                when(groupMemberService.getGroupMembers(GROUP_ID, 1, 50)).thenReturn(pageResult);

                mockMvc.perform(get("/api/group/{groupId}/members", GROUP_ID))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.list[0].userId").value(1002));

                verify(groupMemberService).getGroupMembers(GROUP_ID, 1, 50);
        }

        @Test
        @DisplayName("PUT /api/group/{groupId} - 修改群信息成功")
        void updateGroup_shouldReturnOk() throws Exception {
                doNothing().when(groupCoreService).updateGroup(eq(USER_ID), eq(GROUP_ID), any(UpdateGroupRequest.class));

                mockMvc.perform(put("/api/group/{groupId}", GROUP_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"新群名\",\"announcement\":\"新公告\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.message").value("修改成功"));

                verify(groupCoreService).updateGroup(eq(USER_ID), eq(GROUP_ID), any(UpdateGroupRequest.class));
    }

    // ==================== 新增功能端点测试 ====================

    @Test
    @DisplayName("POST /api/group/{groupId}/transfer - 转让群主成功")
    void transferOwner_shouldReturnOk() throws Exception {
        doNothing().when(groupRoleService).transferOwner(USER_ID, GROUP_ID, 1002L);

        mockMvc.perform(post("/api/group/{groupId}/transfer", GROUP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":1002}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("转让成功"));

        verify(groupRoleService).transferOwner(USER_ID, GROUP_ID, 1002L);
    }

    @Test
    @DisplayName("POST /api/group/{groupId}/transfer - 参数校验失败（targetUserId为空）")
    void transferOwner_shouldReturnBadRequest_whenTargetUserIdNull() throws Exception {
        mockMvc.perform(post("/api/group/{groupId}/transfer", GROUP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("DELETE /api/group/{groupId} - 解散群组成功")
    void dismissGroup_shouldReturnOk() throws Exception {
        doNothing().when(groupCoreService).dismissGroup(USER_ID, GROUP_ID);

        mockMvc.perform(delete("/api/group/{groupId}", GROUP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("群组已解散"));

        verify(groupCoreService).dismissGroup(USER_ID, GROUP_ID);
    }

    @Test
    @DisplayName("POST /api/group/{groupId}/admin - 设置管理员成功")
    void setAdmin_shouldReturnOk() throws Exception {
        doNothing().when(groupRoleService).setAdmin(USER_ID, GROUP_ID, 1002L, "SET");

        mockMvc.perform(post("/api/group/{groupId}/admin", GROUP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":1002,\"action\":\"SET\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));

        verify(groupRoleService).setAdmin(USER_ID, GROUP_ID, 1002L, "SET");
    }

    @Test
    @DisplayName("POST /api/group/{groupId}/admin - 参数校验失败（action为空）")
    void setAdmin_shouldReturnBadRequest_whenActionBlank() throws Exception {
        mockMvc.perform(post("/api/group/{groupId}/admin", GROUP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":1002,\"action\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("PUT /api/group/{groupId}/nickname - 设置群昵称成功")
    void setNickname_shouldReturnOk() throws Exception {
        doNothing().when(groupMemberService).setNickname(USER_ID, GROUP_ID, "我的群昵称");

        mockMvc.perform(put("/api/group/{groupId}/nickname", GROUP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"我的群昵称\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("设置成功"));

        verify(groupMemberService).setNickname(USER_ID, GROUP_ID, "我的群昵称");
    }
}
