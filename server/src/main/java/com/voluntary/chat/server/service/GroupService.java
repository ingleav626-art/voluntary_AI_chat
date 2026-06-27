package com.voluntary.chat.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.voluntary.chat.common.dto.PageResult;
import com.voluntary.chat.common.enums.GroupRole;
import com.voluntary.chat.common.enums.MessageType;
import com.voluntary.chat.common.enums.SenderType;
import com.voluntary.chat.common.exception.BusinessException;
import com.voluntary.chat.common.exception.ErrorCode;
import com.voluntary.chat.server.dto.request.AdminActionRequest;
import com.voluntary.chat.server.dto.request.CreateGroupRequest;
import com.voluntary.chat.server.dto.request.InviteMemberRequest;
import com.voluntary.chat.server.dto.request.UpdateGroupRequest;
import com.voluntary.chat.server.dto.response.CreateGroupResponse;
import com.voluntary.chat.server.dto.response.GroupMemberResponse;
import com.voluntary.chat.server.dto.response.GroupResponse;
import com.voluntary.chat.server.entity.GroupEntity;
import com.voluntary.chat.server.entity.GroupMember;
import com.voluntary.chat.server.entity.Message;
import com.voluntary.chat.server.entity.User;
import com.voluntary.chat.server.mapper.GroupMapper;
import com.voluntary.chat.server.mapper.GroupMemberMapper;
import com.voluntary.chat.server.mapper.MessageMapper;
import com.voluntary.chat.server.service.GroupCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 群组服务
 *
 * <p>
 * 处理群组的创建、管理、成员操作等业务逻辑。
 * </p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final UserService userService;
    private final MessageMapper messageMapper;
    private final GroupCacheService groupCacheService;

    /** 默认最大成员数 */
    private static final int DEFAULT_MAX_MEMBER_COUNT = 200;

    /**
     * 创建群组
     *
     * <p>
     * 创建者自动成为群主，memberIds 中的用户自动成为普通成员。
     * </p>
     *
     * @param userId  创建者ID
     * @param request 创建请求
     * @return 创建结果
     */
    @Transactional
    public CreateGroupResponse createGroup(final Long userId, final CreateGroupRequest request) {
        // 1. 创建群组
        GroupEntity group = new GroupEntity();
        group.setName(request.getName());
        group.setOwnerId(userId);
        group.setMaxMemberCount(DEFAULT_MAX_MEMBER_COUNT);
        groupMapper.insert(group);

        // 2. 添加创建者为群主
        GroupMember owner = new GroupMember();
        owner.setGroupId(group.getId());
        owner.setUserId(userId);
        owner.setRole(GroupRole.OWNER.getCode());
        groupMemberMapper.insert(owner);

        // 3. 添加初始成员（去重，排除创建者自己），并发送系统消息
        final String groupSessionId = "g_" + group.getId();
        Set<Long> memberIds = request.getMemberIds().stream()
                .filter(id -> !id.equals(userId))
                .collect(Collectors.toSet());
        if (!memberIds.isEmpty()) {
            // 验证用户是否存在
            Map<Long, User> userMap = userService.findByIds(memberIds);
            for (Long memberId : memberIds) {
                if (!userMap.containsKey(memberId)) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在: " + memberId);
                }
                GroupMember member = new GroupMember();
                member.setGroupId(group.getId());
                member.setUserId(memberId);
                member.setRole(GroupRole.MEMBER.getCode());
                groupMemberMapper.insert(member);

                // 创建系统消息：XXX 已加入群聊
                User memberUser = userMap.get(memberId);
                if (memberUser != null) {
                    Message sysMsg = new Message();
                    sysMsg.setSessionId(groupSessionId);
                    sysMsg.setSenderId(memberId);
                    sysMsg.setSenderType(SenderType.SYSTEM.ordinal());
                    sysMsg.setTargetId(group.getId());
                    sysMsg.setTargetType(1);
                    sysMsg.setType(MessageType.SYSTEM.ordinal());
                    sysMsg.setContent(memberUser.getUsername() + " 已加入群聊");
                    messageMapper.insert(sysMsg);
                }
            }
        }

        // 创建"群聊已创建"系统消息
        {
            Message sysMsg = new Message();
            sysMsg.setSessionId(groupSessionId);
            sysMsg.setSenderId(userId);
            sysMsg.setSenderType(SenderType.SYSTEM.ordinal());
            sysMsg.setTargetId(group.getId());
            sysMsg.setTargetType(1);
            sysMsg.setType(MessageType.SYSTEM.ordinal());
            final String groupName = group.getName() != null ? group.getName() : "未命名群聊";
            sysMsg.setContent(userService.findById(userId).getUsername() + " 创建了" + groupName);
            messageMapper.insert(sysMsg);
        }

        log.info("群组创建成功: groupId={}, name={}, ownerId={}, memberCount={}",
                group.getId(), request.getName(), userId, memberIds.size() + 1);

        // 缓存群成员列表
        List<Long> allMembers = new ArrayList<>(memberIds);
        allMembers.add(userId);
        groupCacheService.setMemberIds(group.getId(), allMembers);
        groupCacheService.setMemberCount(group.getId(), allMembers.size());

        return CreateGroupResponse.builder()
                .groupId(group.getId())
                .name(group.getName())
                .build();
    }

    /**
     * 获取用户加入的群组列表（分页）
     *
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页数量
     * @return 分页结果
     */
    public PageResult<GroupResponse> getGroupList(final Long userId, final int page, final int size) {
        // 1. 查询用户加入的群组ID列表
        List<Long> groupIds = groupMemberMapper.selectGroupIdsByUserId(userId);
        if (groupIds.isEmpty()) {
            return PageResult.of(Collections.emptyList(), 0, page, size);
        }

        // 2. 分页查询群组信息
        LambdaQueryWrapper<GroupEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(GroupEntity::getId, groupIds)
                .eq(GroupEntity::getIsDeleted, 0)
                .orderByDesc(GroupEntity::getCreateTime);

        long total = groupMapper.selectCount(wrapper);
        int offset = (page - 1) * size;
        List<GroupEntity> groups = groupMapper.selectList(
                wrapper.last("LIMIT " + offset + ", " + size));

        // 3. 批量查询成员数量
        List<GroupResponse> list = groups.stream()
                .map(this::toGroupResponse)
                .toList();

        return PageResult.of(list, total, page, size);
    }

    /**
     * 获取群组成员列表（分页）
     *
     * @param groupId 群组ID
     * @param page    页码
     * @param size    每页数量
     * @return 分页结果
     */
    public PageResult<GroupMemberResponse> getGroupMembers(final Long groupId, final int page, final int size) {
        GroupEntity group = findGroupById(groupId);

        LambdaQueryWrapper<GroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getIsDeleted, 0)
                .orderByAsc(GroupMember::getRole)
                .orderByAsc(GroupMember::getCreateTime);

        long total = groupMemberMapper.selectCount(wrapper);
        int offset = (page - 1) * size;
        List<GroupMember> members = groupMemberMapper.selectList(
                wrapper.last("LIMIT " + offset + ", " + size));

        if (members.isEmpty()) {
            return PageResult.of(Collections.emptyList(), total, page, size);
        }

        // 批量查询用户信息
        Set<Long> userIds = members.stream()
                .map(GroupMember::getUserId)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userService.findByIds(userIds);

        List<GroupMemberResponse> list = members.stream()
                .map(member -> toGroupMemberResponse(member, userMap))
                .toList();

        return PageResult.of(list, total, page, size);
    }

    /**
     * 修改群信息
     *
     * <p>
     * 仅群主可修改。
     * </p>
     *
     * @param userId  当前用户ID
     * @param groupId 群组ID
     * @param request 修改请求
     */
    @Transactional
    public void updateGroup(final Long userId, final Long groupId, final UpdateGroupRequest request) {
        GroupEntity group = findGroupById(groupId);

        // 仅群主可修改
        if (!group.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        boolean changed = false;
        if (request.getName() != null) {
            group.setName(request.getName());
            changed = true;
        }
        if (request.getAnnouncement() != null) {
            group.setAnnouncement(request.getAnnouncement());
            changed = true;
        }
        if (request.getAnnouncementPinned() != null) {
            group.setAnnouncementPinned(request.getAnnouncementPinned());
            changed = true;
        }

        if (changed) {
            groupMapper.updateById(group);
            log.info("群信息已修改: groupId={}, userId={}", groupId, userId);
        }
    }

    /**
     * 邀请成员加入群组
     *
     * <p>
     * 群成员均可邀请，需检查群成员上限和被邀请人是否已在群中。
     * </p>
     *
     * @param userId  当前用户ID
     * @param groupId 群组ID
     * @param request 邀请请求
     */
    @Transactional
    public void inviteMembers(final Long userId, final Long groupId, final InviteMemberRequest request) {
        GroupEntity group = findGroupById(groupId);

        // 验证邀请者是否为群成员
        checkMember(groupId, userId);

        // 验证被邀请用户是否存在
        Set<Long> inviteUserIds = request.getUserIds().stream()
                .filter(id -> !id.equals(userId))
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userService.findByIds(inviteUserIds);
        for (Long inviteUserId : inviteUserIds) {
            if (!userMap.containsKey(inviteUserId)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在: " + inviteUserId);
            }
        }

        // 检查群成员上限
        int currentCount = groupMemberMapper.countMembers(groupId);
        if (currentCount + inviteUserIds.size() > group.getMaxMemberCount()) {
            throw new BusinessException(ErrorCode.GROUP_MEMBER_FULL);
        }

        // 批量添加成员（跳过已在群中的用户），并发送系统消息
        final String groupSessionId = "g_" + groupId;
        for (Long inviteUserId : inviteUserIds) {
            // 检查用户是否已在群中（is_deleted=0）
            if (isMember(groupId, inviteUserId)) {
                log.info("用户已在群中，跳过: groupId={}, userId={}", groupId, inviteUserId);
                continue;
            }

            // 检查用户是否曾经加入过群但已退出（is_deleted=1）
            GroupMember existingMember = groupMemberMapper.selectByGroupIdAndUserIdIncludeDeleted(groupId,
                    inviteUserId);
            if (existingMember != null) {
                // 恢复已退出的成员记录
                groupMemberMapper.restoreMember(groupId, inviteUserId);
                log.info("成员已恢复: groupId={}, userId={}", groupId, inviteUserId);
            } else {
                // 新成员，插入新记录
                GroupMember member = new GroupMember();
                member.setGroupId(groupId);
                member.setUserId(inviteUserId);
                member.setRole(GroupRole.MEMBER.getCode());
                groupMemberMapper.insert(member);
                log.info("新成员已加入: groupId={}, userId={}", groupId, inviteUserId);
            }

            // 创建系统消息：XXX 已加入群聊
            User invitee = userMap.get(inviteUserId);
            if (invitee != null) {
                Message sysMsg = new Message();
                sysMsg.setSessionId(groupSessionId);
                sysMsg.setSenderId(inviteUserId);
                sysMsg.setSenderType(SenderType.SYSTEM.ordinal());
                sysMsg.setTargetId(groupId);
                sysMsg.setTargetType(1); // GROUP
                sysMsg.setType(MessageType.SYSTEM.ordinal());
                sysMsg.setContent(invitee.getUsername() + " 已加入群聊");
                messageMapper.insert(sysMsg);
            }
        }

        // 更新群成员缓存
        for (Long inviteUserId : inviteUserIds) {
            groupCacheService.addMember(groupId, inviteUserId);
        }

        log.info("成员邀请成功: groupId={}, inviter={}, invitees={}",
                groupId, userId, inviteUserIds);
    }

    /**
     * 移除群成员
     *
     * <p>
     * 仅群主和管理员可移除普通成员，群主不可被移除。
     * </p>
     *
     * @param userId       当前用户ID
     * @param groupId      群组ID
     * @param targetUserId 被移除的用户ID
     */
    @Transactional
    public void removeMember(final Long userId, final Long groupId, final Long targetUserId) {
        GroupEntity group = findGroupById(groupId);

        // 不能移除群主
        if (group.getOwnerId().equals(targetUserId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "不能移除群主");
        }

        // 检查操作者权限（群主或管理员）
        Integer operatorRole = groupMemberMapper.selectRoleByGroupIdAndUserId(groupId, userId);
        if (operatorRole == null || operatorRole < GroupRole.ADMIN.getCode()) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        // 管理员只能移除普通成员，不能移除其他管理员
        if (operatorRole == GroupRole.ADMIN.getCode()) {
            Integer targetRole = groupMemberMapper.selectRoleByGroupIdAndUserId(groupId, targetUserId);
            if (targetRole != null && targetRole > GroupRole.MEMBER.getCode()) {
                throw new BusinessException(ErrorCode.NO_PERMISSION, "无权移除管理员");
            }
        }

        // 获取被移除用户的信息
        User targetUser = userService.findById(targetUserId);
        if (targetUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        // 移除成员
        LambdaQueryWrapper<GroupMember> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, targetUserId);
        groupMemberMapper.delete(deleteWrapper);

        // 创建系统消息：XXX 已被移出群聊
        final String groupSessionId = "g_" + groupId;
        Message sysMsg = new Message();
        sysMsg.setSessionId(groupSessionId);
        sysMsg.setSenderId(targetUserId);
        sysMsg.setSenderType(SenderType.SYSTEM.ordinal());
        sysMsg.setTargetId(groupId);
        sysMsg.setTargetType(1); // GROUP
        sysMsg.setType(MessageType.SYSTEM.ordinal());
        sysMsg.setContent(targetUser.getUsername() + " 已被移出群聊");
        messageMapper.insert(sysMsg);

        // 更新缓存
        groupCacheService.removeMember(groupId, targetUserId);

        log.info("成员已移除: groupId={}, targetUserId={}, operatorId={}",
                groupId, targetUserId, userId);
    }

    /**
     * 退出群组
     *
     * <p>
     * 群主不可退出，需先转让群主或解散群组。
     * </p>
     *
     * @param userId  当前用户ID
     * @param groupId 群组ID
     */
    @Transactional
    public void leaveGroup(final Long userId, final Long groupId) {
        GroupEntity group = findGroupById(groupId);

        // 群主不能退出
        if (group.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "群主不可退出群组，请先转让群主");
        }

        // 获取退出用户的信息
        User leaveUser = userService.findById(userId);
        if (leaveUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        // 删除成员记录
        LambdaQueryWrapper<GroupMember> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, userId);
        groupMemberMapper.delete(deleteWrapper);

        // 更新缓存
        groupCacheService.removeMember(groupId, userId);

        // 创建系统消息：XXX 已退出群聊
        final String groupSessionId = "g_" + groupId;
        Message sysMsg = new Message();
        sysMsg.setSessionId(groupSessionId);
        sysMsg.setSenderId(userId);
        sysMsg.setSenderType(SenderType.SYSTEM.ordinal());
        sysMsg.setTargetId(groupId);
        sysMsg.setTargetType(1); // GROUP
        sysMsg.setType(MessageType.SYSTEM.ordinal());
        sysMsg.setContent(leaveUser.getUsername() + " 已退出群聊");
        messageMapper.insert(sysMsg);

        log.info("用户退出群组: groupId={}, userId={}", groupId, userId);
    }

    /**
     * 转让群主
     *
     * <p>
     * 仅群主可操作。转让后原群主自动变为普通成员，被转让者变为群主。
     * </p>
     *
     * @param userId       当前用户ID（必须是群主）
     * @param groupId      群组ID
     * @param targetUserId 目标用户ID
     */
    @Transactional
    public void transferOwner(final Long userId, final Long groupId, final Long targetUserId) {
        GroupEntity group = findGroupById(groupId);

        // 仅群主可转让
        if (!group.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "仅群主可转让群主");
        }

        // 校验目标用户是否为群成员
        Integer targetRole = groupMemberMapper.selectRoleByGroupIdAndUserId(groupId, targetUserId);
        if (targetRole == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "目标用户不在群中");
        }

        // 转让群主：更新群 owner_id
        group.setOwnerId(targetUserId);
        groupMapper.updateById(group);

        // 原群主角色改为 MEMBER
        GroupMember oldOwner = groupMemberMapper.selectByGroupIdAndUserId(groupId, userId);
        if (oldOwner != null) {
            oldOwner.setRole(GroupRole.MEMBER.getCode());
            groupMemberMapper.updateById(oldOwner);
        }

        // 目标用户角色改为 OWNER
        GroupMember newOwner = groupMemberMapper.selectByGroupIdAndUserId(groupId, targetUserId);
        if (newOwner != null) {
            newOwner.setRole(GroupRole.OWNER.getCode());
            groupMemberMapper.updateById(newOwner);
        }

        log.info("群主转让成功: groupId={}, oldOwnerId={}, newOwnerId={}",
                groupId, userId, targetUserId);
    }

    /**
     * 解散群组
     *
     * <p>
     * 仅群主可操作。逻辑删除群组及所有成员记录。
     * </p>
     *
     * @param userId  当前用户ID（必须是群主）
     * @param groupId 群组ID
     */
    @Transactional
    public void dismissGroup(final Long userId, final Long groupId) {
        GroupEntity group = findGroupById(groupId);

        // 仅群主可解散
        if (!group.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "仅群主可解散群组");
        }

        // 逻辑删除群组
        group.setIsDeleted(1);
        groupMapper.updateById(group);

        // 逻辑删除所有群成员
        groupMemberMapper.logicalDeleteByGroupId(groupId);

        // 删除群缓存
        groupCacheService.deleteGroupCache(groupId);

        log.info("群组已解散: groupId={}, ownerId={}", groupId, userId);
    }

    /**
     * 设置/取消管理员
     *
     * <p>
     * 仅群主可操作。通过 action 参数控制设置或取消。
     * </p>
     *
     * @param userId       当前用户ID（必须是群主）
     * @param groupId      群组ID
     * @param targetUserId 目标用户ID
     * @param action       SET 设为管理员，REMOVE 取消管理员
     */
    @Transactional
    public void setAdmin(final Long userId, final Long groupId, final Long targetUserId, final String action) {
        GroupEntity group = findGroupById(groupId);

        // 仅群主可操作
        if (!group.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "仅群主可设置管理员");
        }

        // 校验目标用户是否为群成员
        GroupMember targetMember = groupMemberMapper.selectByGroupIdAndUserId(groupId, targetUserId);
        if (targetMember == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "目标用户不在群中");
        }

        // 不能对群主操作
        if (group.getOwnerId().equals(targetUserId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "不能修改群主的角色");
        }

        if ("SET".equalsIgnoreCase(action)) {
            // 已经是管理员则跳过
            if (targetMember.getRole() == GroupRole.ADMIN.getCode()) {
                log.warn("用户已是管理员: groupId={}, targetUserId={}", groupId, targetUserId);
                return;
            }
            targetMember.setRole(GroupRole.ADMIN.getCode());
            log.info("管理员设置成功: groupId={}, targetUserId={}", groupId, targetUserId);
        } else if ("REMOVE".equalsIgnoreCase(action)) {
            // 已经是普通成员则跳过
            if (targetMember.getRole() == GroupRole.MEMBER.getCode()) {
                log.warn("用户已是普通成员: groupId={}, targetUserId={}", groupId, targetUserId);
                return;
            }
            targetMember.setRole(GroupRole.MEMBER.getCode());
            log.info("管理员取消成功: groupId={}, targetUserId={}", groupId, targetUserId);
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的操作类型: " + action);
        }

        groupMemberMapper.updateById(targetMember);
    }

    /**
     * 设置群成员昵称
     *
     * @param userId   当前用户ID
     * @param groupId  群组ID
     * @param nickname 群昵称
     */
    @Transactional
    public void setNickname(final Long userId, final Long groupId, final String nickname) {
        // 校验用户是否为群成员
        GroupMember member = groupMemberMapper.selectByGroupIdAndUserId(groupId, userId);
        if (member == null) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "不在群中");
        }

        member.setNickname(nickname);
        groupMemberMapper.updateById(member);

        log.info("群昵称设置成功: groupId={}, userId={}, nickname={}", groupId, userId, nickname);
    }

    /**
     * 按ID查找群组
     *
     * @param groupId 群组ID
     * @return 群组实体
     */
    private GroupEntity findGroupById(final Long groupId) {
        GroupEntity group = groupMapper.selectById(groupId);
        if (group == null || group.getIsDeleted() != null && group.getIsDeleted() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "群组不存在");
        }
        return group;
    }

    /**
     * 检查用户是否为群成员
     *
     * @param groupId 群组ID
     * @param userId  用户ID
     */
    private void checkMember(final Long groupId, final Long userId) {
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
    private boolean isMember(final Long groupId, final Long userId) {
        return groupMemberMapper.selectRoleByGroupIdAndUserId(groupId, userId) != null;
    }

    /**
     * 转换为群组响应
     */
    private GroupResponse toGroupResponse(final GroupEntity group) {
        int memberCount = groupMemberMapper.countMembers(group.getId());
        return GroupResponse.builder()
                .groupId(group.getId())
                .name(group.getName())
                .avatar(group.getAvatar())
                .memberCount(memberCount)
                .ownerId(group.getOwnerId())
                .build();
    }

    /**
     * 转换为群成员响应
     */
    private GroupMemberResponse toGroupMemberResponse(final GroupMember member, final Map<Long, User> userMap) {
        User user = userMap.get(member.getUserId());
        String roleStr = switch (member.getRole()) {
            case 2 -> "OWNER";
            case 1 -> "ADMIN";
            default -> "MEMBER";
        };
        return GroupMemberResponse.builder()
                .userId(member.getUserId())
                .username(user != null ? user.getUsername() : null)
                .avatar(user != null ? user.getAvatar() : null)
                .role(roleStr)
                .joinTime(member.getCreateTime())
                .build();
    }
}
