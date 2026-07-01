package com.voluntary.chat.server.controller;

import com.voluntary.chat.common.dto.PageResult;
import com.voluntary.chat.server.dto.response.ConversationResponse;
import com.voluntary.chat.server.service.ConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("ConversationController 测试")
class ConversationControllerTest {

        private MockMvc mockMvc;
        private ConversationService conversationService;

        @BeforeEach
        void setUp() {
                conversationService = mock(ConversationService.class);
                ConversationController controller = new ConversationController(conversationService);
                mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

                SecurityContextHolder.clearContext();
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(1L, null,
                                Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(auth);
        }

        @Test
        @DisplayName("获取会话列表 - 成功")
        void getConversations_shouldReturnOk() throws Exception {
                ConversationResponse conv = ConversationResponse.builder()
                                .sessionId("session-1")
                                .targetId(2L)
                                .targetName("张三")
                                .lastMessage("你好")
                                .lastMessageTime(LocalDateTime.now())
                                .unreadCount(5)
                                .build();
                PageResult<ConversationResponse> pageResult = new PageResult<>(List.of(conv), 1, 20, 1);
                when(conversationService.getConversations(eq(1L), anyInt(), anyInt(), isNull())).thenReturn(pageResult);

                mockMvc.perform(get("/api/conversation/list")
                                .param("page", "1")
                                .param("size", "20"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.list[0].sessionId").value("session-1"))
                                .andExpect(jsonPath("$.data.list[0].unreadCount").value(5));

                verify(conversationService).getConversations(1L, 1, 20, null);
        }

        @Test
        @DisplayName("获取会话列表 - 默认分页")
        void getConversations_shouldUseDefaultPagination() throws Exception {
                when(conversationService.getConversations(eq(1L), anyInt(), anyInt(), isNull()))
                                .thenReturn(new PageResult<>(Collections.emptyList(), 1, 20, 0));

                mockMvc.perform(get("/api/conversation/list"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200));

                verify(conversationService).getConversations(1L, 1, 20, null);
        }

        @Test
        @DisplayName("获取会话列表 - 无认证返回 null")
        void getConversations_noAuth() throws Exception {
                SecurityContextHolder.clearContext();
                when(conversationService.getConversations(isNull(), anyInt(), anyInt(), isNull()))
                                .thenReturn(new PageResult<>(Collections.emptyList(), 1, 20, 0));

                mockMvc.perform(get("/api/conversation/list"))
                                .andExpect(status().isOk());

                verify(conversationService).getConversations(null, 1, 20, null);
        }
}
