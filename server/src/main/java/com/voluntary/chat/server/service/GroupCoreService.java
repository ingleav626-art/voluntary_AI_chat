package com.voluntary.chat.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voluntary.chat.common.dto.PageResult;
import com.voluntary.chat.common.enums.GroupRole;
import com.voluntary.chat.common.enums.MessageType;
import com.voluntary.chat.common.enums.SenderType;
import com.voluntary.chat.common.exception.BusinessException;
import com.voluntary.chat.common.exception.ErrorCode;
import com.voluntary.chat.server.dto.request.CreateGroupRequest;
import com.voluntary.chat.server.dto.request.UpdateGroupRequest;
import com.voluntary.chat.server.dto.response.CreateGroupResponse;
import com.voluntary.chat.server.dto.response.GroupResponse;
import com.voluntary.chat.server.entity.GroupEntity;
import com.voluntary.chat.server.entity.GroupMember;
import com.voluntary.chat.server.entity.Message;
import com.voluntary.chat.server.entity.User;
import com.voluntary.chat.server.mapper.GroupMapper;
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
 * 群组核心服务
 *
 * <p>负责群组的基本操作：创建、查询、修改、解散。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupCoreService {

    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final UserService userService;
    private final MessageMapper messageMapper;

    /** 默认最大成员数 */
    private static final int DEFAULT_MAX_MEMBER_COUNT = 200;

    /**
     * 创建群组
     *
     * <p>创建者自动成为群主，memberIds 中的用户自动成为普通成员。</p>
     *
     * @param userId  创建者ID
     * @param request 创建请求
     * @return 创建结果
     */
    @Transactional
    public CreateGroupResponse createGroup(final Long userId, final CreateGroupRequest request) {
        // 1. 创建群组实体
        final GroupEntity group = createGroupEntity(userId, request);

        // 2. 添加创建者为群主
        addOwner(group.getId(), userId);

        // 3. 添加初始成员（去重，排除创建者）
        final Set<Long> memberIds = filterInitialMembers(userId, request.getMemberIds());

        if (!memberIds.isEmpty()) {
            addInitialMembers(group.getId(), userId, memberIds);
        }

        // 4. 创建系统消息
        createSystemMessages(group.getId(), userId, group.getName(), memberIds);

        log.info("群组创建成功: groupId={}, name={}, ownerId={}, memberCount={}",
                group.getId(), request.getName(), userId, memberIds.size() + 1);
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
        final List<Long> groupIds = groupMemberMapper.selectGroupIdsByUserId(userId);
        if (groupIds.isEmpty()) {
            return PageResult.of(Collections.emptyList(), 0, page, size);
        }

        final LambdaQueryWrapper<GroupEntity> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.in(GroupEntity::getId, groupIds)
                .eq(GroupEntity::getIsDeleted, 0);
        final long total = groupMapper.selectCount(countWrapper);

        final LambdaQueryWrapper<GroupEntity> listWrapper = new LambdaQueryWrapper<>();
        listWrapper.in(GroupEntity::getId, groupIds)
                .eq(GroupEntity::getIsDeleted, 0)
                .orderByDesc(GroupEntity::getCreateTime);
        final int offset = (page - 1) * size;
        final List<GroupEntity> groups = groupMapper.selectList(
                listWrapper.last("LIMIT " + offset + ", " + size));

        final List<GroupResponse> list = groups.stream()
                .map(this::toGroupResponse)
                .toList();

        return PageResult.of(list, total, page, size);
    }

    /**
     * 获取群组详情
     *
     * @param userId  当前用户ID
     * @param groupId 群组ID
     * @return 群组详情
     */
    public GroupResponse getGroupDetail(final Long userId, final Long groupId) {
        final GroupEntity group = findGroupById(groupId);

        // 验证用户是否是该群成员
        final LambdaQueryWrapper<GroupMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, userId)
                .eq(GroupMember::getIsDeleted, 0);
        final GroupMember member = groupMemberMapper.selectOne(memberWrapper);

        if (member == null) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "您不是该群成员");
        }

        return toGroupResponse(group);
    }

    /**
     * 修改群信息
     *
     * <p>仅群主可修改。</p>
     *
     * @param userId  当前用户ID
     * @param groupId 群组ID
     * @param request 修改请求
     */
    @Transactional
    public void updateGroup(final Long userId, final Long groupId, final UpdateGroupRequest request) {
        final GroupEntity group = findGroupById(groupId);

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
        if (request.getAvatar() != null) {
            group.setAvatar(request.getAvatar());
            changed = true;
        }

        if (changed) {
            groupMapper.updateById(group);
            log.info("群信息已修改: groupId={}, userId={}", groupId, userId);
        }
    }

    /**
     * 解散群组
     *
     * <p>仅群主可操作。逻辑删除群组及所有成员记录。</p>
     *
     * @param userId   当前用户ID（必须是群主）
     * @param groupId  群组ID
     */
    @Transactional
    public void dismissGroup(final Long userId, final Long groupId) {
        final GroupEntity group = findGroupById(groupId);

        if (!group.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "仅群主可解散群组");
        }

        group.setIsDeleted(1);
        groupMapper.updateById(group);

        groupMemberMapper.logicalDeleteByGroupId(groupId);

        log.info("群组已解散: groupId={}, ownerId={}", groupId, userId);
    }

    /**
     * 按ID查找群组
     *
     * @param groupId 群组ID
     * @return 群组实体
     */
    public GroupEntity findGroupById(final Long groupId) {
        final GroupEntity group = groupMapper.selectById(groupId);
        if (group == null || group.getIsDeleted() != null && group.getIsDeleted() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "群组不存在");
        }
        return group;
    }

    /**
     * 创建群组实体
     */
    private GroupEntity createGroupEntity(final Long userId, final CreateGroupRequest request) {
        final GroupEntity group = new GroupEntity();
        group.setName(request.getName());
        group.setOwnerId(userId);
        group.setMaxMemberCount(DEFAULT_MAX_MEMBER_COUNT);
        groupMapper.insert(group);
        return group;
    }

    /**
     * 添加群主
     */
    private void addOwner(final Long groupId, final Long userId) {
        final GroupMember owner = new GroupMember();
        owner.setGroupId(groupId);
        owner.setUserId(userId);
        owner.setRole(GroupRole.OWNER.getCode());
        groupMemberMapper.insert(owner);
    }

    /**
     * 过滤初始成员（排除创建者）
     */
    private Set<Long> filterInitialMembers(final Long userId, final List<Long> rawMemberIds) {
        return rawMemberIds == null
                ? Collections.emptySet()
                : rawMemberIds.stream()
                        .filter(id -> !id.equals(userId))
                        .collect(Collectors.toSet());
    }

    /**
     * 添加初始成员并发送系统消息
     */
    private void addInitialMembers(final Long groupId, final Long creatorId, final Set<Long> memberIds) {
        final Map<Long, User> userMap = userService.findByIds(memberIds);
        final String groupSessionId = "g_" + groupId;

        for (final Long memberId : memberIds) {
            if (!userMap.containsKey(memberId)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在: " + memberId);
            }

            final GroupMember member = new GroupMember();
            member.setGroupId(groupId);
            member.setUserId(memberId);
            member.setRole(GroupRole.MEMBER.getCode());
            groupMemberMapper.insert(member);

            // 创建系统消息：XXX 已加入群聊
            final User memberUser = userMap.get(memberId);
            if (memberUser != null) {
                createSystemMessage(groupSessionId, memberId, groupId,
                        memberUser.getUsername() + " 已加入群聊");
            }
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
     * 创建系统消息（创建群组和成员加入）
     */
    private void createSystemMessages(final Long groupId, final Long creatorId,
                                       final String groupName, final Set<Long> memberIds) {
        final String groupSessionId = "g_" + groupId;

        // 创建"群聊已创建"系统消息
        final User creator = userService.findById(creatorId);
        if (creator != null) {
            final String name = groupName != null ? groupName : "未命名群聊";
            createSystemMessage(groupSessionId, creatorId, groupId,
                    creator.getUsername() + " 创建了" + name);
        }
    }

    /**
     * 转换为群组响应
     */
    private GroupResponse toGroupResponse(final GroupEntity group) {
        final int memberCount = groupMemberMapper.countMembers(group.getId());
        return GroupResponse.builder()
                .groupId(group.getId())
                .name(group.getName())
                .avatar(group.getAvatar())
                .memberCount(memberCount)
                .ownerId(group.getOwnerId())
                .announcement(group.getAnnouncement())
                .announcementPinned(group.getAnnouncementPinned())
                .build();
    }
}