package com.voluntary.chat.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voluntary.chat.common.dto.PageResult;
import com.voluntary.chat.common.exception.BusinessException;
import com.voluntary.chat.common.exception.ErrorCode;
import com.voluntary.chat.server.dto.request.RegisterRequest;
import com.voluntary.chat.server.dto.request.UpdateProfileRequest;
import com.voluntary.chat.server.dto.response.UserResponse;
import com.voluntary.chat.server.entity.User;
import com.voluntary.chat.server.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 单元测试")
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1001L);
        mockUser.setPhone("13800138000");
        mockUser.setUsername("张三");
        mockUser.setPasswordHash("hashedPassword");
        mockUser.setSalt("abc123");
        mockUser.setAvatar("http://example.com/avatar.jpg");
        mockUser.setBio("程序员");
        mockUser.setStatus(0);
        mockUser.setIsDeleted(0);
        mockUser.setCreateTime(LocalDateTime.now());
    }

    @Test
    @DisplayName("注册成功")
    void register_shouldSucceed() {
        RegisterRequest request = new RegisterRequest();
        request.setPhone("13800138000");
        request.setCode("123456");
        request.setUsername("张三");
        request.setPassword("password123");

        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");
        when(userMapper.insert(any(User.class))).thenReturn(1);

        User result = userService.register(request);

        assertNotNull(result);
        verify(userMapper, times(2)).selectCount(any(LambdaQueryWrapper.class));
        verify(userMapper).insert(any(User.class));
    }

    @Test
    @DisplayName("注册失败-手机号已存在")
    void register_shouldFail_whenPhoneExists() {
        RegisterRequest request = new RegisterRequest();
        request.setPhone("13800138000");
        request.setCode("123456");
        request.setUsername("张三");
        request.setPassword("password123");

        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class, () -> userService.register(request));
        assertEquals(ErrorCode.PHONE_ALREADY_REGISTERED, exception.getErrorCode());
    }

    @Test
    @DisplayName("注册失败-用户名已存在")
    void register_shouldFail_whenUsernameExists() {
        RegisterRequest request = new RegisterRequest();
        request.setPhone("13800138000");
        request.setCode("123456");
        request.setUsername("张三");
        request.setPassword("password123");

        when(userMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L)
                .thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class, () -> userService.register(request));
        assertEquals(ErrorCode.USERNAME_ALREADY_EXISTS, exception.getErrorCode());
    }

    @Test
    @DisplayName("登录成功")
    void login_shouldSucceed() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockUser);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        User result = userService.login("13800138000", "password123");

        assertNotNull(result);
        assertEquals(1001L, result.getId());
    }

    @Test
    @DisplayName("登录失败-用户不存在")
    void login_shouldFail_whenUserNotFound() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> userService.login("13800138000", "password123"));
        assertEquals(ErrorCode.ACCOUNT_OR_PASSWORD_ERROR, exception.getErrorCode());
    }

    @Test
    @DisplayName("登录失败-密码错误")
    void login_shouldFail_whenPasswordWrong() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockUser);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> userService.login("13800138000", "wrongpassword"));
        assertEquals(ErrorCode.ACCOUNT_OR_PASSWORD_ERROR, exception.getErrorCode());
    }

    @Test
    @DisplayName("登录失败-账号已禁用")
    void login_shouldFail_whenAccountDisabled() {
        mockUser.setStatus(1);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockUser);

        BusinessException exception = assertThrows(BusinessException.class, () -> userService.login("13800138000", "password123"));
        assertEquals(ErrorCode.ACCOUNT_LOCKED, exception.getErrorCode());
    }

    @Test
    @DisplayName("获取个人信息成功")
    void getProfile_shouldSucceed() {
        when(userMapper.selectById(1001L)).thenReturn(mockUser);

        UserResponse response = userService.getProfile(1001L);

        assertNotNull(response);
        assertEquals(1001L, response.getUserId());
        assertEquals("张三", response.getUsername());
        assertEquals("138****8000", response.getPhone());
    }

    @Test
    @DisplayName("获取个人信息失败-用户不存在")
    void getProfile_shouldFail_whenUserNotFound() {
        when(userMapper.selectById(9999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> userService.getProfile(9999L));
    }

    @Test
    @DisplayName("更新个人信息成功")
    void updateProfile_shouldSucceed() {
        when(userMapper.selectById(1001L)).thenReturn(mockUser);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setUsername("李四");

        assertDoesNotThrow(() -> userService.updateProfile(1001L, request));
        verify(userMapper).updateById(any(User.class));
    }

    @Test
    @DisplayName("更新个人信息失败-用户名重复")
    void updateProfile_shouldFail_whenUsernameExists() {
        when(userMapper.selectById(1001L)).thenReturn(mockUser);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setUsername("李四");

        assertThrows(BusinessException.class, () -> userService.updateProfile(1001L, request));
    }

    @Test
    @DisplayName("搜索用户成功")
    void searchUsers_shouldReturnResults() {
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(mockUser));

        PageResult<UserResponse> result = userService.searchUsers("张", 1, 20);

        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getList().size());
    }

    @Test
    @DisplayName("搜索用户-无结果")
    void searchUsers_shouldReturnEmpty() {
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        PageResult<UserResponse> result = userService.searchUsers("不存在", 1, 20);

        assertNotNull(result);
        assertEquals(0, result.getTotal());
        assertTrue(result.getList().isEmpty());
    }
}
