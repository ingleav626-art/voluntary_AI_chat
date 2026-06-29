package com.voluntary.chat.server.controller;

import com.voluntary.chat.server.dto.request.UpdateNotificationSettingsRequest;
import com.voluntary.chat.server.dto.response.NotificationSettingsResponse;
import com.voluntary.chat.server.service.NotificationSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NotificationController 接口测试
 */
@DisplayName("NotificationController 接口测试")
class NotificationControllerTest {

    private MockMvc mockMvc;
    private NotificationSettingsService service;

    private static final Long USER_ID = 1001L;

    @BeforeEach
    void setUp() {
        service = mock(NotificationSettingsService.class);
        NotificationController controller = new NotificationController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        SecurityContextHolder.clearContext();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(USER_ID, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("GET /api/notification/settings - 返回通知设置")
    void getSettings_shouldReturnSettings() throws Exception {
        NotificationSettingsResponse response = NotificationSettingsResponse.defaultSettings();
        when(service.getSettings(USER_ID)).thenReturn(response);

        mockMvc.perform(get("/api/notification/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.messageNotification").value(true))
                .andExpect(jsonPath("$.data.messageSound").value(true))
                .andExpect(jsonPath("$.data.doNotDisturb").value(false))
                .andExpect(jsonPath("$.data.mergeWindowSeconds").value(5));

        // 验证 token 中的 USER_ID 被正确传递到 Service
        org.mockito.Mockito.verify(service).getSettings(USER_ID);
    }

    @Test
    @DisplayName("PUT /api/notification/settings - 更新部分字段")
    void updateSettings_shouldUpdateAndReturn() throws Exception {
        NotificationSettingsResponse updated = NotificationSettingsResponse.defaultSettings();
        updated.setMessageNotification(false);
        updated.setDoNotDisturb(true);
        updated.setDndStartTime(LocalTime.of(23, 0));
        updated.setDndEndTime(LocalTime.of(8, 0));

        when(service.updateSettings(eq(USER_ID), any(UpdateNotificationSettingsRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/notification/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "messageNotification": false,
                                    "doNotDisturb": true,
                                    "dndStartTime": "23:00:00",
                                    "dndEndTime": "08:00:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.messageNotification").value(false))
                .andExpect(jsonPath("$.data.doNotDisturb").value(true))
                .andExpect(jsonPath("$.data.mergeWindowSeconds").value(5));
    }

    @Test
    @DisplayName("PUT /api/notification/settings - mergeWindowSeconds 超范围返回 400")
    void updateSettings_shouldFailWhenMergeWindowOutOfRange() throws Exception {
        mockMvc.perform(put("/api/notification/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "mergeWindowSeconds": 999
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/notification/settings - 空请求体不报错")
    void updateSettings_shouldAcceptEmptyBody() throws Exception {
        NotificationSettingsResponse defaultSettings = NotificationSettingsResponse.defaultSettings();
        when(service.updateSettings(eq(USER_ID), any(UpdateNotificationSettingsRequest.class)))
                .thenReturn(defaultSettings);

        mockMvc.perform(put("/api/notification/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}