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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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
        mockUser.setPasswordHash("oldHash");
        mockUser.setSalt("abc123");
        mockUser.setAvatar("http://example.com/avatar.jpg");
        mockUser.setBio("程序员");
        mockUser.setGender(0);
        mockUser.setStatus(0);
        mockUser.setIsDeleted(0);
        mockUser.setCreateTime(LocalDateTime.now());
    }

    // ===== 注册 =====

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
        assertEquals(0, result.getGender().intValue());
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

    // ===== 登录 =====

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

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.login("13800138000", "password123"));
        assertEquals(ErrorCode.ACCOUNT_OR_PASSWORD_ERROR, exception.getErrorCode());
    }

    @Test
    @DisplayName("登录失败-密码错误")
    void login_shouldFail_whenPasswordWrong() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockUser);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.login("13800138000", "wrongpassword"));
        assertEquals(ErrorCode.ACCOUNT_OR_PASSWORD_ERROR, exception.getErrorCode());
    }

    @Test
    @DisplayName("登录失败-账号已禁用")
    void login_shouldFail_whenAccountDisabled() {
        mockUser.setStatus(1);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockUser);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.login("13800138000", "password123"));
        assertEquals(ErrorCode.ACCOUNT_LOCKED, exception.getErrorCode());
    }

    // ===== 个人信息 =====

    @Test
    @DisplayName("获取个人信息成功")
    void getProfile_shouldSucceed() {
        when(userMapper.selectById(1001L)).thenReturn(mockUser);

        UserResponse response = userService.getProfile(1001L);

        assertNotNull(response);
        assertEquals(1001L, response.getUserId());
        assertEquals("张三", response.getUsername());
        assertEquals("138****8000", response.getPhone());
        assertEquals(0, response.getGender());
    }

    @Test
    @DisplayName("获取个人信息失败-用户不存在")
    void getProfile_shouldFail_whenUserNotFound() {
        when(userMapper.selectById(9999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> userService.getProfile(9999L));
    }

    // ===== 更新个人资料 =====

    @Test
    @DisplayName("更新个人信息-全字段")
    void updateProfile_shouldUpdateAllFields() {
        when(userMapper.selectById(1001L)).thenReturn(mockUser);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setUsername("李四");
        request.setAvatar("http://new-avatar.jpg");
        request.setBio("新签名");
        request.setGender(1);
        request.setAge(25);
        request.setBirthday("2000-01-15");
        request.setDetailBio("个人详细说明内容");

        assertDoesNotThrow(() -> userService.updateProfile(1001L, request));
        verify(userMapper).updateById(any(User.class));
    }

    @Test
    @DisplayName("更新个人信息-仅更新部分字段")
    void updateProfile_shouldUpdatePartialFields() {
        when(userMapper.selectById(1001L)).thenReturn(mockUser);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setGender(2);
        request.setAge(30);

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

    // ===== 修改密码 =====

    @Test
    @DisplayName("修改密码成功")
    void changePassword_shouldSucceed() {
        when(userMapper.selectById(1001L)).thenReturn(mockUser);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("newHash");
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        assertDoesNotThrow(() -> userService.changePassword(1001L, "newPass123", "newPass123"));
        verify(userMapper).updateById(any(User.class));
    }

    @Test
    @DisplayName("修改密码失败-两次密码不一致")
    void changePassword_shouldFail_whenPasswordsMismatch() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.changePassword(1001L, "newPass123", "diffPass"));
        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    @DisplayName("修改密码失败-新密码与旧密码相同")
    void changePassword_shouldFail_whenSameAsOld() {
        when(userMapper.selectById(1001L)).thenReturn(mockUser);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.changePassword(1001L, "sameAsOld", "sameAsOld"));
        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    // ===== 修改手机号 =====

    @Test
    @DisplayName("修改手机号成功")
    void changePhone_shouldSucceed() {
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userMapper.selectById(1001L)).thenReturn(mockUser);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        assertDoesNotThrow(() -> userService.changePhone(1001L, "13900139000"));
        verify(userMapper).updateById(any(User.class));
    }

    @Test
    @DisplayName("修改手机号失败-手机号已被注册")
    void changePhone_shouldFail_whenPhoneExists() {
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.changePhone(1001L, "13900139000"));
        assertEquals(ErrorCode.PHONE_ALREADY_REGISTERED, exception.getErrorCode());
    }

    // ===== 忘记密码-重置密码 =====

    @Test
    @DisplayName("重置密码成功")
    void resetPassword_shouldSucceed() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockUser);
        when(passwordEncoder.encode(anyString())).thenReturn("newHash");
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        assertDoesNotThrow(() -> userService.resetPassword("13800138000", "newPass123", "newPass123"));
        verify(userMapper).updateById(any(User.class));
    }

    @Test
    @DisplayName("重置密码失败-两次密码不一致")
    void resetPassword_shouldFail_whenPasswordsMismatch() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.resetPassword("13800138000", "newPass123", "diffPass"));
        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    @DisplayName("重置密码失败-用户不存在")
    void resetPassword_shouldFail_whenUserNotFound() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.resetPassword("13800138000", "newPass123", "newPass123"));
        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    // ===== 搜索 =====

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
