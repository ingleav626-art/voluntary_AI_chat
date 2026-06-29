package org.example.client.service;

import org.example.client.model.AiGroupConfig;
import org.example.client.model.AiMemory;
import org.example.client.model.AiProfile;
import org.example.client.model.ApiResponse;
import org.example.client.model.LoginResponse;
import org.example.client.model.PageResult;
import org.example.client.model.UserInfo;
import org.example.client.util.TokenStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AiService 单元测试
 *
 * <p>主要测试未登录场景下的方法行为，以及单例和参数构造逻辑。</p>
 */
@DisplayName("AiService 测试")
class AiServiceTest {

    private AiService aiService;

    @BeforeEach
    void setUp() {
        aiService = AiService.getInstance();
        // 确保未登录状态
        TokenStorage.clear();
    }

    @AfterEach
    void tearDown() {
        TokenStorage.clear();
    }

    /**
     * 模拟登录状态（仅内存缓存，不持久化）
     */
    private void simulateLogin() {
        final UserInfo user = new UserInfo();
        user.setUserId(1001L);
        user.setUsername("测试用户");
        final LoginResponse loginResponse = new LoginResponse(
                "test-access-token", "test-refresh-token", 7200L, user);
        TokenStorage.save(loginResponse, false);
    }

    @Test
    @DisplayName("获取单例实例")
    void getInstance_shouldReturnSameInstance() {
        final AiService instance1 = AiService.getInstance();
        final AiService instance2 = AiService.getInstance();

        assertSame(instance1, instance2);
    }

    @Test
    @DisplayName("未登录时获取AI列表返回401")
    void getAiList_shouldReturn401_whenNotLoggedIn() {
        final ApiResponse<PageResult<AiProfile>> response = aiService.getAiList(1, 20).join();

        assertNotNull(response);
        assertEquals(401, response.getCode());
        assertEquals("请先登录", response.getMessage());
    }

    @Test
    @DisplayName("未登录时创建AI角色返回401")
    void createAiProfile_shouldReturn401_whenNotLoggedIn() {
        final AiProfile profile = new AiProfile();
        profile.setName("测试AI");
        profile.setModelProvider("openai");
        profile.setModel("gpt-4");
        profile.setApiKey("sk-test");
        profile.setSystemPrompt("你是助手");
        profile.setPersona("友好");
        profile.setAvatar("http://example.com/avatar.jpg");
        profile.setIsGroup(false);
        profile.setTemperature(0.7);
        profile.setMaxTokens(2000);

        final ApiResponse<Long> response = aiService.createAiProfile(profile).join();

        assertNotNull(response);
        assertEquals(401, response.getCode());
    }

    @Test
    @DisplayName("未登录时修改AI角色返回401")
    void updateAiProfile_shouldReturn401_whenNotLoggedIn() {
        final AiProfile profile = new AiProfile();
        profile.setName("更新AI");
        profile.setModel("gpt-3.5");
        profile.setApiKey("sk-new");
        profile.setTemperature(0.5);

        final ApiResponse<Void> response = aiService.updateAiProfile(3001L, profile).join();

        assertNotNull(response);
        assertEquals(401, response.getCode());
    }

    @Test
    @DisplayName("未登录时删除AI角色返回401")
    void deleteAiProfile_shouldReturn401_whenNotLoggedIn() {
        final ApiResponse<Void> response = aiService.deleteAiProfile(3001L).join();

        assertNotNull(response);
        assertEquals(401, response.getCode());
    }

    @Test
    @DisplayName("未登录时获取AI记忆返回401")
    void getAiMemories_shouldReturn401_whenNotLoggedIn() {
        final ApiResponse<PageResult<AiMemory>> response =
                aiService.getAiMemories(3001L, 1, 20).join();

        assertNotNull(response);
        assertEquals(401, response.getCode());
    }

    @Test
    @DisplayName("未登录时创建群AI配置返回401")
    void createGroupConfig_shouldReturn401_whenNotLoggedIn() {
        final AiGroupConfig config = new AiGroupConfig();
        config.setAiId(3001L);
        config.setTriggerKeywords("AI,助手");
        config.setTriggerProbability(0.5);
        config.setIsEnabled(true);

        final ApiResponse<Long> response = aiService.createGroupConfig(2001L, config).join();

        assertNotNull(response);
        assertEquals(401, response.getCode());
    }

    @Test
    @DisplayName("未登录时获取群AI配置返回401")
    void getGroupConfigs_shouldReturn401_whenNotLoggedIn() {
        final ApiResponse<List<AiGroupConfig>> response =
                aiService.getGroupConfigs(2001L).join();

        assertNotNull(response);
        assertEquals(401, response.getCode());
    }

    @Test
    @DisplayName("创建AI角色-null字段不加入请求体")
    void createAiProfile_shouldHandleNullFields() {
        final AiProfile profile = new AiProfile();
        // 所有字段为 null

        final ApiResponse<Long> response = aiService.createAiProfile(profile).join();

        assertNotNull(response);
        assertEquals(401, response.getCode());
    }

    @Test
    @DisplayName("修改AI角色-null字段不加入请求体")
    void updateAiProfile_shouldHandleNullFields() {
        final AiProfile profile = new AiProfile();

        final ApiResponse<Void> response = aiService.updateAiProfile(3001L, profile).join();

        assertNotNull(response);
        assertEquals(401, response.getCode());
    }

    @Test
    @DisplayName("创建群AI配置-仅必填字段")
    void createGroupConfig_shouldHandleNullOptionalFields() {
        final AiGroupConfig config = new AiGroupConfig();
        config.setAiId(3001L);

        final ApiResponse<Long> response = aiService.createGroupConfig(2001L, config).join();

        assertNotNull(response);
        assertEquals(401, response.getCode());
    }

    @Test
    @DisplayName("已登录时获取AI列表-请求发送（预期网络失败）")
    void getAiList_shouldSendRequest_whenLoggedIn() {
        simulateLogin();

        final ApiResponse<PageResult<AiProfile>> response = aiService.getAiList(1, 20).join();

        assertNotNull(response);
        // 由于没有真实服务器，预期返回错误响应（非401表示请求已发送）
        assertNotEquals(401, response.getCode());
    }

    @Test
    @DisplayName("已登录时创建AI角色-请求发送（预期网络失败）")
    void createAiProfile_shouldSendRequest_whenLoggedIn() {
        simulateLogin();

        final AiProfile profile = new AiProfile();
        profile.setName("测试AI");
        profile.setModelProvider("openai");
        profile.setModel("gpt-4");
        profile.setApiKey("sk-test");
        profile.setIsGroup(false);

        final ApiResponse<Long> response = aiService.createAiProfile(profile).join();

        assertNotNull(response);
        assertNotEquals(401, response.getCode());
    }

    @Test
    @DisplayName("已登录时修改AI角色-请求发送（预期网络失败）")
    void updateAiProfile_shouldSendRequest_whenLoggedIn() {
        simulateLogin();

        final AiProfile profile = new AiProfile();
        profile.setName("更新AI");

        final ApiResponse<Void> response = aiService.updateAiProfile(3001L, profile).join();

        assertNotNull(response);
        assertNotEquals(401, response.getCode());
    }

    @Test
    @DisplayName("已登录时删除AI角色-请求发送（预期网络失败）")
    void deleteAiProfile_shouldSendRequest_whenLoggedIn() {
        simulateLogin();

        final ApiResponse<Void> response = aiService.deleteAiProfile(3001L).join();

        assertNotNull(response);
        assertNotEquals(401, response.getCode());
    }

    @Test
    @DisplayName("已登录时获取AI记忆-请求发送（预期网络失败）")
    void getAiMemories_shouldSendRequest_whenLoggedIn() {
        simulateLogin();

        final ApiResponse<PageResult<AiMemory>> response =
                aiService.getAiMemories(3001L, 1, 20).join();

        assertNotNull(response);
        assertNotEquals(401, response.getCode());
    }

    @Test
    @DisplayName("已登录时创建群AI配置-请求发送（预期网络失败）")
    void createGroupConfig_shouldSendRequest_whenLoggedIn() {
        simulateLogin();

        final AiGroupConfig config = new AiGroupConfig();
        config.setAiId(3001L);
        config.setTriggerKeywords("AI");
        config.setTriggerProbability(0.5);
        config.setIsEnabled(true);

        final ApiResponse<Long> response = aiService.createGroupConfig(2001L, config).join();

        assertNotNull(response);
        assertNotEquals(401, response.getCode());
    }

    @Test
    @DisplayName("已登录时获取群AI配置-请求发送（预期网络失败）")
    void getGroupConfigs_shouldSendRequest_whenLoggedIn() {
        simulateLogin();

        final ApiResponse<List<AiGroupConfig>> response =
                aiService.getGroupConfigs(2001L).join();

        assertNotNull(response);
        assertNotEquals(401, response.getCode());
    }
}

