package org.example.client.service;

import java.net.http.HttpRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.example.client.model.ApiResponse;
import org.example.client.model.CreateGroupRequest;
import org.example.client.model.CreateGroupResponse;
import org.example.client.model.GroupInfo;
import org.example.client.model.GroupMemberInfo;
import org.example.client.model.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 群组服务
 *
 * <p>封装群组列表、创建群组、群成员管理等 REST 接口。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class GroupService extends BaseHttpService {

    private static final Logger LOG = LoggerFactory.getLogger(GroupService.class);

    private static final GroupService INSTANCE = new GroupService();

    /** 群组模块基础路径 */
    private static final String GROUP_PATH = "/group";

    private GroupService() {
        // 单例模式
    }

    public static GroupService getInstance() {
        return INSTANCE;
    }

    /**
     * 获取群组列表
     *
     * @param page 页码
     * @param size 每页数量
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<PageResult<GroupInfo>>> getGroupList(final int page, final int size) {
        // 检查登录状态
        if (!checkLoginStatus()) {
            LOG.warn("用户未登录，无法获取群组列表");
            return CompletableFuture.completedFuture(createNotLoggedInResponse());
        }

        final String path = GROUP_PATH + "/list?page=" + page + "&size=" + size;
        final HttpRequest request = buildGetRequest(path).build();

        LOG.info("获取群组列表: page={}, size={}", page, size);

        return sendRequest(request, getTypeFactory().constructParametricType(
                ApiResponse.class,
                getTypeFactory().constructParametricType(PageResult.class, GroupInfo.class)));
    }

    /**
     * 创建群组
     *
     * @param request 创建请求
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<CreateGroupResponse>> createGroup(final CreateGroupRequest request) {
        // 检查登录状态
        if (!checkLoginStatus()) {
            LOG.warn("用户未登录，无法创建群组");
            return CompletableFuture.completedFuture(createNotLoggedInResponse());
        }

        final HttpRequest httpRequest = buildPostRequest(GROUP_PATH + "/create", request).build();

        LOG.info("创建群组: name={}, memberCount={}", request.getName(),
                request.getMemberIds() != null ? request.getMemberIds().size() : 0);

        return sendRequest(httpRequest, getTypeFactory().constructParametricType(
                ApiResponse.class, CreateGroupResponse.class));
    }

    /**
     * 获取群成员列表
     *
     * @param groupId 群组ID
     * @param page    页码
     * @param size    每页数量
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<PageResult<GroupMemberInfo>>> getGroupMembers(
            final Long groupId, final int page, final int size) {
        final String path = GROUP_PATH + "/" + groupId + "/members?page=" + page + "&size=" + size;
        final HttpRequest request = buildGetRequest(path).build();

        LOG.info("获取群成员: groupId={}", groupId);

        return sendRequest(request, getTypeFactory().constructParametricType(
                ApiResponse.class,
                getTypeFactory().constructParametricType(PageResult.class, GroupMemberInfo.class)));
    }

    /**
     * 邀请成员
     *
     * @param groupId 群组ID
     * @param userIds 用户ID列表
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<Void>> inviteMembers(final Long groupId, final List<Long> userIds) {
        final String path = GROUP_PATH + "/" + groupId + "/invite";
        final Map<String, Object> body = new HashMap<>();
        body.put("userIds", userIds);
        final HttpRequest httpRequest = buildPostRequest(path, body).build();

        LOG.info("邀请群成员: groupId={}, inviteCount={}", groupId, userIds.size());

        return sendRequest(httpRequest, getTypeFactory().constructParametricType(
                ApiResponse.class, Void.class));
    }

    /**
     * 移除群成员
     *
     * @param groupId      群组ID
     * @param targetUserId 目标用户ID
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<Void>> removeMember(final Long groupId, final Long targetUserId) {
        final String path = GROUP_PATH + "/" + groupId + "/members/" + targetUserId;
        final HttpRequest httpRequest = buildDeleteRequest(path).build();

        LOG.info("移除群成员: groupId={}, targetUserId={}", groupId, targetUserId);

        return sendRequest(httpRequest, getTypeFactory().constructParametricType(
                ApiResponse.class, Void.class));
    }

    /**
     * 退出群组
     *
     * @param groupId 群组ID
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<Void>> leaveGroup(final Long groupId) {
        final String path = GROUP_PATH + "/" + groupId + "/leave";
        final HttpRequest httpRequest = buildPostRequest(path, new HashMap<>()).build();

        LOG.info("退出群组: groupId={}", groupId);

        return sendRequest(httpRequest, getTypeFactory().constructParametricType(
                ApiResponse.class, Void.class));
    }

    /**
     * 转让群主
     *
     * @param groupId      群组ID
     * @param targetUserId 目标用户ID
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<Void>> transferOwner(final Long groupId, final Long targetUserId) {
        final String path = GROUP_PATH + "/" + groupId + "/transfer";
        final Map<String, Object> body = new HashMap<>();
        body.put("targetUserId", targetUserId);
        final HttpRequest httpRequest = buildPostRequest(path, body).build();

        LOG.info("转让群主: groupId={}, targetUserId={}", groupId, targetUserId);

        return sendRequest(httpRequest, getTypeFactory().constructParametricType(
                ApiResponse.class, Void.class));
    }

    /**
     * 解散群组
     *
     * @param groupId 群组ID
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<Void>> dismissGroup(final Long groupId) {
        final String path = GROUP_PATH + "/" + groupId;
        final HttpRequest httpRequest = buildDeleteRequest(path).build();

        LOG.info("解散群组: groupId={}", groupId);

        return sendRequest(httpRequest, getTypeFactory().constructParametricType(
                ApiResponse.class, Void.class));
    }

    /**
     * 设置/取消管理员
     *
     * @param groupId      群组ID
     * @param targetUserId 目标用户ID
     * @param action       SET 或 REMOVE
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<Void>> setAdmin(
            final Long groupId, final Long targetUserId, final String action) {
        final String path = GROUP_PATH + "/" + groupId + "/admin";
        final Map<String, Object> body = new HashMap<>();
        body.put("targetUserId", targetUserId);
        body.put("action", action);
        final HttpRequest httpRequest = buildPostRequest(path, body).build();

        LOG.info("设置/取消管理员: groupId={}, targetUserId={}, action={}", groupId, targetUserId, action);

        return sendRequest(httpRequest, getTypeFactory().constructParametricType(
                ApiResponse.class, Void.class));
    }

    /**
     * 修改群信息（仅群主可操作）
     *
     * @param groupId      群组ID
     * @param name         群名称
     * @param announcement 群公告
     * @param announcementPinned 公告是否置顶（null 表示不修改）
     * @param avatar       群头像URL（可选）
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<Void>> updateGroup(
            final Long groupId, final String name,
            final String announcement, final Boolean announcementPinned,
            final String avatar) {
        final String path = GROUP_PATH + "/" + groupId;
        final Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("announcement", announcement);
        if (announcementPinned != null) {
            body.put("announcementPinned", announcementPinned);
        }
        if (avatar != null && !avatar.isEmpty()) {
            body.put("avatar", avatar);
        }
        final HttpRequest httpRequest = buildPutRequest(path, body).build();

        LOG.info("修改群信息: groupId={}, name={}", groupId, name);

        return sendRequest(httpRequest, getTypeFactory().constructParametricType(
                ApiResponse.class, Void.class));
    }

    /**
     * 设置群昵称
     *
     * @param groupId  群组ID
     * @param nickname 昵称
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<Void>> setNickname(final Long groupId, final String nickname) {
        final String path = GROUP_PATH + "/" + groupId + "/nickname";
        final Map<String, Object> body = new HashMap<>();
        body.put("nickname", nickname);
        final HttpRequest httpRequest = buildPutRequest(path, body).build();

        LOG.info("设置群昵称: groupId={}, nickname={}", groupId, nickname);

        return sendRequest(httpRequest, getTypeFactory().constructParametricType(
                ApiResponse.class, Void.class));
    }
}