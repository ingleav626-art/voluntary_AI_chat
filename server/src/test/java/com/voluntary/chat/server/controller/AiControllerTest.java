package com.voluntary.chat.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voluntary.chat.common.dto.PageResult;
import com.voluntary.chat.server.dto.request.AiGroupConfigRequest;
import com.voluntary.chat.server.dto.request.CreateAiProfileRequest;
import com.voluntary.chat.server.dto.request.UpdateAiProfileRequest;
import com.voluntary.chat.server.dto.response.AiGroupConfigResponse;
import com.voluntary.chat.server.dto.response.AiProfileResponse;
import com.voluntary.chat.server.security.JwtTokenProvider;
import com.voluntary.chat.server.security.SecurityUtils;
import com.voluntary.chat.server.service.AiGroupConfigService;
import com.voluntary.chat.server.service.AiMemoryService;
import com.voluntary.chat.server.service.AiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AI 控制器测试
 */
@WebMvcTest(AiController.class)
@AutoConfigureMockMvc(addFilters = false)
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AiController 测试")
class AiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiService aiService;

    @MockBean
    private AiGroupConfigService aiGroupConfigService;

    @MockBean
    private AiMemoryService aiMemoryService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private static final Long USER_ID = 1001L;
    private static final Long AI_ID = 3001L;
    private static final Long GROUP_ID = 2001L;

    @BeforeEach
    void setUp() {
        // Mock SecurityUtils
    }

    @Test
    @DisplayName("获取 AI 列表 - 成功")
    void listAiProfiles_shouldReturnList() throws Exception {
        final AiProfileResponse profile1 = AiProfileResponse.builder()
                .aiId(AI_ID)
                .name("小助手")
                .build();

        final PageResult<AiProfileResponse> result = PageResult.<AiProfileResponse>builder()
                .list(List.of(profile1))
                .total(1L)
                .page(1)
                .size(20)
                .build();

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            when(aiService.listAiProfiles(USER_ID, 1, 20)).thenReturn(result);

            mockMvc.perform(get("/api/ai/list")
                    .param("page", "1")
                    .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.list").isArray());
        }
    }

    @Test
    @DisplayName("创建 AI 角色 - 成功")
    void createAiProfile_shouldSucceed() throws Exception {
        final CreateAiProfileRequest request = new CreateAiProfileRequest();
        request.setName("小助手");
        request.setModelProvider("openai");
        request.setModel("gpt-4");
        request.setApiKey("sk-test-key");

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            when(aiService.createAiProfile(USER_ID, request)).thenReturn(AI_ID);

            mockMvc.perform(post("/api/ai/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(AI_ID));
        }
    }

    @Test
    @DisplayName("修改 AI 角色 - 成功")
    void updateAiProfile_shouldSucceed() throws Exception {
        final UpdateAiProfileRequest request = new UpdateAiProfileRequest();
        request.setName("新助手");

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

            mockMvc.perform(put("/api/ai/" + AI_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(aiService).updateAiProfile(USER_ID, AI_ID, request);
        }
    }

    @Test
    @DisplayName("删除 AI 角色 - 成功")
    void deleteAiProfile_shouldSucceed() throws Exception {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

            mockMvc.perform(delete("/api/ai/" + AI_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(aiService).deleteAiProfile(USER_ID, AI_ID);
        }
    }

    @Test
    @DisplayName("创建群 AI 配置 - 成功")
    void createGroupConfig_shouldSucceed() throws Exception {
        final AiGroupConfigRequest request = new AiGroupConfigRequest();
        request.setAiId(AI_ID);
        request.setTriggerKeywords("小助手");

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            when(aiGroupConfigService.createGroupConfig(GROUP_ID, USER_ID, request)).thenReturn(4001L);

            mockMvc.perform(post("/api/ai/group/" + GROUP_ID + "/config")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    @Test
    @DisplayName("获取群 AI 配置列表 - 成功")
    void getGroupConfigs_shouldReturnList() throws Exception {
        final AiGroupConfigResponse config = AiGroupConfigResponse.builder()
                .configId(4001L)
                .aiId(AI_ID)
                .aiName("小助手")
                .build();

        when(aiGroupConfigService.getGroupConfigs(GROUP_ID)).thenReturn(List.of(config));

        mockMvc.perform(get("/api/ai/group/" + GROUP_ID + "/configs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("获取 AI 记忆列表 - 成功")
    void getAiMemories_shouldSucceed() throws Exception {
        final com.voluntary.chat.server.entity.AiMemory memory = new com.voluntary.chat.server.entity.AiMemory();
        memory.setId(5001L);
        memory.setAiId(AI_ID);
        memory.setSummary("用户喜欢编程");

        final PageResult<com.voluntary.chat.server.entity.AiMemory> result = PageResult.<com.voluntary.chat.server.entity.AiMemory>builder()
                .list(List.of(memory))
                .total(1L)
                .page(1)
                .size(10)
                .build();

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            when(aiMemoryService.getMemories(AI_ID, USER_ID, 1, 10)).thenReturn(result);

            mockMvc.perform(get("/api/ai/" + AI_ID + "/memories")
                    .param("page", "1")
                    .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.list").isArray());
        }
    }

    @Test
    @DisplayName("获取 AI 列表 - 空列表")
    void listAiProfiles_shouldReturnEmptyList() throws Exception {
        final PageResult<AiProfileResponse> result = PageResult.<AiProfileResponse>builder()
                .list(List.of())
                .total(0L)
                .page(1)
                .size(20)
                .build();

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            when(aiService.listAiProfiles(USER_ID, 1, 20)).thenReturn(result);

            mockMvc.perform(get("/api/ai/list")
                    .param("page", "1")
                    .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.list").isEmpty());
        }
    }

    @Test
    @DisplayName("获取群 AI 配置列表 - 空列表")
    void getGroupConfigs_shouldReturnEmptyList() throws Exception {
        when(aiGroupConfigService.getGroupConfigs(GROUP_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/ai/group/" + GROUP_ID + "/configs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isEmpty());
    }
}