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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User register(RegisterRequest request) {
        checkPhoneNotExists(request.getPhone());
        checkUsernameNotExists(request.getUsername());

        String salt = UUID.randomUUID().toString().replace("-", "");
        String passwordHash = passwordEncoder.encode(request.getPassword() + salt);

        User user = new User();
        user.setPhone(request.getPhone());
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordHash);
        user.setSalt(salt);
        user.setStatus(0);
        user.setIsDeleted(0);
        userMapper.insert(user);

        log.info("用户注册成功: userId={}, phone={}", user.getId(), request.getPhone());
        return user;
    }

    public User login(String phone, String password) {
        User user = findByPhone(phone);
        if (user == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_OR_PASSWORD_ERROR);
        }
        if (user.getStatus() != 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        if (!passwordEncoder.matches(password + user.getSalt(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.ACCOUNT_OR_PASSWORD_ERROR);
        }

        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("用户登录成功: userId={}", user.getId());
        return user;
    }

    public User findByPhone(String phone) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone, phone)
                .eq(User::getIsDeleted, 0);
        return userMapper.selectOne(wrapper);
    }

    public User findById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getIsDeleted() != null && user.getIsDeleted() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return user;
    }

    /**
     * 批量查询用户信息，避免循环查询
     */
    public Map<Long, User> findByIds(java.util.Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(User::getId, userIds).eq(User::getIsDeleted, 0);
        List<User> users = userMapper.selectList(wrapper);
        return users.stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    public UserResponse getProfile(Long userId) {
        User user = findById(userId);
        return toResponse(user);
    }

    @Transactional
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findById(userId);

        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            checkUsernameNotExists(request.getUsername());
            user.setUsername(request.getUsername());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }

        userMapper.updateById(user);
    }

    public PageResult<UserResponse> searchUsers(String keyword, int page, int size) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(User::getUsername, keyword)
                .or()
                .like(User::getPhone, keyword)
                .eq(User::getIsDeleted, 0)
                .orderByDesc(User::getCreateTime);

        long total = userMapper.selectCount(wrapper);
        int offset = (page - 1) * size;
        List<User> users = userMapper.selectList(wrapper.last("LIMIT " + offset + ", " + size));

        List<UserResponse> list = users.stream()
                .map(this::toResponse)
                .toList();

        return PageResult.of(list, total, page, size);
    }

    private void checkPhoneNotExists(String phone) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone, phone).eq(User::getIsDeleted, 0);
        if (userMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED);
        }
    }

    private void checkUsernameNotExists(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username).eq(User::getIsDeleted, 0);
        if (userMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
    }

    private UserResponse toResponse(User user) {
        String maskedPhone = user.getPhone().substring(0, 3)
                + "****" + user.getPhone().substring(7);
        return UserResponse.builder()
                .userId(user.getId())
                .phone(maskedPhone)
                .username(user.getUsername())
                .avatar(user.getAvatar())
                .bio(user.getBio())
                .createTime(user.getCreateTime())
                .build();
    }
}
