package com.voluntary.chat.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voluntary.chat.server.dto.request.CreateGroupRequest;
import com.voluntary.chat.server.dto.request.FriendApplyRequest;
import com.voluntary.chat.server.dto.request.LoginRequest;
import com.voluntary.chat.server.dto.request.RegisterRequest;
import com.voluntary.chat.server.dto.request.SendMessageRequest;
import com.voluntary.chat.server.dto.request.UpdateProfileRequest;
import com.voluntary.chat.server.dto.response.RefreshTokenResponse;
import com.voluntary.chat.server.entity.User;
import com.voluntary.chat.server.mapper.UserMapper;
import com.voluntary.chat.server.security.JwtTokenProvider;
import com.voluntary.chat.server.service.FriendService;
import com.voluntary.chat.server.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("集成测试 - 全链路 API 验证")
@Sql(scripts = "/db/schema-h2.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ApiIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private UserMapper userMapper;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @Autowired
        private JwtTokenProvider jwtTokenProvider;

        @Autowired
        private UserService userService;

        @Autowired
        private FriendService friendService;

        @Autowired
        private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

        private String accessToken;
        private Long userId;
        private static final String TEST_PHONE = "13800138001";
        private static final String TEST_PASSWORD = "password123";
        private static final String TEST_USERNAME = "集成测试用户";

        private void registerAndLogin() throws Exception {
                User existing = userService.findByPhone(TEST_PHONE);
                if (existing == null) {
                        RegisterRequest registerRequest = new RegisterRequest();
                        registerRequest.setPhone(TEST_PHONE);
                        registerRequest.setCode("123456");
                        registerRequest.setUsername(TEST_USERNAME);
                        registerRequest.setPassword(TEST_PASSWORD);

                        String salt = "testsalt12345678901234567890";
                        User user = new User();
                        user.setPhone(TEST_PHONE);
                        user.setUsername(TEST_USERNAME);
                        user.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD + salt));
                        user.setSalt(salt);
                        user.setGender(0);
                        user.setStatus(0);
                        user.setIsDeleted(0);
                        userMapper.insert(user);
                }

                User user = userService.findByPhone(TEST_PHONE);
                this.userId = user.getId();
                this.accessToken = jwtTokenProvider.generateAccessToken(userId);
        }

        @Nested
        @DisplayName("一、认证模块集成测试")
        class AuthIntegrationTest {

                @Test
                @DisplayName("1.1 发送验证码 - 成功")
                void sendSmsCode_shouldSucceed() throws Exception {
                        mockMvc.perform(post("/api/auth/sms/send")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"phone\":\"13800138000\"}"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(200))
                                        .andExpect(jsonPath("$.message").value("验证码已发送"));
                }

                @Test
                @DisplayName("1.1 发送验证码 - 空手机号返回400")
                void sendSmsCode_shouldFail_whenPhoneBlank() throws Exception {
                        mockMvc.perform(post("/api/auth/sms/send")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"phone\":\"\"}"))
                                        .andExpect(status().isBadRequest());
                }

                @Test
                @DisplayName("1.3 用户登录 - 密码错误返回业务异常")
                void login_shouldFail_whenPasswordWrong() throws Exception {
                        registerAndLogin();

                        LoginRequest request = new LoginRequest();
                        request.setPhone(TEST_PHONE);
                        request.setPassword("wrongpassword");

                        mockMvc.perform(post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(not(200)));
                }

                @Test
                @DisplayName("1.3 用户登录 - 用户不存在返回业务异常")
                void login_shouldFail_whenUserNotFound() throws Exception {
                        LoginRequest request = new LoginRequest();
                        request.setPhone("19999999999");
                        request.setPassword("password123");

                        mockMvc.perform(post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(not(200)));
                }

                @Test
                @DisplayName("1.4 刷新Token - 无效Token返回业务异常")
                void refresh_shouldFail_whenTokenInvalid() throws Exception {
                        mockMvc.perform(post("/api/auth/refresh")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"refreshToken\":\"invalid-token\"}"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(not(200)));
                }

                @Test
                @DisplayName("1.4 刷新Token - 有效RefreshToken返回新Token")
                void refresh_shouldSucceed_whenTokenValid() throws Exception {
                        registerAndLogin();
                        String refreshToken = jwtTokenProvider.generateRefreshToken(userId);

                        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(200))
                                        .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                                        .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                                        .andExpect(jsonPath("$.data.expiresIn").isNumber())
                                        .andReturn();

                        RefreshTokenResponse response = objectMapper.readValue(
                                        objectMapper.readTree(result.getResponse().getContentAsString()).get("data")
                                                        .toString(),
                                        RefreshTokenResponse.class);
                        assertNotNull(response.getAccessToken());
                        assertNotNull(response.getRefreshToken());
                }
        }

        @Nested
        @DisplayName("二、用户模块集成测试")
        class UserIntegrationTest {

                @BeforeEach
                void setUp() throws Exception {
                        registerAndLogin();
                }

                @Test
                @DisplayName("2.1 获取个人信息 - 成功")
                void getProfile_shouldSucceed() throws Exception {
                        mockMvc.perform(get("/api/user/profile")
                                        .header("Authorization", "Bearer " + accessToken))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(200))
                                        .andExpect(jsonPath("$.data.username").value(TEST_USERNAME))
                                        .andExpect(jsonPath("$.data.phone").value(containsString("****")))
                                        .andExpect(jsonPath("$.data.userId").value(userId));
                }

                @Test
                @DisplayName("2.1 获取个人信息 - 未认证返回401或403")
                void getProfile_shouldFail_whenNoAuth() throws Exception {
                        mockMvc.perform(get("/api/user/profile"))
                                        .andExpect(status().is(anyOf(is(401), is(403))));
                }

                @Test
                @DisplayName("2.2 修改个人信息 - 成功")
                void updateProfile_shouldSucceed() throws Exception {
                        UpdateProfileRequest request = new UpdateProfileRequest();
                        request.setBio("集成测试签名");
                        request.setGender(1);
                        request.setAge(25);

                        mockMvc.perform(put("/api/user/profile")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(200))
                                        .andExpect(jsonPath("$.message").value("修改成功"));

                        User updated = userMapper.selectById(userId);
                        assertEquals("集成测试签名", updated.getBio());
                        assertEquals(1, updated.getGender());
                        assertEquals(25, updated.getAge());
                }

                @Test
                @DisplayName("2.2 修改个人信息 - 用户名过短返回400")
                void updateProfile_shouldFail_whenUsernameTooShort() throws Exception {
                        UpdateProfileRequest request = new UpdateProfileRequest();
                        request.setUsername("A");

                        mockMvc.perform(put("/api/user/profile")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isBadRequest());
                }

                @Test
                @DisplayName("2.3 搜索用户 - 成功")
                void searchUsers_shouldSucceed() throws Exception {
                        mockMvc.perform(get("/api/user/search")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .param("keyword", TEST_USERNAME)
                                        .param("page", "1")
                                        .param("size", "20"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(200))
                                        .andExpect(jsonPath("$.data.list").isArray())
                                        .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)));
                }

                @Test
                @DisplayName("2.3 搜索用户 - 空关键词返回结果")
                void searchUsers_shouldReturnResults_whenKeywordEmpty() throws Exception {
                        mockMvc.perform(get("/api/user/search")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .param("keyword", "不存在的用户名xyz")
                                        .param("page", "1")
                                        .param("size", "20"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(200))
                                        .andExpect(jsonPath("$.data.total").value(0));
                }
        }

        @Nested
        @DisplayName("三、好友模块集成测试")
        class FriendIntegrationTest {

                private Long user2Id;
                private String user2Phone = "13900139001";
                private String user2AccessToken;

                @BeforeEach
                void setUp() throws Exception {
                        registerAndLogin();
                        // 清理好友和群组相关表，避免测试方法间状态干扰
                        jdbcTemplate.execute("DELETE FROM friend_apply");
                        jdbcTemplate.execute("DELETE FROM friend");
                        jdbcTemplate.execute("DELETE FROM group_member");
                        jdbcTemplate.execute("DELETE FROM chat_group");

                        User existing = userService.findByPhone(user2Phone);
                        if (existing == null) {
                                String salt2 = "testsalt2abcdef123456789012";
                                User user2 = new User();
                                user2.setPhone(user2Phone);
                                user2.setUsername("好友测试用户");
                                user2.setPasswordHash(passwordEncoder.encode("password123" + salt2));
                                user2.setSalt(salt2);
                                user2.setGender(0);
                                user2.setStatus(0);
                                user2.setIsDeleted(0);
                                userMapper.insert(user2);
                        }
                        User user2 = userService.findByPhone(user2Phone);
                        this.user2Id = user2.getId();
                        this.user2AccessToken = jwtTokenProvider.generateAccessToken(user2Id);
                }

                @Test
                @DisplayName("3.1 发送好友申请 - 成功")
                void applyFriend_shouldSucceed() throws Exception {
                        FriendApplyRequest request = new FriendApplyRequest();
                        request.setTargetPhone(user2Phone);
                        request.setMessage("你好，加个好友");

                        mockMvc.perform(post("/api/friend/apply")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(200))
                                        .andExpect(jsonPath("$.message").value("申请已发送"));
                }

                @Test
                @DisplayName("3.1 发送好友申请 - 不能添加自己")
                void applyFriend_shouldFail_whenAddSelf() throws Exception {
                        FriendApplyRequest request = new FriendApplyRequest();
                        request.setTargetPhone(TEST_PHONE);

                        mockMvc.perform(post("/api/friend/apply")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(not(200)));
                }

                @Test
                @DisplayName("3.1 发送好友申请 - 重复申请返回错误")
                void applyFriend_shouldFail_whenDuplicateApply() throws Exception {
                        FriendApplyRequest request = new FriendApplyRequest();
                        request.setTargetPhone(user2Phone);
                        request.setMessage("第一次申请");

                        mockMvc.perform(post("/api/friend/apply")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(200));

                        mockMvc.perform(post("/api/friend/apply")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(not(200)));
                }

                @Test
                @DisplayName("3.2 获取好友申请列表 - 成功")
                void getApplyList_shouldSucceed() throws Exception {
                        FriendApplyRequest request = new FriendApplyRequest();
                        request.setTargetPhone(user2Phone);
                        request.setMessage("申请列表测试");

                        mockMvc.perform(post("/api/friend/apply")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isOk());

                        mockMvc.perform(get("/api/friend/apply/list")
                                        .header("Authorization", "Bearer " + user2AccessToken))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(200))
                                        .andExpect(jsonPath("$.data").isArray());
                }

                @Test
                @DisplayName("3.4 获取好友列表 - 成功")
                void getFriendList_shouldSucceed() throws Exception {
                        mockMvc.perform(get("/api/friend/list")
                                        .header("Authorization", "Bearer " + accessToken))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(200))
                                        .andExpect(jsonPath("$.data").isArray());
                }

                @Test
                @DisplayName("3.5 删除好友 - 好友关系不存在返回错误")
                void deleteFriend_shouldFail_whenNotFriend() throws Exception {
                        mockMvc.perform(delete("/api/friend/{friendId}", user2Id)
                                        .header("Authorization", "Bearer " + accessToken))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(not(200)));
                }
        }

        @Nested
        @DisplayName("四、群组模块集成测试")
        class GroupIntegrationTest {

                private Long user2Id;
                private String user2Phone = "13700137001";

                @BeforeEach
                void setUp() throws Exception {
                        registerAndLogin();
                        // 清理群组相关表，避免测试方法间状态干扰
                        jdbcTemplate.execute("DELETE FROM group_member");
                        jdbcTemplate.execute("DELETE FROM chat_group");

                        User existing = userService.findByPhone(user2Phone);
                        if (existing == null) {
                                String salt2 = "testsalt3abcdef123456789012";
                                User user2 = new User();
                                user2.setPhone(user2Phone);
                                user2.setUsername("群组测试用户");
                                user2.setPasswordHash(passwordEncoder.encode("password123" + salt2));
                                user2.setSalt(salt2);
                                user2.setGender(0);
                                user2.setStatus(0);
                                user2.setIsDeleted(0);
                                userMapper.insert(user2);
                        }
                        this.user2Id = userService.findByPhone(user2Phone).getId();
                }

                @Test
                @DisplayName("5.1 创建群组 - 成功")
                void createGroup_shouldSucceed() throws Exception {
                        CreateGroupRequest request = new CreateGroupRequest();
                        request.setName("集成测试群组");
                        request.setMemberIds(List.of(user2Id));

                        mockMvc.perform(post("/api/group/create")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(200))
                                        .andExpect(jsonPath("$.message").value("创建成功"))
                                        .andExpect(jsonPath("$.data.groupId").isNumber())
                                        .andExpect(jsonPath("$.data.name").value("集成测试群组"));
                }

                @Test
                @DisplayName("5.1 创建群组 - 名称为空返回400")
                void createGroup_shouldFail_whenNameBlank() throws Exception {
                        CreateGroupRequest request = new CreateGroupRequest();
                        request.setName("");
                        request.setMemberIds(List.of(user2Id));

                        mockMvc.perform(post("/api/group/create")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isBadRequest());
                }

                @Test
                @DisplayName("5.2 获取群列表 - 成功")
                void getGroupList_shouldSucceed() throws Exception {
                        CreateGroupRequest request = new CreateGroupRequest();
                        request.setName("群列表测试");
                        request.setMemberIds(List.of(user2Id));

                        mockMvc.perform(post("/api/group/create")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isOk());

                        mockMvc.perform(get("/api/group/list")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .param("page", "1")
                                        .param("size", "20"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(200))
                                        .andExpect(jsonPath("$.data.list").isArray())
                                        .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)));
                }

                @Test
                @DisplayName("5.3 获取群成员 - 成功")
                void getGroupMembers_shouldSucceed() throws Exception {
                        CreateGroupRequest request = new CreateGroupRequest();
                        request.setName("群成员测试");
                        request.setMemberIds(List.of(user2Id));

                        MvcResult result = mockMvc.perform(post("/api/group/create")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isOk())
                                        .andReturn();

                        Long groupId = objectMapper.readTree(result.getResponse().getContentAsString())
                                        .get("data").get("groupId").asLong();

                        mockMvc.perform(get("/api/group/{groupId}/members", groupId)
                                        .header("Authorization", "Bearer " + accessToken)
                                        .param("page", "1")
                                        .param("size", "50"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(200))
                                        .andExpect(jsonPath("$.data.list").isArray())
                                        .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(2)));
                }

                @Test
                @DisplayName("5.4 修改群信息 - 成功")
                void updateGroup_shouldSucceed() throws Exception {
                        CreateGroupRequest request = new CreateGroupRequest();
                        request.setName("修改群信息测试");
                        request.setMemberIds(List.of(user2Id));

                        MvcResult result = mockMvc.perform(post("/api/group/create")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isOk())
                                        .andReturn();

                        Long groupId = objectMapper.readTree(result.getResponse().getContentAsString())
                                        .get("data").get("groupId").asLong();

                        mockMvc.perform(put("/api/group/{groupId}", groupId)
                                        .header("Authorization", "Bearer " + accessToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"name\":\"新群名\",\"announcement\":\"新公告\"}"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(200))
                                        .andExpect(jsonPath("$.message").value("修改成功"));
                }

                @Test
                @DisplayName("5.5 邀请成员 - 成功")
                void inviteMembers_shouldSucceed() throws Exception {
                        CreateGroupRequest request = new CreateGroupRequest();
                        request.setName("邀请成员测试");
                        request.setMemberIds(List.of(user2Id));

                        MvcResult result = mockMvc.perform(post("/api/group/create")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isOk())
                                        .andReturn();

                        Long groupId = objectMapper.readTree(result.getResponse().getContentAsString())
                                        .get("data").get("groupId").asLong();

                        String user3Phone = "13600136001";
                        User existing3 = userService.findByPhone(user3Phone);
                        if (existing3 == null) {
                                String salt3 = "testsalt4abcdef123456789012";
                                User user3 = new User();
                                user3.setPhone(user3Phone);
                                user3.setUsername("邀请测试用户3");
                                user3.setPasswordHash(passwordEncoder.encode("password123" + salt3));
                                user3.setSalt(salt3);
                                user3.setGender(0);
                                user3.setStatus(0);
                                user3.setIsDeleted(0);
                                userMapper.insert(user3);
                        }
                        Long user3Id = userService.findByPhone(user3Phone).getId();

                        mockMvc.perform(post("/api/group/{groupId}/invite", groupId)
                                        .header("Authorization", "Bearer " + accessToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"userIds\":[" + user3Id + "]}"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(200))
                                        .andExpect(jsonPath("$.message").value("邀请成功"));
                }

                @Test
                @DisplayName("5.6 退出群组 - 成功")
                void leaveGroup_shouldSucceed() throws Exception {
                        CreateGroupRequest request = new CreateGroupRequest();
                        request.setName("退出群组测试");
                        request.setMemberIds(List.of(user2Id));

                        MvcResult result = mockMvc.perform(post("/api/group/create")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isOk())
                                        .andReturn();

                        Long groupId = objectMapper.readTree(result.getResponse().getContentAsString())
                                        .get("data").get("groupId").asLong();

                        String user2AccessToken = jwtTokenProvider.generateAccessToken(user2Id);

                        mockMvc.perform(post("/api/group/{groupId}/leave", groupId)
                                        .header("Authorization", "Bearer " + user2AccessToken))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(200))
                                        .andExpect(jsonPath("$.message").value("已退出"));
                }

                @Test
                @DisplayName("5.8 解散群组 - 成功")
                void dismissGroup_shouldSucceed() throws Exception {
                        CreateGroupRequest request = new CreateGroupRequest();
                        request.setName("解散群组测试");
                        request.setMemberIds(List.of(user2Id));

                        MvcResult result = mockMvc.perform(post("/api/group/create")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isOk())
                                        .andReturn();

                        Long groupId = objectMapper.readTree(result.getResponse().getContentAsString())
                                        .get("data").get("groupId").asLong();

                        mockMvc.perform(delete("/api/group/{groupId}", groupId)
                                        .header("Authorization", "Bearer " + accessToken))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(200))
                                        .andExpect(jsonPath("$.message").value("群组已解散"));
                }
        }

        @Nested
        @DisplayName("五、消息模块集成测试")
        class MessageIntegrationTest {

                private Long user2Id;
                private String user2Phone = "13500135001";
                private String sessionId;

                @BeforeEach
                void setUp() throws Exception {
                        registerAndLogin();

                        User existing = userService.findByPhone(user2Phone);
                        if (existing == null) {
                                String salt2 = "testsalt5abcdef123456789012";
                                User user2 = new User();
                                user2.setPhone(user2Phone);
                                user2.setUsername("消息测试用户");
                                user2.setPasswordHash(passwordEncoder.encode("password123" + salt2));
                                user2.setSalt(salt2);
                                user2.setGender(0);
                                user2.setStatus(0);
                                user2.setIsDeleted(0);
                                userMapper.insert(user2);
                        }
                        this.user2Id = userService.findByPhone(user2Phone).getId();

                        long min = Math.min(userId, user2Id);
                        long max = Math.max(userId, user2Id);
                        this.sessionId = "p_" + min + "_" + max;
                }

                @Test
                @DisplayName("4.3 发送消息 - 成功")
                void sendMessage_shouldSucceed() throws Exception {
                        SendMessageRequest request = new SendMessageRequest();
                        request.setSessionId(sessionId);
                        request.setType("TEXT");
                        request.setContent("集成测试消息");

                        mockMvc.perform(post("/api/message/send")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(200))
                                        .andExpect(jsonPath("$.message").value("发送成功"))
                                        .andExpect(jsonPath("$.data.messageId").isNumber())
                                        .andExpect(jsonPath("$.data.createTime").isNotEmpty());
                }

                @Test
                @DisplayName("4.3 发送消息 - 空内容返回400")
                void sendMessage_shouldFail_whenContentBlank() throws Exception {
                        SendMessageRequest request = new SendMessageRequest();
                        request.setSessionId(sessionId);
                        request.setType("TEXT");
                        request.setContent("");

                        mockMvc.perform(post("/api/message/send")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isBadRequest());
                }

                @Test
                @DisplayName("4.2 获取聊天记录 - 成功")
                void getHistory_shouldSucceed() throws Exception {
                        SendMessageRequest request = new SendMessageRequest();
                        request.setSessionId(sessionId);
                        request.setType("TEXT");
                        request.setContent("历史消息测试");

                        mockMvc.perform(post("/api/message/send")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isOk());

                        mockMvc.perform(get("/api/message/history")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .param("sessionId", sessionId)
                                        .param("page", "1")
                                        .param("size", "20"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(200))
                                        .andExpect(jsonPath("$.data.list").isArray())
                                        .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)));
                }

                @Test
                @DisplayName("4.5 标记已读 - 成功")
                void markRead_shouldSucceed() throws Exception {
                        mockMvc.perform(post("/api/message/read")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"sessionId\":\"" + sessionId + "\",\"messageIds\":[1]}"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(200));
                }
        }

        @Nested
        @DisplayName("六、会话模块集成测试")
        class ConversationIntegrationTest {

                private Long user2Id;
                private String user2Phone = "13400134001";

                @BeforeEach
                void setUp() throws Exception {
                        registerAndLogin();

                        User existing = userService.findByPhone(user2Phone);
                        if (existing == null) {
                                String salt2 = "testsalt6abcdef123456789012";
                                User user2 = new User();
                                user2.setPhone(user2Phone);
                                user2.setUsername("会话测试用户");
                                user2.setPasswordHash(passwordEncoder.encode("password123" + salt2));
                                user2.setSalt(salt2);
                                user2.setGender(0);
                                user2.setStatus(0);
                                user2.setIsDeleted(0);
                                userMapper.insert(user2);
                        }
                        this.user2Id = userService.findByPhone(user2Phone).getId();
                }

                @Test
                @DisplayName("4.1 获取会话列表 - 无会话返回空列表")
                void getConversations_shouldReturnEmpty_whenNoConversations() throws Exception {
                        mockMvc.perform(get("/api/conversation/list")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .param("page", "1")
                                        .param("size", "20"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(200))
                                        .andExpect(jsonPath("$.data.list").isArray());
                }

                @Test
                @DisplayName("4.1 获取会话列表 - 发送消息后出现会话")
                void getConversations_shouldReturnConversation_afterMessageSent() throws Exception {
                        long min = Math.min(userId, user2Id);
                        long max = Math.max(userId, user2Id);
                        String sessionId = "p_" + min + "_" + max;

                        SendMessageRequest request = new SendMessageRequest();
                        request.setSessionId(sessionId);
                        request.setType("TEXT");
                        request.setContent("会话列表测试");

                        mockMvc.perform(post("/api/message/send")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isOk());

                        mockMvc.perform(get("/api/conversation/list")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .param("page", "1")
                                        .param("size", "20"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code").value(200))
                                        .andExpect(jsonPath("$.data.list").isArray())
                                        .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)));
                }
        }

        @Nested
        @DisplayName("七、端到端流程集成测试")
        class EndToEndIntegrationTest {

                @Test
                @DisplayName("完整用户流程：注册→登录→修改资料→搜索→创建群→发消息→查看会话")
                void fullUserFlow_shouldSucceed() throws Exception {
                        String e2ePhone = "13300133001";
                        String e2ePassword = "e2epass123";

                        User existing = userService.findByPhone(e2ePhone);
                        if (existing == null) {
                                String salt = "e2esalt1234567890123456789";
                                User user = new User();
                                user.setPhone(e2ePhone);
                                user.setUsername("端到端测试用户");
                                user.setPasswordHash(passwordEncoder.encode(e2ePassword + salt));
                                user.setSalt(salt);
                                user.setGender(0);
                                user.setStatus(0);
                                user.setIsDeleted(0);
                                userMapper.insert(user);
                        }
                        User e2eUser = userService.findByPhone(e2ePhone);
                        Long e2eUserId = e2eUser.getId();
                        String e2eToken = jwtTokenProvider.generateAccessToken(e2eUserId);

                        mockMvc.perform(get("/api/user/profile")
                                        .header("Authorization", "Bearer " + e2eToken))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.data.username").value("端到端测试用户"));

                        UpdateProfileRequest updateReq = new UpdateProfileRequest();
                        updateReq.setBio("端到端测试签名");
                        updateReq.setGender(1);
                        mockMvc.perform(put("/api/user/profile")
                                        .header("Authorization", "Bearer " + e2eToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(updateReq)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.message").value("修改成功"));

                        mockMvc.perform(get("/api/user/search")
                                        .header("Authorization", "Bearer " + e2eToken)
                                        .param("keyword", "端到端")
                                        .param("page", "1")
                                        .param("size", "20"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)));

                        registerAndLogin();

                        CreateGroupRequest groupReq = new CreateGroupRequest();
                        groupReq.setName("端到端测试群");
                        groupReq.setMemberIds(List.of(e2eUserId));

                        MvcResult groupResult = mockMvc.perform(post("/api/group/create")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(groupReq)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.data.groupId").isNumber())
                                        .andReturn();

                        Long groupId = objectMapper.readTree(groupResult.getResponse().getContentAsString())
                                        .get("data").get("groupId").asLong();

                        String groupSessionId = "g_" + groupId;
                        SendMessageRequest msgReq = new SendMessageRequest();
                        msgReq.setSessionId(groupSessionId);
                        msgReq.setType("TEXT");
                        msgReq.setContent("端到端群消息测试");

                        mockMvc.perform(post("/api/message/send")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(msgReq)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.data.messageId").isNumber());

                        mockMvc.perform(get("/api/conversation/list")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .param("page", "1")
                                        .param("size", "20"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)));
                }
        }
}
