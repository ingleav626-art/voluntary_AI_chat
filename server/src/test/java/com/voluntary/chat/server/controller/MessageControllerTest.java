package com.voluntary.chat.server.controller;

import com.voluntary.chat.common.dto.PageResult;
import com.voluntary.chat.server.dto.request.MarkReadRequest;
import com.voluntary.chat.server.dto.request.RecallMessageRequest;
import com.voluntary.chat.server.dto.request.SendMessageRequest;
import com.voluntary.chat.server.dto.response.MessageResponse;
import com.voluntary.chat.server.dto.response.SendMessageResponse;
import com.voluntary.chat.server.service.MessageService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("MessageController 测试")
class MessageControllerTest {

        private MockMvc mockMvc;
        private MessageService messageService;

        @BeforeEach
        void setUp() {
                messageService = mock(MessageService.class);
                MessageController controller = new MessageController(messageService);
                mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

                SecurityContextHolder.clearContext();
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(1L, null,
                                Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(auth);
        }

        @Test
        @DisplayName("发送消息 - 成功")
        void sendMessage_shouldReturnOk() throws Exception {
                SendMessageResponse mockResp = SendMessageResponse.builder()
                                .messageId(100L)
                                .createTime(LocalDateTime.now())
                                .build();
                when(messageService.sendMessage(eq(1L), any(SendMessageRequest.class))).thenReturn(mockResp);

                mockMvc.perform(post("/api/message/send")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"sessionId\":\"session-1\",\"type\":\"text\",\"content\":\"你好\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.messageId").value(100));
        }

        @Test
        @DisplayName("发送消息 - 参数校验失败")
        void sendMessage_shouldReturnBadRequest_whenInvalid() throws Exception {
                mockMvc.perform(post("/api/message/send")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"sessionId\":\"\",\"type\":\"\",\"content\":\"\"}"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("获取历史消息 - 成功")
        void getHistory_shouldReturnOk() throws Exception {
                MessageResponse msg = MessageResponse.builder()
                                .messageId(100L)
                                .sessionId("session-1")
                                .senderId(1L)
                                .content("你好")
                                .createTime(LocalDateTime.now())
                                .build();
                PageResult<MessageResponse> pageResult = new PageResult<>(List.of(msg), 1, 20, 1);
                when(messageService.getHistory(anyString(), eq(1L), anyInt(), anyInt())).thenReturn(pageResult);

                mockMvc.perform(get("/api/message/history")
                                .param("sessionId", "session-1")
                                .param("page", "1")
                                .param("size", "20"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.list[0].messageId").value(100));
        }

        @Test
        @DisplayName("获取历史消息 - 默认分页")
        void getHistory_shouldUseDefaultPagination() throws Exception {
                when(messageService.getHistory(anyString(), eq(1L), anyInt(), anyInt()))
                                .thenReturn(new PageResult<>(Collections.emptyList(), 1, 20, 0));

                mockMvc.perform(get("/api/message/history")
                                .param("sessionId", "session-1"))
                                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("撤回消息 - 成功")
        void recallMessage_shouldReturnOk() throws Exception {
                doNothing().when(messageService).recallMessage(eq(1L), anyLong());

                mockMvc.perform(post("/api/message/recall")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"messageId\":\"100\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("撤回消息 - 空消息ID校验失败")
        void recallMessage_shouldReturnBadRequest_whenBlank() throws Exception {
                mockMvc.perform(post("/api/message/recall")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"messageId\":\"\"}"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("标记已读 - 成功")
        void markRead_shouldReturnOk() throws Exception {
                doNothing().when(messageService).markRead(eq(1L), any(MarkReadRequest.class));

                mockMvc.perform(post("/api/message/read")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"sessionId\":\"session-1\",\"messageIds\":[\"100\",\"101\"]}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("标记已读 - 空参数校验失败")
        void markRead_shouldReturnBadRequest_whenInvalid() throws Exception {
                mockMvc.perform(post("/api/message/read")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"sessionId\":\"\",\"messageIds\":[]}"))
                                .andExpect(status().isBadRequest());
        }
}
