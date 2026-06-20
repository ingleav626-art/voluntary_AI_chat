package com.voluntary.chat.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voluntary.chat.common.exception.BusinessException;
import com.voluntary.chat.common.exception.ErrorCode;
import com.voluntary.chat.server.dto.request.FriendApplyRequest;
import com.voluntary.chat.server.dto.response.FriendApplyResponse;
import com.voluntary.chat.server.dto.response.FriendResponse;
import com.voluntary.chat.server.entity.Friend;
import com.voluntary.chat.server.entity.FriendApply;
import com.voluntary.chat.server.entity.User;
import com.voluntary.chat.server.mapper.FriendApplyMapper;
import com.voluntary.chat.server.mapper.FriendMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 好友服务
 *
 * <p>处理好友申请、好友关系管理。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendApplyMapper friendApplyMapper;
    private final FriendMapper friendMapper;
    private final UserService userService;

    /** 申请状态：待处理 */
    private static final int STATUS_PENDING = 0;
    /** 申请状态：已同意 */
    private static final int STATUS_ACCEPTED = 1;
    /** 申请状态：已拒绝 */
    private static final int STATUS_REJECTED = 2;

    /** 处理动作：同意 */
    private static final String ACTION_ACCEPT = "ACCEPT";
    /** 处理动作：拒绝 */
    private static final String ACTION_REJECT = "REJECT";

    /**
     * 发送好友申请
     *
     * <p>通过手机号查找目标用户。如果对方已发送过申请且待处理，自动接受并建立好友关系。</p>
     *
     * @param userId  当前用户ID
     * @param request 申请请求
     */
    @Transactional
    public void applyFriend(Long userId, FriendApplyRequest request) {
        String targetPhone = request.getTargetPhone();

        // 通过手机号查找目标用户
        User targetUser = userService.findByPhone(targetPhone);
        if (targetUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        Long targetUserId = targetUser.getId();

        // 不能添加自己为好友
        if (userId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.CANNOT_ADD_SELF);
        }

        // 已是好友关系
        if (isFriend(userId, targetUserId)) {
            throw new BusinessException(ErrorCode.ALREADY_FRIENDS);
        }

        // 检查对方是否已发送过申请（待处理状态），若是则自动接受
        FriendApply reverseApply = findPendingApply(targetUserId, userId);
        if (reverseApply != null) {
            acceptApplyAndCreateFriendship(reverseApply);
            log.info("双向申请自动建立好友关系: user1={}, user2={}", userId, targetUserId);
            return;
        }

        // 检查是否已存在待处理申请
        FriendApply existing = findPendingApply(userId, targetUserId);
        if (existing != null) {
            throw new BusinessException(ErrorCode.FRIEND_APPLY_EXISTS);
        }

        FriendApply apply = new FriendApply();
        apply.setUserId(userId);
        apply.setTargetUserId(targetUserId);
        apply.setMessage(request.getMessage());
        apply.setStatus(STATUS_PENDING);
        friendApplyMapper.insert(apply);

        log.info("好友申请已发送: from={}, to={}", userId, targetUserId);
    }

    /**
     * 获取好友申请列表（收到的申请）
     *
     * @param userId 当前用户ID
     * @return 申请列表
     */
    public List<FriendApplyResponse> getApplyList(Long userId) {
        LambdaQueryWrapper<FriendApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FriendApply::getTargetUserId, userId)
                .orderByDesc(FriendApply::getCreateTime);
        List<FriendApply> applies = friendApplyMapper.selectList(wrapper);

        if (applies.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查询申请人信息，避免循环查询
        Set<Long> applicantIds = applies.stream()
                .map(FriendApply::getUserId)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userService.findByIds(applicantIds);

        return applies.stream()
                .map(apply -> toApplyResponse(apply, userMap))
                .toList();
    }

    /**
     * 处理好友申请
     *
     * @param userId   当前用户ID
     * @param applyId  申请ID
     * @param action   处理动作：ACCEPT / REJECT
     */
    @Transactional
    public void handleApply(Long userId, Long applyId, String action) {
        FriendApply apply = friendApplyMapper.selectById(applyId);
        if (apply == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        // 仅目标用户可处理
        if (!apply.getTargetUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        // 仅待处理状态可操作
        if (apply.getStatus() != STATUS_PENDING) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "申请已处理");
        }

        if (ACTION_ACCEPT.equals(action)) {
            acceptApplyAndCreateFriendship(apply);
            log.info("好友申请已同意: applyId={}, user1={}, user2={}",
                    applyId, apply.getUserId(), apply.getTargetUserId());
        } else if (ACTION_REJECT.equals(action)) {
            apply.setStatus(STATUS_REJECTED);
            friendApplyMapper.updateById(apply);
            log.info("好友申请已拒绝: applyId={}", applyId);
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的处理动作");
        }
    }

    /**
     * 获取好友列表
     *
     * @param userId 当前用户ID
     * @return 好友列表
     */
    public List<FriendResponse> getFriendList(Long userId) {
        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Friend::getUserId, userId);
        List<Friend> friends = friendMapper.selectList(wrapper);

        if (friends.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查询好友用户信息
        Set<Long> friendIds = friends.stream()
                .map(Friend::getFriendId)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userService.findByIds(friendIds);

        return friends.stream()
                .map(friend -> toFriendResponse(friend, userMap))
                .toList();
    }

    /**
     * 删除好友
     *
     * <p>双向删除好友关系记录。</p>
     *
     * @param userId   当前用户ID
     * @param friendId 好友ID
     */
    @Transactional
    public void deleteFriend(Long userId, Long friendId) {
        if (!isFriend(userId, friendId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "好友关系不存在");
        }

        // 删除正向记录
        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Friend::getUserId, userId).eq(Friend::getFriendId, friendId);
        friendMapper.delete(wrapper);

        // 删除反向记录
        LambdaQueryWrapper<Friend> reverseWrapper = new LambdaQueryWrapper<>();
        reverseWrapper.eq(Friend::getUserId, friendId).eq(Friend::getFriendId, userId);
        friendMapper.delete(reverseWrapper);

        log.info("好友关系已删除: user1={}, user2={}", userId, friendId);
    }

    /**
     * 判断是否为好友关系
     *
     * @param userId   用户ID
     * @param friendId 好友ID
     * @return 是否好友
     */
    public boolean isFriend(Long userId, Long friendId) {
        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Friend::getUserId, userId).eq(Friend::getFriendId, friendId);
        return friendMapper.selectCount(wrapper) > 0;
    }

    /**
     * 查找待处理申请
     */
    private FriendApply findPendingApply(Long userId, Long targetUserId) {
        LambdaQueryWrapper<FriendApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FriendApply::getUserId, userId)
                .eq(FriendApply::getTargetUserId, targetUserId)
                .eq(FriendApply::getStatus, STATUS_PENDING);
        return friendApplyMapper.selectOne(wrapper);
    }

    /**
     * 接受申请并建立双向好友关系
     */
    private void acceptApplyAndCreateFriendship(FriendApply apply) {
        apply.setStatus(STATUS_ACCEPTED);
        friendApplyMapper.updateById(apply);

        // 建立双向好友关系
        createFriendIfNotExists(apply.getUserId(), apply.getTargetUserId());
        createFriendIfNotExists(apply.getTargetUserId(), apply.getUserId());
    }

    /**
     * 若不存在则创建好友关系
     *
     * <p>好友表使用逻辑删除 + 唯一索引 (user_id, friend_id)，
     * 当好友被删除后重新添加时，需先恢复已删除记录，否则唯一索引冲突。</p>
     */
    private void createFriendIfNotExists(Long userId, Long friendId) {
        if (isFriend(userId, friendId)) {
            return;
        }
        // 尝试恢复已删除的记录（绕过逻辑删除），若恢复成功则无需插入
        if (friendMapper.restoreFriend(userId, friendId) > 0) {
            return;
        }
        Friend friend = new Friend();
        friend.setUserId(userId);
        friend.setFriendId(friendId);
        friendMapper.insert(friend);
    }

    /**
     * 转换申请为响应
     */
    private FriendApplyResponse toApplyResponse(FriendApply apply, Map<Long, User> userMap) {
        User applicant = userMap.get(apply.getUserId());
        return FriendApplyResponse.builder()
                .applyId(apply.getId())
                .userId(apply.getUserId())
                .username(applicant != null ? applicant.getUsername() : null)
                .avatar(applicant != null ? applicant.getAvatar() : null)
                .message(apply.getMessage())
                .status(statusToString(apply.getStatus()))
                .createTime(apply.getCreateTime())
                .build();
    }

    /**
     * 转换好友为响应
     */
    private FriendResponse toFriendResponse(Friend friend, Map<Long, User> userMap) {
        User user = userMap.get(friend.getFriendId());
        return FriendResponse.builder()
                .userId(friend.getFriendId())
                .username(user != null ? user.getUsername() : null)
                .avatar(user != null ? user.getAvatar() : null)
                .bio(user != null ? user.getBio() : null)
                .remark(friend.getRemark())
                .online(false)
                .build();
    }

    /**
     * 状态转字符串
     */
    private String statusToString(Integer status) {
        return switch (status) {
            case STATUS_ACCEPTED -> "ACCEPTED";
            case STATUS_REJECTED -> "REJECTED";
            default -> "PENDING";
        };
    }
}
