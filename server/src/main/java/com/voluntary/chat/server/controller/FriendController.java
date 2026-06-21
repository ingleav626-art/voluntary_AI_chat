package com.voluntary.chat.server.controller;

import com.voluntary.chat.server.common.ApiResult;
import com.voluntary.chat.server.dto.request.FriendApplyHandleRequest;
import com.voluntary.chat.server.dto.request.FriendApplyRequest;
import com.voluntary.chat.server.dto.response.FriendApplyResponse;
import com.voluntary.chat.server.dto.response.FriendResponse;
import com.voluntary.chat.server.security.SecurityUtils;
import com.voluntary.chat.server.service.FriendService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 好友模块接口
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/friend")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    /**
     * 发送好友申请
     */
    @PostMapping("/apply")
    public ApiResult<Void> applyFriend(@Valid @RequestBody FriendApplyRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        friendService.applyFriend(userId, request);
        return ApiResult.ok("申请已发送", null);
    }

    /**
     * 获取好友申请列表
     */
    @GetMapping("/apply/list")
    public ApiResult<List<FriendApplyResponse>> getApplyList() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<FriendApplyResponse> list = friendService.getApplyList(userId);
        return ApiResult.ok(list);
    }

    /**
     * 处理好友申请
     */
    @PostMapping("/apply/{applyId}/handle")
    public ApiResult<Void> handleApply(@PathVariable Long applyId,
                                       @Valid @RequestBody FriendApplyHandleRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        friendService.handleApply(userId, applyId, request.getAction());
        return ApiResult.ok("ACCEPT".equals(request.getAction()) ? "已同意" : "已拒绝", null);
    }

    /**
     * 获取好友列表
     */
    @GetMapping("/list")
    public ApiResult<List<FriendResponse>> getFriendList() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<FriendResponse> list = friendService.getFriendList(userId);
        return ApiResult.ok(list);
    }

    /**
     * 删除好友
     */
    @DeleteMapping("/{friendId}")
    public ApiResult<Void> deleteFriend(@PathVariable Long friendId) {
        Long userId = SecurityUtils.getCurrentUserId();
        friendService.deleteFriend(userId, friendId);
        return ApiResult.ok("已删除", null);
    }
}
