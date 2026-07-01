package com.voluntary.chat.server.controller;

import com.voluntary.chat.common.dto.PageResult;
import com.voluntary.chat.server.dto.request.UpdateProfileRequest;
import com.voluntary.chat.server.dto.response.UserResponse;
import com.voluntary.chat.server.service.AuthService;
import com.voluntary.chat.server.service.UserService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("UserController 测试")
class UserControllerTest {

        private MockMvc mockMvc;
        private UserService userService;
        private AuthService authService;

        @BeforeEach
        void setUp() {
                userService = mock(UserService.class);
                authService = mock(AuthService.class);
                UserController controller = new UserController(userService, authService);
                mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

                SecurityContextHolder.clearContext();
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(1L, null,
                                Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(auth);
        }

        // ===== GET /profile =====

        @Test
        @DisplayName("获取个人资料 - 成功")
        void getProfile_shouldReturnOk() throws Exception {
                UserResponse mockResp = UserResponse.builder()
                                .userId(1L)
                                .username("张三")
                                .phone("138****8000")
                                .avatar("https://example.com/avatar.jpg")
                                .bio("你好")
                                .gender(1)
                                .age(25)
                                .createTime(LocalDateTime.now())
                                .build();
                when(userService.getProfile(1L)).thenReturn(mockResp);

                mockMvc.perform(get("/api/user/profile"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.username").value("张三"))
                                .andExpect(jsonPath("$.data.phone").value("138****8000"))
                                .andExpect(jsonPath("$.data.gender").value(1))
                                .andExpect(jsonPath("$.data.age").value(25));

                verify(userService).getProfile(1L);
        }

        // ===== PUT /profile =====

        @Test
        @DisplayName("更新个人资料 - 成功")
        void updateProfile_shouldReturnOk() throws Exception {
                doNothing().when(userService).updateProfile(eq(1L), any(UpdateProfileRequest.class));

                mockMvc.perform(put("/api/user/profile")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"李四\",\"avatar\":\"https://example.com/new.jpg\","
                                                + "\"bio\":\"新签名\",\"gender\":1,\"age\":25,"
                                                + "\"birthday\":\"2000-01-15\",\"detailBio\":\"详细说明\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.message").value("修改成功"));

                verify(userService).updateProfile(eq(1L), any(UpdateProfileRequest.class));
        }

        @Test
        @DisplayName("更新个人资料 - 用户名过短校验失败")
        void updateProfile_shouldReturnBadRequest_whenUsernameTooShort() throws Exception {
                mockMvc.perform(put("/api/user/profile")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"A\"}"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("更新个人资料 - 性别校验失败")
        void updateProfile_shouldReturnBadRequest_whenGenderInvalid() throws Exception {
                mockMvc.perform(put("/api/user/profile")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"gender\":5}"))
                                .andExpect(status().isBadRequest());
        }

        // ===== PUT /password =====

        @Test
        @DisplayName("修改密码 - 成功")
        void changePassword_shouldReturnOk() throws Exception {
                doNothing().when(authService).changePassword(eq(1L), anyString(), anyString(), anyString());

                mockMvc.perform(put("/api/user/password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"smsCode\":\"123456\",\"newPassword\":\"newPass123\",\"confirmPassword\":\"newPass123\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.message").value("密码修改成功"));
        }

        @Test
        @DisplayName("修改密码 - 验证码为空")
        void changePassword_shouldReturnBadRequest_whenMissingCode() throws Exception {
                mockMvc.perform(put("/api/user/password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"newPassword\":\"newPass123\",\"confirmPassword\":\"newPass123\"}"))
                                .andExpect(status().isBadRequest());
        }

        // ===== PUT /phone =====

        @Test
        @DisplayName("修改手机号 - 成功")
        void changePhone_shouldReturnOk() throws Exception {
                doNothing().when(authService).changePhone(eq(1L), anyString(), anyString(), anyString());

                mockMvc.perform(put("/api/user/phone")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"smsCode\":\"111111\",\"newPhone\":\"13900139000\",\"newSmsCode\":\"222222\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.message").value("手机号修改成功"));
        }

        @Test
        @DisplayName("修改手机号 - 新手机号格式错误")
        void changePhone_shouldReturnBadRequest_whenPhoneInvalid() throws Exception {
                mockMvc.perform(put("/api/user/phone")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"smsCode\":\"111111\",\"newPhone\":\"12345\",\"newSmsCode\":\"222222\"}"))
                                .andExpect(status().isBadRequest());
        }

        // ===== GET /search =====

        @Test
        @DisplayName("搜索用户 - 成功")
        void searchUsers_shouldReturnOk() throws Exception {
                UserResponse user = UserResponse.builder()
                                .userId(2L)
                                .username("赵六")
                                .build();
                PageResult<UserResponse> pageResult = new PageResult<>(List.of(user), 1, 20, 1);
                when(userService.searchUsers(anyString(), anyInt(), anyInt())).thenReturn(pageResult);

                mockMvc.perform(get("/api/user/search")
                                .param("keyword", "赵")
                                .param("page", "1")
                                .param("size", "20"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.list[0].username").value("赵六"));

                verify(userService).searchUsers("赵", 1, 20);
        }

        @Test
        @DisplayName("搜索用户 - 默认分页")
        void searchUsers_shouldUseDefaultPagination() throws Exception {
                when(userService.searchUsers(anyString(), anyInt(), anyInt()))
                                .thenReturn(new PageResult<>(Collections.emptyList(), 1, 20, 0));

                mockMvc.perform(get("/api/user/search")
                                .param("keyword", "赵"))
                                .andExpect(status().isOk());

                verify(userService).searchUsers("赵", 1, 20);
        }
}