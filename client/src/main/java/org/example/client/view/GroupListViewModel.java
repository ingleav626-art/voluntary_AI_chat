package org.example.client.view;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.example.client.model.CreateGroupRequest;
import org.example.client.model.GroupInfo;
import org.example.client.model.GroupMemberInfo;
import org.example.client.model.LoginResponse;
import org.example.client.service.GroupService;
import org.example.client.util.TokenStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * 群组列表视图模型
 *
 * <p>
 * 管理群组列表、群成员列表、创建群组、退出/解散群组等操作。
 * </p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class GroupListViewModel {

    private static final Logger LOG = LoggerFactory.getLogger(GroupListViewModel.class);

    /** 群组事件监听器接口 */
    public interface GroupEventListener {
        /**
         * 群成员变更通知（加入、离开、角色变更）
         *
         * @param groupId 群组ID
         */
        void onMemberChanged(Long groupId);
    }

    /** 全局群组事件监听器，由 GroupController 注册 */
    private static GroupEventListener groupEventListener;

    /**
     * 设置全局群组事件监听器
     *
     * @param listener 监听器
     */
    public static void setGroupEventListener(final GroupEventListener listener) {
        groupEventListener = listener;
    }

    /**
     * 通知群成员变更事件（供 MainViewModel 调用）
     *
     * @param groupId 群组ID
     */
    public static void notifyMemberChanged(final Long groupId) {
        if (groupEventListener != null) {
            groupEventListener.onMemberChanged(groupId);
        }
    }

    /** 群组列表 */
    private final ListProperty<GroupInfo> groups = new SimpleListProperty<>(FXCollections.observableArrayList());

    /** 群成员列表 */
    private final ListProperty<GroupMemberInfo> members = new SimpleListProperty<>(FXCollections.observableArrayList());

    /** 加载状态 */
    private final BooleanProperty loading = new SimpleBooleanProperty(false);

    /** 错误消息 */
    private final StringProperty errorMessage = new SimpleStringProperty("");

    /** 成功消息 */
    private final StringProperty successMessage = new SimpleStringProperty("");

    /** 创建群组-群名称 */
    private final StringProperty groupName = new SimpleStringProperty("");

    /** 当前选中群组 */
    private final ObjectProperty<GroupInfo> selectedGroup = new SimpleObjectProperty<>();

    /** 当前登录用户ID */
    private Long currentUserId;

    /** 当前用户在选中群组中的角色 */
    private String currentUserRole;

    /** 待选中的群组ID（用于跳转后自动选中） */
    private Long pendingSelectGroupId;

    public GroupListViewModel() {
        // 从登录响应中获取当前用户ID，用于判断是否为群主
        final LoginResponse login = TokenStorage.load();
        if (login != null && login.getUser() != null) {
            this.currentUserId = login.getUser().getUserId();
        }
    }

    /**
     * 加载群组列表
     */
    public void loadGroups() {
        loading.set(true);
        errorMessage.set("");

        GroupService.getInstance().getGroupList(1, 100)
                .thenAccept(response -> Platform.runLater(() -> {
                    loading.set(false);

                    if (response != null && response.isSuccess() && response.getData() != null) {
                        final List<GroupInfo> list = response.getData().getList();
                        groups.setAll(list);

                        // 刷新后同步更新 selectedGroup，确保 ownerId 等字段为最新值
                        if (selectedGroup.get() != null) {
                            for (final GroupInfo g : list) {
                                if (selectedGroup.get().getGroupId().equals(g.getGroupId())) {
                                    selectedGroup.set(g);
                                    break;
                                }
                            }
                        }

                        // 检查是否有待选中的群组ID，如果有则自动选中
                        if (pendingSelectGroupId != null) {
                            for (final GroupInfo g : list) {
                                if (g.getGroupId().equals(pendingSelectGroupId)) {
                                    selectedGroup.set(g);
                                    // 清除待选中ID
                                    pendingSelectGroupId = null;
                                    LOG.info("自动选中群组: groupId={}, groupName={}", g.getGroupId(), g.getName());
                                    break;
                                }
                            }
                            // 如果未找到，清除待选中ID并记录警告
                            if (pendingSelectGroupId != null) {
                                LOG.warn("未找到待选中的群组ID: {}", pendingSelectGroupId);
                                pendingSelectGroupId = null;
                            }
                        }

                        LOG.info("群组列表加载成功: count={}", list.size());
                    } else {
                        final String msg = response != null ? response.getMessage() : "加载群组列表失败";
                        errorMessage.set(msg);
                        LOG.warn("群组列表加载失败: {}", msg);
                    }
                }));
    }

    /**
     * 设置待选中的群组ID（用于跳转后自动选中）
     *
     * @param groupId 群组ID
     */
    public void setPendingSelectGroupId(final Long groupId) {
        this.pendingSelectGroupId = groupId;
        LOG.info("设置待选中群组ID: {}", groupId);
    }

    /**
     * 创建群组
     */
    public void createGroup() {
        final String name = groupName.get();
        if (name == null || name.trim().isEmpty()) {
            errorMessage.set("请输入群名称");
            return;
        }

        final CreateGroupRequest request = new CreateGroupRequest();
        request.setName(name.trim());
        request.setMemberIds(List.of());

        loading.set(true);
        errorMessage.set("");

        GroupService.getInstance().createGroup(request)
                .thenAccept(response -> Platform.runLater(() -> {
                    loading.set(false);

                    if (response != null && response.isSuccess()) {
                        successMessage.set("群组创建成功");
                        groupName.set("");
                        loadGroups();
                        LOG.info("群组创建成功: name={}", name);
                    } else {
                        final String msg = response != null ? response.getMessage() : "创建群组失败";
                        errorMessage.set(msg);
                        LOG.warn("群组创建失败: {}", msg);
                    }
                }));
    }

    /**
     * 加载群成员列表
     *
     * @param groupId 群组ID
     */
    public void loadMembers(final Long groupId) {
        if (groupId == null) {
            return;
        }

        loading.set(true);
        errorMessage.set("");

        GroupService.getInstance().getGroupMembers(groupId, 1, 100)
                .thenAccept(response -> Platform.runLater(() -> {
                    loading.set(false);

                    if (response != null && response.isSuccess() && response.getData() != null) {
                        final List<GroupMemberInfo> list = response.getData().getList();
                        members.setAll(list);

                        // 记录当前用户在群中的角色
                        currentUserRole = null;
                        if (currentUserId != null) {
                            for (final GroupMemberInfo member : list) {
                                if (currentUserId.equals(member.getUserId())) {
                                    currentUserRole = member.getRole();
                                    break;
                                }
                            }
                        }

                        LOG.info("群成员加载成功: groupId={}, count={}", groupId, list.size());
                    } else {
                        final String msg = response != null ? response.getMessage() : "加载群成员失败";
                        errorMessage.set(msg);
                        LOG.warn("群成员加载失败: {}", msg);
                    }
                }));
    }

    /**
     * 退出群组
     *
     * @param groupId 群组ID
     */
    public void leaveGroup(final Long groupId) {
        if (groupId == null) {
            return;
        }

        loading.set(true);
        errorMessage.set("");

        GroupService.getInstance().leaveGroup(groupId)
                .thenAccept(response -> Platform.runLater(() -> {
                    loading.set(false);

                    if (response != null && response.isSuccess()) {
                        successMessage.set("已退出群组");
                        groups.removeIf(g -> groupId.equals(g.getGroupId()));
                        LOG.info("已退出群组: groupId={}", groupId);
                    } else {
                        final String msg = response != null ? response.getMessage() : "退出群组失败";
                        errorMessage.set(msg);
                        LOG.warn("退出群组失败: {}", msg);
                    }
                }));
    }

    /**
     * 解散群组（仅群主可操作）
     *
     * @param groupId 群组ID
     */
    public void dismissGroup(final Long groupId) {
        if (groupId == null) {
            return;
        }

        loading.set(true);
        errorMessage.set("");

        GroupService.getInstance().dismissGroup(groupId)
                .thenAccept(response -> Platform.runLater(() -> {
                    loading.set(false);

                    if (response != null && response.isSuccess()) {
                        successMessage.set("群组已解散");
                        groups.removeIf(g -> groupId.equals(g.getGroupId()));
                        LOG.info("群组已解散: groupId={}", groupId);
                    } else {
                        final String msg = response != null ? response.getMessage() : "解散群组失败";
                        errorMessage.set(msg);
                        LOG.warn("解散群组失败: {}", msg);
                    }
                }));
    }

    /**
     * 修改群信息（仅群主可操作）
     *
     * @param groupId            群组ID
     * @param name               新群名称
     * @param announcement       新群公告
     * @param announcementPinned 是否置顶公告（null 表示不修改）
     * @param avatar             群头像URL
     */
    public void updateGroupInfo(final Long groupId, final String name,
            final String announcement, final Boolean announcementPinned,
            final String avatar) {
        if (groupId == null) {
            return;
        }

        loading.set(true);
        errorMessage.set("");

        GroupService.getInstance().updateGroup(groupId, name, announcement, announcementPinned, avatar)
                .thenAccept(response -> Platform.runLater(() -> {
                    loading.set(false);

                    if (response != null && response.isSuccess()) {
                        successMessage.set("群信息修改成功");
                        // 刷新群列表以显示最新信息
                        loadGroups();
                        LOG.info("群信息修改成功: groupId={}, name={}", groupId, name);
                    } else {
                        final String msg = response != null ? response.getMessage() : "修改群信息失败";
                        errorMessage.set(msg);
                        LOG.warn("修改群信息失败: {}", msg);
                    }
                }));
    }

    /**
     * 判断当前用户是否为选中群组的群主
     *
     * @return true 表示当前用户是群主
     */
    public boolean isOwnerOfSelectedGroup() {
        return selectedGroup.get() != null
                && currentUserId != null
                && currentUserId.equals(selectedGroup.get().getOwnerId());
    }

    /**
     * 判断当前用户是否为选中群组的管理员或群主
     *
     * @return true 表示当前用户是管理员或群主
     */
    public boolean isAdminOrOwnerOfSelectedGroup() {
        return "OWNER".equals(currentUserRole) || "ADMIN".equals(currentUserRole);
    }

    /**
     * 获取当前用户在群组中的角色
     *
     * @return 角色：OWNER / ADMIN / MEMBER
     */
    public String getCurrentUserRole() {
        return currentUserRole;
    }

    /**
     * 获取当前登录用户ID
     *
     * @return 用户ID
     */
    public Long getCurrentUserId() {
        return currentUserId;
    }

    /**
     * 移除群成员（仅群主和管理员可操作）
     *
     * @param groupId      群组ID
     * @param targetUserId 目标用户ID
     */
    public void removeMember(final Long groupId, final Long targetUserId) {
        if (groupId == null || targetUserId == null) {
            return;
        }

        loading.set(true);
        errorMessage.set("");

        GroupService.getInstance().removeMember(groupId, targetUserId)
                .thenAccept(response -> Platform.runLater(() -> {
                    loading.set(false);

                    if (response != null && response.isSuccess()) {
                        successMessage.set("成员已移除");
                        loadMembers(groupId);
                        LOG.info("成员已移除: groupId={}, targetUserId={}", groupId, targetUserId);
                    } else {
                        final String msg = response != null ? response.getMessage() : "移除成员失败";
                        errorMessage.set(msg);
                        LOG.warn("移除成员失败: {}", msg);
                    }
                }));
    }

    /**
     * 设置/取消管理员（仅群主可操作）
     *
     * @param groupId      群组ID
     * @param targetUserId 目标用户ID
     * @param action       SET 设置管理员 / REMOVE 取消管理员
     */
    public void setAdmin(final Long groupId, final Long targetUserId, final String action) {
        if (groupId == null || targetUserId == null) {
            return;
        }

        loading.set(true);
        errorMessage.set("");

        GroupService.getInstance().setAdmin(groupId, targetUserId, action)
                .thenAccept(response -> Platform.runLater(() -> {
                    loading.set(false);

                    if (response != null && response.isSuccess()) {
                        final String msg = "SET".equals(action) ? "已设为管理员" : "已取消管理员";
                        successMessage.set(msg);
                        loadMembers(groupId);
                        LOG.info("管理员操作成功: groupId={}, targetUserId={}, action={}",
                                groupId, targetUserId, action);
                    } else {
                        final String msg = response != null ? response.getMessage() : "操作失败";
                        errorMessage.set(msg);
                        LOG.warn("管理员操作失败: {}", msg);
                    }
                }));
    }

    /**
     * 转让群主（仅群主可操作）
     *
     * @param groupId      群组ID
     * @param targetUserId 目标用户ID
     */
    public void transferOwner(final Long groupId, final Long targetUserId) {
        if (groupId == null || targetUserId == null) {
            return;
        }

        loading.set(true);
        errorMessage.set("");

        GroupService.getInstance().transferOwner(groupId, targetUserId)
                .thenCompose(response -> {
                    if (response != null && response.isSuccess()) {
                        // 先刷新群组列表以更新 ownerId，再刷新成员列表
                        return GroupService.getInstance().getGroupList(1, 100)
                                .thenAccept(groupResponse -> Platform.runLater(() -> {
                                    if (groupResponse != null && groupResponse.isSuccess()
                                            && groupResponse.getData() != null) {
                                        final List<GroupInfo> list = groupResponse.getData().getList();
                                        groups.setAll(list);

                                        // 同步更新 selectedGroup
                                        if (selectedGroup.get() != null) {
                                            for (final GroupInfo g : list) {
                                                if (selectedGroup.get().getGroupId().equals(g.getGroupId())) {
                                                    selectedGroup.set(g);
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                    loading.set(false);
                                    successMessage.set("群主已转让");
                                    // selectedGroup 已更新，再加载成员列表触发按钮刷新
                                    loadMembers(groupId);
                                }));
                    }
                    // 转让失败
                    Platform.runLater(() -> {
                        loading.set(false);
                        final String msg = response != null ? response.getMessage() : "转让群主失败";
                        errorMessage.set(msg);
                        LOG.warn("转让群主失败: {}", msg);
                    });
                    return CompletableFuture.completedFuture(null);
                })
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        loading.set(false);
                        errorMessage.set("转让群主失败");
                        LOG.error("转让群主异常", ex);
                    });
                    return null;
                });

        LOG.info("转让群主请求已发送: groupId={}, targetUserId={}", groupId, targetUserId);
    }

    /**
     * 设置群昵称
     *
     * @param groupId  群组ID
     * @param nickname 昵称
     */
    public void setNickname(final Long groupId, final String nickname) {
        if (groupId == null) {
            return;
        }

        loading.set(true);
        errorMessage.set("");

        GroupService.getInstance().setNickname(groupId, nickname)
                .thenAccept(response -> Platform.runLater(() -> {
                    loading.set(false);

                    if (response != null && response.isSuccess()) {
                        successMessage.set("群昵称设置成功");
                        loadMembers(groupId);
                        LOG.info("群昵称设置成功: groupId={}, nickname={}", groupId, nickname);
                    } else {
                        final String msg = response != null ? response.getMessage() : "设置群昵称失败";
                        errorMessage.set(msg);
                        LOG.warn("设置群昵称失败: {}", msg);
                    }
                }));
    }

    /**
     * 设置当前选中的群组
     *
     * @param group 群组
     */
    public void setSelectedGroup(final GroupInfo group) {
        this.selectedGroup.set(group);
    }

    /**
     * 获取当前选中的群组
     *
     * @return 群组信息
     */
    public GroupInfo getSelectedGroup() {
        return selectedGroup.get();
    }

    /**
     * 获取选中群组属性（用于监听选中变化）
     *
     * @return 选中群组属性
     */
    public ObjectProperty<GroupInfo> selectedGroupProperty() {
        return selectedGroup;
    }

    // Property getters
    public ListProperty<GroupInfo> groupsProperty() {
        return groups;
    }

    public ObservableList<GroupInfo> getGroups() {
        return groups.get();
    }

    public ListProperty<GroupMemberInfo> membersProperty() {
        return members;
    }

    public ObservableList<GroupMemberInfo> getMembers() {
        return members.get();
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public StringProperty successMessageProperty() {
        return successMessage;
    }

    public StringProperty groupNameProperty() {
        return groupName;
    }
}
