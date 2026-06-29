package com.voluntary.chat.server.service;

import com.voluntary.chat.common.enums.GroupRole;
import com.voluntary.chat.common.exception.BusinessException;
import com.voluntary.chat.common.exception.ErrorCode;
import com.voluntary.chat.server.entity.GroupEntity;
import com.voluntary.chat.server.entity.GroupMember;
import com.voluntary.chat.server.mapper.GroupMapper;
import com.voluntary.chat.server.mapper.GroupMemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 群组权限服务
 *
 * <p>负责群组权限管理：转让群主、设置管理员、角色验证。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupRoleService {

    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final GroupCoreService groupCoreService;

    /**
     * 转让群主
     *
     * <p>仅群主可操作。转让后原群主自动变为普通成员，被转让者变为群主。</p>
     *
     * @param userId        当前用户ID（必须是群主）
     * @param groupId       群组ID
     * @param targetUserId  目标用户ID
     */
    @Transactional
    public void transferOwner(final Long userId, final Long groupId, final Long targetUserId) {
        final GroupEntity group = groupCoreService.findGroupById(groupId);

        if (!group.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "仅群主可转让群主");
        }

        final Integer targetRole = groupMemberMapper.selectRoleByGroupIdAndUserId(groupId, targetUserId);
        if (targetRole == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "目标用户不在群中");
        }

        // 更新群 owner_id
        group.setOwnerId(targetUserId);
        groupMapper.updateById(group);

        // 原群主角色改为 MEMBER
        final GroupMember oldOwner = groupMemberMapper.selectByGroupIdAndUserId(groupId, userId);
        if (oldOwner != null) {
            oldOwner.setRole(GroupRole.MEMBER.getCode());
            groupMemberMapper.updateById(oldOwner);
        }

        // 目标用户角色改为 OWNER
        final GroupMember newOwner = groupMemberMapper.selectByGroupIdAndUserId(groupId, targetUserId);
        if (newOwner != null) {
            newOwner.setRole(GroupRole.OWNER.getCode());
            groupMemberMapper.updateById(newOwner);
        }

        log.info("群主转让成功: groupId={}, oldOwnerId={}, newOwnerId={}",
                groupId, userId, targetUserId);
    }

    /**
     * 设置/取消管理员
     *
     * <p>仅群主可操作。通过 action 参数控制设置或取消。</p>
     *
     * @param userId        当前用户ID（必须是群主）
     * @param groupId       群组ID
     * @param targetUserId  目标用户ID
     * @param action        SET 设为管理员，REMOVE 取消管理员
     */
    @Transactional
    public void setAdmin(final Long userId, final Long groupId, final Long targetUserId, final String action) {
        final GroupEntity group = groupCoreService.findGroupById(groupId);

        if (!group.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "仅群主可设置管理员");
        }

        final GroupMember targetMember = groupMemberMapper.selectByGroupIdAndUserId(groupId, targetUserId);
        if (targetMember == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "目标用户不在群中");
        }

        if (group.getOwnerId().equals(targetUserId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "不能修改群主的角色");
        }

        if ("SET".equalsIgnoreCase(action)) {
            if (targetMember.getRole() == GroupRole.ADMIN.getCode()) {
                log.warn("用户已是管理员: groupId={}, targetUserId={}", groupId, targetUserId);
                return;
            }
            targetMember.setRole(GroupRole.ADMIN.getCode());
            log.info("管理员设置成功: groupId={}, targetUserId={}", groupId, targetUserId);
        } else if ("REMOVE".equalsIgnoreCase(action)) {
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
     * 获取用户在群组中的角色
     *
     * @param groupId 群组ID
     * @param userId  用户ID
     * @return 角色代码（null表示不在群中）
     */
    public Integer getMemberRole(final Long groupId, final Long userId) {
        return groupMemberMapper.selectRoleByGroupIdAndUserId(groupId, userId);
    }

    /**
     * 检查用户是否有管理权限（群主或管理员）
     *
     * @param groupId 群组ID
     * @param userId  用户ID
     * @return 是否有管理权限
     */
    public boolean hasAdminPermission(final Long groupId, final Long userId) {
        final Integer role = getMemberRole(groupId, userId);
        return role != null && role >= GroupRole.ADMIN.getCode();
    }

    /**
     * 检查用户是否为群主
     *
     * @param groupId 群组ID
     * @param userId  用户ID
     * @return 是否为群主
     */
    public boolean isOwner(final Long groupId, final Long userId) {
        final GroupEntity group = groupMapper.selectById(groupId);
        return group != null && group.getOwnerId().equals(userId);
    }
}