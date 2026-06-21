package com.voluntary.chat.server.service;

import com.voluntary.chat.common.exception.BusinessException;
import com.voluntary.chat.common.exception.ErrorCode;
import com.voluntary.chat.server.dto.request.LoginRequest;
import com.voluntary.chat.server.dto.request.RefreshTokenRequest;
import com.voluntary.chat.server.dto.request.RegisterRequest;
import com.voluntary.chat.server.dto.response.LoginResponse;
import com.voluntary.chat.server.dto.response.RefreshTokenResponse;
import com.voluntary.chat.server.entity.User;
import com.voluntary.chat.server.entity.UserToken;
import com.voluntary.chat.server.mapper.UserTokenMapper;
import com.voluntary.chat.server.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.voluntary.chat.server.config.redis.SmsCodeStorage;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 单元测试")
class AuthServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserTokenMapper userTokenMapper;

    @Mock
    private SmsCodeStorage smsCodeStorage;

    @InjectMocks
    private AuthService authService;

    private User mockUser;
    private static final String TEST_ACCESS_TOKEN = "access.token.test";
    private static final String TEST_REFRESH_TOKEN = "refresh.token.test";

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1001L);
        mockUser.setPhone("13800138000");
        mockUser.setUsername("张三");
        mockUser.setPasswordHash("hashedPassword");
        mockUser.setSalt("abc123");
        mockUser.setStatus(0);
        mockUser.setIsDeleted(0);
        mockUser.setCreateTime(LocalDateTime.now());
    }

    @Test
    @DisplayName("发送验证码成功")
    void sendSmsCode_shouldSucceed() {
        assertDoesNotThrow(() -> authService.sendSmsCode("13800138000"));
        verify(smsCodeStorage).put(eq("sms:code:13800138000"), anyString(), anyLong());
    }

    @Test
    @DisplayName("验证短信码成功")
    void verifySmsCode_shouldSucceed() {
        when(smsCodeStorage.get("sms:code:13800138000")).thenReturn("123456");
        assertDoesNotThrow(() -> authService.verifySmsCode("13800138000", "123456"));
        verify(smsCodeStorage).delete("sms:code:13800138000");
    }

    @Test
    @DisplayName("验证短信码失败-验证码错误")
    void verifySmsCode_shouldFail_whenCodeWrong() {
        when(smsCodeStorage.get("sms:code:13800138000")).thenReturn("123456");
        assertThrows(BusinessException.class, () -> authService.verifySmsCode("13800138000", "000000"));
    }

    @Test
    @DisplayName("验证短信码失败-验证码过期")
    void verifySmsCode_shouldFail_whenCodeExpired() {
        when(smsCodeStorage.get("sms:code:13800138000")).thenReturn(null);
        assertThrows(BusinessException.class, () -> authService.verifySmsCode("13800138000", "123456"));
    }

    @Test
    @DisplayName("注册成功")
    void register_shouldSucceed() {
        RegisterRequest request = new RegisterRequest();
        request.setPhone("13800138000");
        request.setCode("123456");
        request.setUsername("张三");
        request.setPassword("password123");

        when(smsCodeStorage.get("sms:code:13800138000")).thenReturn("123456");
        when(userService.register(any(RegisterRequest.class))).thenReturn(mockUser);
        when(jwtTokenProvider.generateAccessToken(anyLong())).thenReturn(TEST_ACCESS_TOKEN);
        when(jwtTokenProvider.generateRefreshToken(anyLong())).thenReturn(TEST_REFRESH_TOKEN);
        when(userTokenMapper.insert(any(UserToken.class))).thenReturn(1);

        LoginResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals(TEST_ACCESS_TOKEN, response.getAccessToken());
        assertEquals(TEST_REFRESH_TOKEN, response.getRefreshToken());
        assertNotNull(response.getUser());
    }

    @Test
    @DisplayName("注册失败-验证码错误")
    void register_shouldFail_whenCodeWrong() {
        RegisterRequest request = new RegisterRequest();
        request.setPhone("13800138000");
        request.setCode("000000");
        request.setUsername("张三");
        request.setPassword("password123");

        when(smsCodeStorage.get("sms:code:13800138000")).thenReturn("123456");

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.register(request));
        assertEquals(ErrorCode.VERIFICATION_CODE_ERROR, exception.getErrorCode());
    }

    @Test
    @DisplayName("注册失败-验证码过期")
    void register_shouldFail_whenCodeExpired() {
        RegisterRequest request = new RegisterRequest();
        request.setPhone("13800138000");
        request.setCode("123456");
        request.setUsername("张三");
        request.setPassword("password123");

        when(smsCodeStorage.get("sms:code:13800138000")).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.register(request));
        assertEquals(ErrorCode.VERIFICATION_CODE_ERROR, exception.getErrorCode());
    }

    @Test
    @DisplayName("登录成功")
    void login_shouldSucceed() {
        LoginRequest request = new LoginRequest();
        request.setPhone("13800138000");
        request.setPassword("password123");

        when(userService.login("13800138000", "password123")).thenReturn(mockUser);
        when(jwtTokenProvider.generateAccessToken(1001L)).thenReturn(TEST_ACCESS_TOKEN);
        when(jwtTokenProvider.generateRefreshToken(1001L)).thenReturn(TEST_REFRESH_TOKEN);
        when(userTokenMapper.insert(any(UserToken.class))).thenReturn(1);

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals(TEST_ACCESS_TOKEN, response.getAccessToken());
        assertNotNull(response.getUser());
    }

    // ===== 修改密码 =====

    @Test
    @DisplayName("修改密码成功")
    void changePassword_shouldSucceed() {
        when(userService.findById(1001L)).thenReturn(mockUser);
        when(smsCodeStorage.get("sms:code:13800138000")).thenReturn("654321");

        authService.changePassword(1001L, "654321", "newPass123", "newPass123");

        verify(userService).changePassword(1001L, "newPass123", "newPass123");
    }

    @Test
    @DisplayName("修改密码失败-短信码错误")
    void changePassword_shouldFail_whenSmsCodeWrong() {
        when(userService.findById(1001L)).thenReturn(mockUser);
        when(smsCodeStorage.get("sms:code:13800138000")).thenReturn("654321");

        assertThrows(BusinessException.class,
                () -> authService.changePassword(1001L, "000000", "newPass123", "newPass123"));
        verify(userService, never()).changePassword(anyLong(), anyString(), anyString());
    }

    // ===== 修改手机号 =====

    @Test
    @DisplayName("修改手机号成功")
    void changePhone_shouldSucceed() {
        when(userService.findById(1001L)).thenReturn(mockUser);
        when(smsCodeStorage.get("sms:code:13800138000")).thenReturn("111111");
        when(smsCodeStorage.get("sms:code:13900139000")).thenReturn("222222");

        authService.changePhone(1001L, "111111", "13900139000", "222222");

        verify(userService).changePhone(1001L, "13900139000");
    }

    @Test
    @DisplayName("修改手机号失败-新旧手机号相同")
    void changePhone_shouldFail_whenSamePhone() {
        when(userService.findById(1001L)).thenReturn(mockUser);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.changePhone(1001L, "111111", "13800138000", "222222"));
        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verify(userService, never()).changePhone(anyLong(), anyString());
    }

    // ===== 忘记密码 =====

    @Test
    @DisplayName("忘记密码-重置成功")
    void forgotPassword_shouldSucceed() {
        when(smsCodeStorage.get("sms:code:13800138000")).thenReturn("123456");

        authService.forgotPassword("13800138000", "123456", "newPass123", "newPass123");

        verify(userService).resetPassword("13800138000", "newPass123", "newPass123");
    }

    @Test
    @DisplayName("忘记密码-短信码错误")
    void forgotPassword_shouldFail_whenSmsCodeWrong() {
        when(smsCodeStorage.get("sms:code:13800138000")).thenReturn("123456");

        assertThrows(BusinessException.class,
                () -> authService.forgotPassword("13800138000", "000000", "newPass123", "newPass123"));
        verify(userService, never()).resetPassword(anyString(), anyString(), anyString());
    }

    // ===== 刷新Token =====

    @Test
    @DisplayName("刷新Token成功")
    void refresh_shouldSucceed() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(TEST_REFRESH_TOKEN);

        when(jwtTokenProvider.validateToken(TEST_REFRESH_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken(TEST_REFRESH_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(TEST_REFRESH_TOKEN)).thenReturn(1001L);
        when(jwtTokenProvider.generateAccessToken(1001L)).thenReturn("new.access.token");
        when(jwtTokenProvider.generateRefreshToken(1001L)).thenReturn("new.refresh.token");
        when(userTokenMapper.insert(any(UserToken.class))).thenReturn(1);

        RefreshTokenResponse response = authService.refresh(request);

        assertNotNull(response);
        assertEquals("new.access.token", response.getAccessToken());
        assertEquals("new.refresh.token", response.getRefreshToken());
    }

    @Test
    @DisplayName("刷新Token失败-Token无效")
    void refresh_shouldFail_whenTokenInvalid() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("invalid.token");

        when(jwtTokenProvider.validateToken("invalid.token")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.refresh(request));
        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, exception.getErrorCode());
    }

    @Test
    @DisplayName("刷新Token失败-不是Refresh Token")
    void refresh_shouldFail_whenNotRefreshToken() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(TEST_ACCESS_TOKEN);

        when(jwtTokenProvider.validateToken(TEST_ACCESS_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken(TEST_ACCESS_TOKEN)).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.refresh(request));
        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, exception.getErrorCode());
    }
}
