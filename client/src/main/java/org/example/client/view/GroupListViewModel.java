package org.example.client.view;

import java.util.List;

import org.example.client.model.CreateGroupRequest;
import org.example.client.model.CreateGroupResponse;
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
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * 群组列表视图模型
 *
 * <p>管理群组列表、群成员列表、创建群组、退出/解散群组等操作。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class GroupListViewModel {

    private static final Logger LOG = LoggerFactory.getLogger(GroupListViewModel.class);

    /** 群组列表 */
    private final ListProperty<GroupInfo> groups =
            new SimpleListProperty<>(FXCollections.observableArrayList());

    /** 群成员列表 */
    private final ListProperty<GroupMemberInfo> members =
            new SimpleListProperty<>(FXCollections.observableArrayList());

    /** 加载状态 */
    private final BooleanProperty loading = new SimpleBooleanProperty(false);

    /** 错误消息 */
    private final StringProperty errorMessage = new SimpleStringProperty("");

    /** 成功消息 */
    private final StringProperty successMessage = new SimpleStringProperty("");

    /** 创建群组-群名称 */
    private final StringProperty groupName = new SimpleStringProperty("");

    /** 当前选中群组 */
    private GroupInfo selectedGroup;

    /** 当前登录用户ID */
    private Long currentUserId;

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
                        LOG.info("群组列表加载成功: count={}", list.size());
                    } else {
                        final String msg = response != null ? response.getMessage() : "加载群组列表失败";
                        errorMessage.set(msg);
                        LOG.warn("群组列表加载失败: {}", msg);
                    }
                }));
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
     * 判断当前用户是否为选中群组的群主
     *
     * @return true 表示当前用户是群主
     */
    public boolean isOwnerOfSelectedGroup() {
        return selectedGroup != null
                && currentUserId != null
                && currentUserId.equals(selectedGroup.getOwnerId());
    }

    /**
     * 设置当前选中的群组
     *
     * @param group 群组
     */
    public void setSelectedGroup(final GroupInfo group) {
        this.selectedGroup = group;
    }

    /**
     * 获取当前选中的群组
     *
     * @return 群组信息
     */
    public GroupInfo getSelectedGroup() {
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
