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

    private static final String SMS_CODE_PREFIX = "sms:code:";
    private static final long SMS_CODE_TTL_MINUTES = 5;

    public void sendSmsCode(String phone) {
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
        smsCodeStorage.put(SMS_CODE_PREFIX + phone, code, SMS_CODE_TTL_MINUTES);
        log.info("验证码已发送: phone={}, code={}（仅开发环境打印）", phone, code);
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        String cachedCode = smsCodeStorage.get(SMS_CODE_PREFIX + request.getPhone());
        if (cachedCode == null || !cachedCode.equals(request.getCode())) {
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_ERROR);
        }

        User user = userService.register(request);
        smsCodeStorage.delete(SMS_CODE_PREFIX + request.getPhone());

        return generateLoginResponse(user);
    }

    public LoginResponse login(LoginRequest request) {
        User user = userService.login(request.getPhone(), request.getPassword());
        return generateLoginResponse(user);
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
                .phone(user.getPhone().substring(0, 3) + "****" + user.getPhone().substring(7))
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
