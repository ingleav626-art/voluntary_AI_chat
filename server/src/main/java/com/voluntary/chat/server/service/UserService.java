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

import java.time.LocalDate;
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

    /** 手机号脱敏：前缀长度 */
    private static final int PHONE_MASK_PREFIX_LENGTH = 3;
    /** 手机号脱敏：后缀起始位置 */
    private static final int PHONE_MASK_SUFFIX_START = 7;

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
        user.setGender(0); // 默认未知
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
        wrapper.eq(User::getPhone, phone);
        // @TableLogic 会自动添加 is_deleted = 0 条件，无需手动指定
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
        wrapper.in(User::getId, userIds);
        // @TableLogic 会自动添加 is_deleted = 0 条件，无需手动指定
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
        // 个人信息新字段
        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }
        if (request.getAge() != null) {
            user.setAge(request.getAge());
        }
        if (request.getBirthday() != null) {
            user.setBirthday(LocalDate.parse(request.getBirthday()));
        }
        if (request.getDetailBio() != null) {
            user.setDetailBio(request.getDetailBio());
        }

        userMapper.updateById(user);
    }

    /**
     * 修改密码（登录用户通过短信验证码）
     */
    @Transactional
    public void changePassword(Long userId, String newPassword, String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "两次密码不一致");
        }
        User user = findById(userId);

        // 新密码不能与旧密码相同
        if (passwordEncoder.matches(newPassword + user.getSalt(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新密码不能与旧密码相同");
        }

        // 修改密码
        String newSalt = UUID.randomUUID().toString().replace("-", "");
        String newPasswordHash = passwordEncoder.encode(newPassword + newSalt);
        user.setPasswordHash(newPasswordHash);
        user.setSalt(newSalt);
        userMapper.updateById(user);
        log.info("密码修改成功: userId={}", userId);
    }

    /**
     * 修改手机号
     */
    @Transactional
    public void changePhone(Long userId, String newPhone) {
        // 新手机号不能已被注册
        checkPhoneNotExists(newPhone);

        User user = findById(userId);
        user.setPhone(newPhone);
        userMapper.updateById(user);
        log.info("手机号修改成功: userId={}, newPhone={}", userId, newPhone);
    }

    /**
     * 忘记密码-重置密码（未登录用户）
     */
    @Transactional
    public void resetPassword(String phone, String newPassword, String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "两次密码不一致");
        }

        User user = findByPhone(phone);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        String newSalt = UUID.randomUUID().toString().replace("-", "");
        String newPasswordHash = passwordEncoder.encode(newPassword + newSalt);
        user.setPasswordHash(newPasswordHash);
        user.setSalt(newSalt);
        userMapper.updateById(user);
        log.info("密码重置成功: userId={}", user.getId());
    }

    public PageResult<UserResponse> searchUsers(String keyword, int page, int size) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(User::getUsername, keyword)
                .or()
                .like(User::getPhone, keyword)
                // @TableLogic 会自动添加 is_deleted = 0 条件，无需手动指定
                .orderByDesc(User::getCreateTime);

        long total = userMapper.selectCount(wrapper);
        int offset = (page - 1) * size;
        List<User> users = userMapper.selectList(wrapper.last("LIMIT " + offset + ", " + size));

        List<UserResponse> list = users.stream()
                .map(this::toResponse)
                .toList();

        return PageResult.of(list, total, page, size);
    }

    void checkPhoneNotExists(String phone) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone, phone);
        // @TableLogic 会自动添加 is_deleted = 0 条件，无需手动指定
        if (userMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED);
        }
    }

    private void checkUsernameNotExists(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        // @TableLogic 会自动添加 is_deleted = 0 条件，无需手动指定
        if (userMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
    }

    UserResponse toResponse(User user) {
        String maskedPhone = user.getPhone().substring(0, PHONE_MASK_PREFIX_LENGTH)
                + "****" + user.getPhone().substring(PHONE_MASK_SUFFIX_START);
        return UserResponse.builder()
                .userId(user.getId())
                .phone(maskedPhone)
                .username(user.getUsername())
                .avatar(user.getAvatar())
                .bio(user.getBio())
                .gender(user.getGender())
                .age(user.getAge())
                .birthday(user.getBirthday())
                .detailBio(user.getDetailBio())
                .createTime(user.getCreateTime())
                .build();
    }
}
