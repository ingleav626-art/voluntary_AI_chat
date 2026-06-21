package com.voluntary.chat.server.controller;

import com.voluntary.chat.common.dto.PageResult;
import com.voluntary.chat.server.common.ApiResult;
import com.voluntary.chat.server.dto.request.AdminActionRequest;
import com.voluntary.chat.server.dto.request.CreateGroupRequest;
import com.voluntary.chat.server.dto.request.InviteMemberRequest;
import com.voluntary.chat.server.dto.request.SetNicknameRequest;
import com.voluntary.chat.server.dto.request.TransferOwnerRequest;
import com.voluntary.chat.server.dto.request.UpdateGroupRequest;
import com.voluntary.chat.server.dto.response.CreateGroupResponse;
import com.voluntary.chat.server.dto.response.GroupMemberResponse;
import com.voluntary.chat.server.dto.response.GroupResponse;
import com.voluntary.chat.server.security.SecurityUtils;
import com.voluntary.chat.server.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 群组模块接口
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/group")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    /**
     * 创建群组
     */
    @PostMapping("/create")
    public ApiResult<CreateGroupResponse> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        CreateGroupResponse result = groupService.createGroup(userId, request);
        return ApiResult.ok("创建成功", result);
    }

    /**
     * 获取群列表
     */
    @GetMapping("/list")
    public ApiResult<PageResult<GroupResponse>> getGroupList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        PageResult<GroupResponse> result = groupService.getGroupList(userId, page, size);
        return ApiResult.ok(result);
    }

    /**
     * 获取群成员
     */
    @GetMapping("/{groupId}/members")
    public ApiResult<PageResult<GroupMemberResponse>> getGroupMembers(
            @PathVariable Long groupId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        PageResult<GroupMemberResponse> result = groupService.getGroupMembers(groupId, page, size);
        return ApiResult.ok(result);
    }

    /**
     * 修改群信息
     */
    @PutMapping("/{groupId}")
    public ApiResult<Void> updateGroup(
            @PathVariable Long groupId,
            @Valid @RequestBody UpdateGroupRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        groupService.updateGroup(userId, groupId, request);
        return ApiResult.ok("修改成功", null);
    }

    /**
     * 邀请成员
     */
    @PostMapping("/{groupId}/invite")
    public ApiResult<Void> inviteMembers(
            @PathVariable Long groupId,
            @Valid @RequestBody InviteMemberRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        groupService.inviteMembers(userId, groupId, request);
        return ApiResult.ok("邀请成功", null);
    }

    /**
     * 移除成员
     */
    @DeleteMapping("/{groupId}/members/{targetUserId}")
    public ApiResult<Void> removeMember(
            @PathVariable Long groupId,
            @PathVariable Long targetUserId) {
        Long userId = SecurityUtils.getCurrentUserId();
        groupService.removeMember(userId, groupId, targetUserId);
        return ApiResult.ok("已移除", null);
    }

    /**
     * 退出群组
     */
    @PostMapping("/{groupId}/leave")
    public ApiResult<Void> leaveGroup(@PathVariable Long groupId) {
        Long userId = SecurityUtils.getCurrentUserId();
        groupService.leaveGroup(userId, groupId);
        return ApiResult.ok("已退出", null);
    }

    /**
     * 转让群主
     */
    @PostMapping("/{groupId}/transfer")
    public ApiResult<Void> transferOwner(
            @PathVariable Long groupId,
            @Valid @RequestBody TransferOwnerRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        groupService.transferOwner(userId, groupId, request.getTargetUserId());
        return ApiResult.ok("转让成功", null);
    }

    /**
     * 解散群组
     */
    @DeleteMapping("/{groupId}")
    public ApiResult<Void> dismissGroup(@PathVariable Long groupId) {
        Long userId = SecurityUtils.getCurrentUserId();
        groupService.dismissGroup(userId, groupId);
        return ApiResult.ok("群组已解散", null);
    }

    /**
     * 设置/取消管理员
     */
    @PostMapping("/{groupId}/admin")
    public ApiResult<Void> setAdmin(
            @PathVariable Long groupId,
            @Valid @RequestBody AdminActionRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        groupService.setAdmin(userId, groupId, request.getTargetUserId(), request.getAction());
        return ApiResult.ok("操作成功", null);
    }

    /**
     * 设置群成员昵称
     */
    @PutMapping("/{groupId}/nickname")
    public ApiResult<Void> setNickname(
            @PathVariable Long groupId,
            @Valid @RequestBody SetNicknameRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        groupService.setNickname(userId, groupId, request.getNickname());
        return ApiResult.ok("设置成功", null);
    }
}