package com.voluntary.chat.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voluntary.chat.common.exception.BusinessException;
import com.voluntary.chat.common.exception.ErrorCode;
import com.voluntary.chat.server.dto.request.LoginRequest;
import com.voluntary.chat.server.dto.request.RefreshTokenRequest;
import com.voluntary.chat.server.dto.request.RegisterRequest;
import com.voluntary.chat.server.dto.response.LoginResponse;
import com.voluntary.chat.server.dto.response.RefreshTokenResponse;
import com.voluntary.chat.server.dto.response.UserResponse;
import com.voluntary.chat.server.entity.User;
import com.voluntary.chat.server.entity.UserToken;
import com.voluntary.chat.server.mapper.UserTokenMapper;
import com.voluntary.chat.server.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import com.voluntary.chat.server.config.redis.SmsCodeStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserTokenMapper userTokenMapper;
    private final SmsCodeStorage smsCodeStorage;

    @Value("${jwt.access-token-expiration:7200000}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpiration;

    public static final String SMS_CODE_PREFIX = "sms:code:";
    private static final long SMS_CODE_TTL_MINUTES = 5;
    private static final int SMS_CODE_MAX_VALUE = 1000000;
    /** 手机号脱敏：前缀长度 */
    private static final int PHONE_MASK_PREFIX_LENGTH = 3;
    /** 手机号脱敏：后缀起始位置 */
    private static final int PHONE_MASK_SUFFIX_START = 7;

    public void sendSmsCode(String phone) {
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(SMS_CODE_MAX_VALUE));
        smsCodeStorage.put(SMS_CODE_PREFIX + phone, code, SMS_CODE_TTL_MINUTES);
        log.info("验证码已发送: phone={}, code={}（仅开发环境打印）", phone, code);
    }

    /**
     * 验证手机号的短信验证码
     *
     * @param phone 手机号
     * @param code  验证码
     */
    public void verifySmsCode(String phone, String code) {
        String cachedCode = smsCodeStorage.get(SMS_CODE_PREFIX + phone);
        if (cachedCode == null || !cachedCode.equals(code)) {
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_ERROR);
        }
        smsCodeStorage.delete(SMS_CODE_PREFIX + phone);
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        verifySmsCode(request.getPhone(), request.getCode());
        User user = userService.register(request);
        return generateLoginResponse(user);
    }

    public LoginResponse login(LoginRequest request) {
        User user = userService.login(request.getPhone(), request.getPassword());
        return generateLoginResponse(user);
    }

    /**
     * 登录用户修改密码（验证当前手机号短信码）
     */
    @Transactional
    public void changePassword(Long userId, String smsCode, String newPassword, String confirmPassword) {
        User user = userService.findById(userId);
        verifySmsCode(user.getPhone(), smsCode);
        userService.changePassword(userId, newPassword, confirmPassword);
    }

    /**
     * 登录用户修改手机号（验证旧手机和新手机短信码）
     */
    @Transactional
    public void changePhone(Long userId, String smsCode, String newPhone, String newSmsCode) {
        // 新手机号不能与当前相同
        User user = userService.findById(userId);
        if (user.getPhone().equals(newPhone)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新手机号与当前手机号相同");
        }
        // 验证当前手机号短信码
        verifySmsCode(user.getPhone(), smsCode);
        // 验证新手机号短信码
        verifySmsCode(newPhone, newSmsCode);
        userService.changePhone(userId, newPhone);
    }

    /**
     * 忘记密码 - 通过手机号+短信码重置密码
     */
    @Transactional
    public void forgotPassword(String phone, String code, String newPassword, String confirmPassword) {
        verifySmsCode(phone, code);
        userService.resetPassword(phone, newPassword, confirmPassword);
    }

    public RefreshTokenResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        String newAccessToken = jwtTokenProvider.generateAccessToken(userId);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);

        saveToken(userId, newAccessToken, newRefreshToken);

        return RefreshTokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(TimeUnit.MILLISECONDS.toSeconds(accessTokenExpiration))
                .build();
    }

    private LoginResponse generateLoginResponse(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        saveToken(user.getId(), accessToken, refreshToken);

        UserResponse userResponse = UserResponse.builder()
                .userId(user.getId())
                .phone(user.getPhone().substring(0, PHONE_MASK_PREFIX_LENGTH) + "****"
                        + user.getPhone().substring(PHONE_MASK_SUFFIX_START))
                .username(user.getUsername())
                .avatar(user.getAvatar())
                .build();

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(TimeUnit.MILLISECONDS.toSeconds(accessTokenExpiration))
                .user(userResponse)
                .build();
    }

    private void saveToken(Long userId, String accessToken, String refreshToken) {
        LambdaQueryWrapper<UserToken> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserToken::getUserId, userId);
        userTokenMapper.delete(wrapper);

        UserToken userToken = new UserToken();
        userToken.setUserId(userId);
        userToken.setAccessToken(accessToken);
        userToken.setRefreshToken(refreshToken);
        userToken.setAccessExpires(LocalDateTime.ofInstant(
                java.time.Instant.now().plusMillis(accessTokenExpiration), ZoneId.systemDefault()));
        userToken.setRefreshExpires(LocalDateTime.ofInstant(
                java.time.Instant.now().plusMillis(refreshTokenExpiration), ZoneId.systemDefault()));
        userTokenMapper.insert(userToken);
    }
}
