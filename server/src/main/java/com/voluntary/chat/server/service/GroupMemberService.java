package com.voluntary.chat.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voluntary.chat.common.dto.PageResult;
import com.voluntary.chat.common.enums.GroupRole;
import com.voluntary.chat.common.enums.MessageType;
import com.voluntary.chat.common.enums.SenderType;
import com.voluntary.chat.common.exception.BusinessException;
import com.voluntary.chat.common.exception.ErrorCode;
import com.voluntary.chat.server.dto.request.InviteMemberRequest;
import com.voluntary.chat.server.dto.response.GroupMemberResponse;
import com.voluntary.chat.server.entity.GroupEntity;
import com.voluntary.chat.server.entity.GroupMember;
import com.voluntary.chat.server.entity.Message;
import com.voluntary.chat.server.entity.User;
import com.voluntary.chat.server.mapper.GroupMemberMapper;
import com.voluntary.chat.server.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 群组成员服务
 *
 * <p>负责群组成员管理：邀请、移除、退出、获取成员列表、设置昵称。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupMemberService {

    private final GroupMemberMapper groupMemberMapper;
    private final MessageMapper messageMapper;
    private final UserService userService;
    private final GroupCoreService groupCoreService;

    /**
     * 获取群组成员列表（分页）
     *
     * @param groupId 群组ID
     * @param page    页码
     * @param size    每页数量
     * @return 分页结果
     */
    public PageResult<GroupMemberResponse> getGroupMembers(final Long groupId, final int page, final int size) {
        final GroupEntity group = groupCoreService.findGroupById(groupId);

        final LambdaQueryWrapper<GroupMember> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getIsDeleted, 0);
        final long total = groupMemberMapper.selectCount(countWrapper);

        final LambdaQueryWrapper<GroupMember> listWrapper = new LambdaQueryWrapper<>();
        listWrapper.eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getIsDeleted, 0)
                .orderByAsc(GroupMember::getRole)
                .orderByAsc(GroupMember::getCreateTime);
        final int offset = (page - 1) * size;
        final List<GroupMember> members = groupMemberMapper.selectList(
                listWrapper.last("LIMIT " + offset + ", " + size));

        if (members.isEmpty()) {
            return PageResult.of(Collections.emptyList(), total, page, size);
        }

        final Set<Long> userIds = members.stream()
                .map(GroupMember::getUserId)
                .collect(Collectors.toSet());
        final Map<Long, User> userMap = userService.findByIds(userIds);

        final List<GroupMemberResponse> list = members.stream()
                .map(member -> toGroupMemberResponse(member, userMap))
                .toList();

        return PageResult.of(list, total, page, size);
    }

    /**
     * 邀请成员加入群组
     *
     * <p>群成员均可邀请，需检查群成员上限和被邀请人是否已在群中。</p>
     *
     * @param userId  当前用户ID
     * @param groupId 群组ID
     * @param request 邀请请求
     */
    @Transactional
    public void inviteMembers(final Long userId, final Long groupId, final InviteMemberRequest request) {
        final GroupEntity group = groupCoreService.findGroupById(groupId);

        checkMember(groupId, userId);

        final Set<Long> inviteUserIds = request.getUserIds().stream()
                .filter(id -> !id.equals(userId))
                .collect(Collectors.toSet());
        final Map<Long, User> userMap = userService.findByIds(inviteUserIds);

        for (final Long inviteUserId : inviteUserIds) {
            if (!userMap.containsKey(inviteUserId)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在: " + inviteUserId);
            }
        }

        final int currentCount = groupMemberMapper.countMembers(groupId);
        if (currentCount + inviteUserIds.size() > group.getMaxMemberCount()) {
            throw new BusinessException(ErrorCode.GROUP_MEMBER_FULL);
        }

        final String groupSessionId = "g_" + groupId;
        for (final Long inviteUserId : inviteUserIds) {
            addOrRestoreMember(groupId, inviteUserId, userMap.get(inviteUserId), groupSessionId);
        }

        log.info("成员邀请成功: groupId={}, inviter={}, invitees={}",
                groupId, userId, inviteUserIds);
    }

    /**
     * 移除群成员
     *
     * <p>仅群主和管理员可移除普通成员，群主不可被移除。</p>
     *
     * @param userId        当前用户ID
     * @param groupId       群组ID
     * @param targetUserId  被移除的用户ID
     */
    @Transactional
    public void removeMember(final Long userId, final Long groupId, final Long targetUserId) {
        final GroupEntity group = groupCoreService.findGroupById(groupId);

        if (group.getOwnerId().equals(targetUserId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "不能移除群主");
        }

        final Integer operatorRole = groupMemberMapper.selectRoleByGroupIdAndUserId(groupId, userId);
        if (operatorRole == null || operatorRole < GroupRole.ADMIN.getCode()) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        if (operatorRole == GroupRole.ADMIN.getCode()) {
            final Integer targetRole = groupMemberMapper.selectRoleByGroupIdAndUserId(groupId, targetUserId);
            if (targetRole != null && targetRole > GroupRole.MEMBER.getCode()) {
                throw new BusinessException(ErrorCode.NO_PERMISSION, "无权移除管理员");
            }
        }

        final User targetUser = userService.findById(targetUserId);
        if (targetUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        final LambdaQueryWrapper<GroupMember> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, targetUserId);
        groupMemberMapper.delete(deleteWrapper);

        final String groupSessionId = "g_" + groupId;
        createSystemMessage(groupSessionId, targetUserId, groupId,
                targetUser.getUsername() + " 已被移出群聊");

        log.info("成员已移除: groupId={}, targetUserId={}, operatorId={}",
                groupId, targetUserId, userId);
    }

    /**
     * 退出群组
     *
     * <p>群主不可退出，需先转让群主或解散群组。</p>
     *
     * @param userId  当前用户ID
     * @param groupId 群组ID
     */
    @Transactional
    public void leaveGroup(final Long userId, final Long groupId) {
        final GroupEntity group = groupCoreService.findGroupById(groupId);

        if (group.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "群主不可退出群组，请先转让群主");
        }

        final User leaveUser = userService.findById(userId);
        if (leaveUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        final LambdaQueryWrapper<GroupMember> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, userId);
        groupMemberMapper.delete(deleteWrapper);

        final String groupSessionId = "g_" + groupId;
        createSystemMessage(groupSessionId, userId, groupId,
                leaveUser.getUsername() + " 已退出群聊");

        log.info("用户退出群组: groupId={}, userId={}", groupId, userId);
    }

    /**
     * 设置群成员昵称
     *
     * @param userId    当前用户ID
     * @param groupId   群组ID
     * @param nickname  群昵称
     */
    @Transactional
    public void setNickname(final Long userId, final Long groupId, final String nickname) {
        final GroupMember member = groupMemberMapper.selectByGroupIdAndUserId(groupId, userId);
        if (member == null) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "不在群中");
        }

        member.setNickname(nickname);
        groupMemberMapper.updateById(member);

        log.info("群昵称设置成功: groupId={}, userId={}, nickname={}", groupId, userId, nickname);
    }

    /**
     * 检查用户是否为群成员
     *
     * @param groupId 群组ID
     * @param userId  用户ID
     */
    public void checkMember(final Long groupId, final Long userId) {
        if (!isMember(groupId, userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "不在群中");
        }
    }

    /**
     * 判断用户是否为群成员
     *
     * @param groupId 群组ID
     * @param userId  用户ID
     * @return 是否在群中
     */
    public boolean isMember(final Long groupId, final Long userId) {
        return groupMemberMapper.selectRoleByGroupIdAndUserId(groupId, userId) != null;
    }

    /**
     * 添加或恢复成员
     */
    private void addOrRestoreMember(final Long groupId, final Long userId,
                                      final User user, final String groupSessionId) {
        if (isMember(groupId, userId)) {
            log.info("用户已在群中，跳过: groupId={}, userId={}", groupId, userId);
            return;
        }

        final GroupMember existingMember = groupMemberMapper.selectByGroupIdAndUserIdIncludeDeleted(groupId, userId);
        if (existingMember != null) {
            groupMemberMapper.restoreMember(groupId, userId);
            log.info("成员已恢复: groupId={}, userId={}", groupId, userId);
        } else {
            final GroupMember member = new GroupMember();
            member.setGroupId(groupId);
            member.setUserId(userId);
            member.setRole(GroupRole.MEMBER.getCode());
            groupMemberMapper.insert(member);
            log.info("新成员已加入: groupId={}, userId={}", groupId, userId);
        }

        if (user != null) {
            createSystemMessage(groupSessionId, userId, groupId,
                    user.getUsername() + " 已加入群聊");
        }
    }

    /**
     * 创建系统消息
     */
    private void createSystemMessage(final String sessionId, final Long senderId,
                                      final Long targetId, final String content) {
        final Message sysMsg = new Message();
        sysMsg.setSessionId(sessionId);
        sysMsg.setSenderId(senderId);
        sysMsg.setSenderType(SenderType.SYSTEM.ordinal());
        sysMsg.setTargetId(targetId);
        sysMsg.setTargetType(1);
        sysMsg.setType(MessageType.SYSTEM.ordinal());
        sysMsg.setContent(content);
        messageMapper.insert(sysMsg);
    }

    /**
     * 转换为群成员响应
     */
    private GroupMemberResponse toGroupMemberResponse(final GroupMember member, final Map<Long, User> userMap) {
        final User user = userMap.get(member.getUserId());
        final String roleStr = switch (member.getRole()) {
            case 2 -> "OWNER";
            case 1 -> "ADMIN";
            default -> "MEMBER";
        };

        return GroupMemberResponse.builder()
                .userId(member.getUserId())
                .username(user != null ? user.getUsername() : "未知用户")
                .avatar(user != null ? user.getAvatar() : null)
                .role(roleStr)
                .nickname(member.getNickname())
                .joinTime(member.getCreateTime())
                .build();
    }
}