package com.voluntary.chat.server.controller;

import com.voluntary.chat.server.dto.request.ForgotPasswordRequest;
import com.voluntary.chat.server.dto.request.LoginRequest;
import com.voluntary.chat.server.dto.request.RefreshTokenRequest;
import com.voluntary.chat.server.dto.request.RegisterRequest;
import com.voluntary.chat.server.dto.request.SmsSendRequest;
import com.voluntary.chat.server.dto.response.LoginResponse;
import com.voluntary.chat.server.dto.response.RefreshTokenResponse;
import com.voluntary.chat.server.dto.response.UserResponse;
import com.voluntary.chat.server.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("AuthController 接口测试")
class AuthControllerTest {

        private MockMvc mockMvc;
        private AuthService authService;

        @BeforeEach
        void setUp() {
                authService = mock(AuthService.class);
                AuthController controller = new AuthController(authService);
                mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        }

        @Test
        @DisplayName("发送验证码 - 成功")
        void sendSmsCode_shouldReturnOk() throws Exception {
                doNothing().when(authService).sendSmsCode("13800138000");

                mockMvc.perform(post("/api/auth/sms/send")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"phone\":\"13800138000\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.message").value("验证码已发送"));

                verify(authService).sendSmsCode("13800138000");
        }

        @Test
        @DisplayName("注册 - 成功")
        void register_shouldReturnOk() throws Exception {
                LoginResponse mockResp = LoginResponse.builder()
                                .accessToken("test-access-token")
                                .refreshToken("test-refresh-token")
                                .expiresIn(7200L)
                                .user(UserResponse.builder().userId(1L).username("张三").phone("138****8000").build())
                                .build();
                when(authService.register(any(RegisterRequest.class))).thenReturn(mockResp);

                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"phone\":\"13800138000\",\"code\":\"123456\",\"username\":\"张三\",\"password\":\"pass123\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.accessToken").value("test-access-token"))
                                .andExpect(jsonPath("$.data.user.username").value("张三"));
        }

        @Test
        @DisplayName("登录 - 成功")
        void login_shouldReturnOk() throws Exception {
                LoginResponse mockResp = LoginResponse.builder()
                                .accessToken("test-access-token")
                                .refreshToken("test-refresh-token")
                                .expiresIn(7200L)
                                .user(UserResponse.builder().userId(1L).username("张三").phone("138****8000").build())
                                .build();
                when(authService.login(any(LoginRequest.class))).thenReturn(mockResp);

                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"phone\":\"13800138000\",\"password\":\"pass123\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.accessToken").value("test-access-token"));
        }

        @Test
        @DisplayName("刷新Token - 成功")
        void refresh_shouldReturnOk() throws Exception {
                RefreshTokenResponse mockResp = RefreshTokenResponse.builder()
                                .accessToken("new-access-token")
                                .refreshToken("new-refresh-token")
                                .expiresIn(7200L)
                                .build();
                when(authService.refresh(any(RefreshTokenRequest.class))).thenReturn(mockResp);

                mockMvc.perform(post("/api/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"old-refresh-token\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"));
        }

        @Test
        @DisplayName("登录 - 手机号格式错误")
        void login_shouldReturnBadRequest_whenPhoneInvalid() throws Exception {
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"phone\":\"123\",\"password\":\"pass123\"}"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("发送验证码 - 空手机号")
        void sendSmsCode_shouldReturnBadRequest_whenPhoneBlank() throws Exception {
                mockMvc.perform(post("/api/auth/sms/send")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"phone\":\"\"}"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("忘记密码 - 成功")
        void forgotPassword_shouldReturnOk() throws Exception {
                doNothing().when(authService).forgotPassword(anyString(), anyString(), anyString(), anyString());

                mockMvc.perform(post("/api/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"phone\":\"13800138000\",\"code\":\"123456\","
                                                + "\"newPassword\":\"newPass123\",\"confirmPassword\":\"newPass123\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.message").value("密码重置成功"));

                verify(authService).forgotPassword("13800138000", "123456", "newPass123", "newPass123");
        }

        @Test
        @DisplayName("忘记密码 - 手机号格式错误")
        void forgotPassword_shouldReturnBadRequest_whenPhoneInvalid() throws Exception {
                mockMvc.perform(post("/api/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"phone\":\"123\",\"code\":\"123456\","
                                                + "\"newPassword\":\"newPass123\",\"confirmPassword\":\"newPass123\"}"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("忘记密码 - 验证码为空")
        void forgotPassword_shouldReturnBadRequest_whenCodeBlank() throws Exception {
                mockMvc.perform(post("/api/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"phone\":\"13800138000\",\"code\":\"\","
                                                + "\"newPassword\":\"newPass123\",\"confirmPassword\":\"newPass123\"}"))
                                .andExpect(status().isBadRequest());
        }
}
