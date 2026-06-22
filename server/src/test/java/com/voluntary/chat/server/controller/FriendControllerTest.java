package com.voluntary.chat.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voluntary.chat.server.dto.request.FriendApplyHandleRequest;
import com.voluntary.chat.server.dto.request.FriendApplyRequest;
import com.voluntary.chat.server.dto.response.FriendApplyResponse;
import com.voluntary.chat.server.dto.response.FriendResponse;
import com.voluntary.chat.server.security.SecurityUtils;
import com.voluntary.chat.server.service.FriendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FriendController 接口测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FriendController 接口测试")
class FriendControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private FriendService friendService;

    private static final Long USER_ID = 1001L;
    private static final Long FRIEND_ID = 1002L;
    private static final Long APPLY_ID = 2001L;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        FriendController controller = new FriendController(friendService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("发送好友申请 - 成功")
    void applyFriend_shouldReturnOk() throws Exception {
        final FriendApplyRequest request = new FriendApplyRequest();
        request.setTargetPhone("13800138000");
        request.setMessage("你好，我想加你为好友");

        try (MockedStatic<SecurityUtils> mockedStatic = mockStatic(SecurityUtils.class)) {
            mockedStatic.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

            doNothing().when(friendService).applyFriend(eq(USER_ID), any(FriendApplyRequest.class));

            mockMvc.perform(post("/api/friend/apply")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("申请已发送"));

            verify(friendService).applyFriend(eq(USER_ID), any(FriendApplyRequest.class));
        }
    }

    @Test
    @DisplayName("获取好友申请列表 - 成功")
    void getApplyList_shouldReturnOk() throws Exception {
        final FriendApplyResponse response1 = FriendApplyResponse.builder()
                .applyId(APPLY_ID)
                .userId(FRIEND_ID)
                .username("张三")
                .avatar("http://example.com/avatar.jpg")
                .message("你好，我想加你为好友")
                .status("PENDING")
                .createTime(java.time.LocalDateTime.now())
                .build();

        final List<FriendApplyResponse> applyList = List.of(response1);

        try (MockedStatic<SecurityUtils> mockedStatic = mockStatic(SecurityUtils.class)) {
            mockedStatic.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

            when(friendService.getApplyList(USER_ID)).thenReturn(applyList);

            mockMvc.perform(get("/api/friend/apply/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].applyId").value(APPLY_ID))
                    .andExpect(jsonPath("$.data[0].username").value("张三"));

            verify(friendService).getApplyList(USER_ID);
        }
    }

    @Test
    @DisplayName("获取好友申请列表 - 空列表")
    void getApplyList_shouldReturnEmptyList() throws Exception {
        try (MockedStatic<SecurityUtils> mockedStatic = mockStatic(SecurityUtils.class)) {
            mockedStatic.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

            when(friendService.getApplyList(USER_ID)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/friend/apply/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());

            verify(friendService).getApplyList(USER_ID);
        }
    }

    @Test
    @DisplayName("处理好友申请 - 同意")
    void handleApply_shouldAccept() throws Exception {
        final FriendApplyHandleRequest request = new FriendApplyHandleRequest();
        request.setAction("ACCEPT");

        try (MockedStatic<SecurityUtils> mockedStatic = mockStatic(SecurityUtils.class)) {
            mockedStatic.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

            doNothing().when(friendService).handleApply(USER_ID, APPLY_ID, "ACCEPT");

            mockMvc.perform(post("/api/friend/apply/{applyId}/handle", APPLY_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("已同意"));

            verify(friendService).handleApply(USER_ID, APPLY_ID, "ACCEPT");
        }
    }

    @Test
    @DisplayName("处理好友申请 - 拒绝")
    void handleApply_shouldReject() throws Exception {
        final FriendApplyHandleRequest request = new FriendApplyHandleRequest();
        request.setAction("REJECT");

        try (MockedStatic<SecurityUtils> mockedStatic = mockStatic(SecurityUtils.class)) {
            mockedStatic.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

            doNothing().when(friendService).handleApply(USER_ID, APPLY_ID, "REJECT");

            mockMvc.perform(post("/api/friend/apply/{applyId}/handle", APPLY_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("已拒绝"));

            verify(friendService).handleApply(USER_ID, APPLY_ID, "REJECT");
        }
    }

    @Test
    @DisplayName("获取好友列表 - 成功")
    void getFriendList_shouldReturnOk() throws Exception {
        final FriendResponse response1 = FriendResponse.builder()
                .userId(FRIEND_ID)
                .username("张三")
                .avatar("http://example.com/avatar.jpg")
                .bio("这是我的简介")
                .remark("好友备注")
                .online(true)
                .build();

        final List<FriendResponse> friendList = List.of(response1);

        try (MockedStatic<SecurityUtils> mockedStatic = mockStatic(SecurityUtils.class)) {
            mockedStatic.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

            when(friendService.getFriendList(USER_ID)).thenReturn(friendList);

            mockMvc.perform(get("/api/friend/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].userId").value(FRIEND_ID))
                    .andExpect(jsonPath("$.data[0].username").value("张三"));

            verify(friendService).getFriendList(USER_ID);
        }
    }

    @Test
    @DisplayName("获取好友列表 - 空列表")
    void getFriendList_shouldReturnEmptyList() throws Exception {
        try (MockedStatic<SecurityUtils> mockedStatic = mockStatic(SecurityUtils.class)) {
            mockedStatic.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

            when(friendService.getFriendList(USER_ID)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/friend/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());

            verify(friendService).getFriendList(USER_ID);
        }
    }

    @Test
    @DisplayName("删除好友 - 成功")
    void deleteFriend_shouldReturnOk() throws Exception {
        try (MockedStatic<SecurityUtils> mockedStatic = mockStatic(SecurityUtils.class)) {
            mockedStatic.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

            doNothing().when(friendService).deleteFriend(USER_ID, FRIEND_ID);

            mockMvc.perform(delete("/api/friend/{friendId}", FRIEND_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("已删除"));

            verify(friendService).deleteFriend(USER_ID, FRIEND_ID);
        }
    }
}