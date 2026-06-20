package com.voluntary.chat.server.controller;

import com.voluntary.chat.common.dto.PageResult;
import com.voluntary.chat.server.dto.request.UpdateProfileRequest;
import com.voluntary.chat.server.dto.response.UserResponse;
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

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        UserController controller = new UserController(userService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        SecurityContextHolder.clearContext();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(1L, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("获取个人资料 - 成功")
    void getProfile_shouldReturnOk() throws Exception {
        UserResponse mockResp = UserResponse.builder()
                .userId(1L)
                .username("张三")
                .phone("138****8000")
                .avatar("https://example.com/avatar.jpg")
                .bio("你好")
                .createTime(LocalDateTime.now())
                .build();
        when(userService.getProfile(1L)).thenReturn(mockResp);

        mockMvc.perform(get("/api/user/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("张三"))
                .andExpect(jsonPath("$.data.phone").value("138****8000"));

        verify(userService).getProfile(1L);
    }

    @Test
    @DisplayName("更新个人资料 - 成功")
    void updateProfile_shouldReturnOk() throws Exception {
        doNothing().when(userService).updateProfile(eq(1L), any(UpdateProfileRequest.class));

        mockMvc.perform(put("/api/user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"李四\",\"avatar\":\"https://example.com/new.jpg\",\"bio\":\"新签名\"}"))
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
