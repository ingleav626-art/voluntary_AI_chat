package org.example.client.controller;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import org.example.client.model.GroupInfo;
import org.example.client.model.GroupMemberInfo;
import org.example.client.view.GroupListViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 群组面板控制器
 *
 * <p>负责群组列表展示、创建群组、群成员管理、退出/解散群组等操作。</p>
 *
 * <p>
 * <b>TODO:⚠️ 类长度超限警告：当前311行，超出Controller限制（300行）</b>
 * <br>请勿在此类中添加新的职责，应拆分为：
 * <ul>
 * <li>GroupListController（群组列表管理）</li>
 * <li>GroupMemberController（群成员管理）</li>
 * </ul>
 * </p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class GroupController implements Initializable {

    private static final Logger LOG = LoggerFactory.getLogger(GroupController.class);

    private static final String GROUP_CREATE_FXML = "/fxml/group_create_dialog.fxml";

    /** 修改群信息对话框 FXML */
    private static final String GROUP_INFO_DIALOG_FXML = "/fxml/group_info_dialog.fxml";

    /** 修改群头像对话框 FXML */
    private static final String GROUP_AVATAR_DIALOG_FXML = "/fxml/group_avatar_dialog.fxml";

    /** 邀请成员对话框 FXML */
    private static final String GROUP_INVITE_DIALOG_FXML = "/fxml/group_invite_dialog.fxml";

    @FXML
    private VBox rootPane;

    @FXML
    private Button backButton;

    @FXML
    private Button refreshGroupsButton;

    @FXML
    private ListView<GroupInfo> groupList;

    @FXML
    private Button createGroupButton;

    @FXML
    private ListView<GroupMemberInfo> memberList;

    @FXML
    private Label errorLabel;

    @FXML
    private Label successLabel;

    @FXML
    private Label groupDetailTitle;

    @FXML
    private ProgressIndicator loadingIndicator;

    /** 群操作按钮 */
    @FXML
    private Button inviteMemberButton;

    @FXML
    private Button aiConfigButton;

    @FXML
    private Button avatarButton;

    @FXML
    private Button editGroupButton;

    @FXML
    private Button leaveGroupButton;

    @FXML
    private Button dismissGroupButton;

    private GroupListViewModel viewModel;

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        viewModel = new GroupListViewModel();

        // 绑定群组列表
        groupList.itemsProperty().bind(viewModel.groupsProperty());
        groupList.setCellFactory(param -> new GroupCell());

        // 监听viewModel.selectedGroup变化，用于自动选中群组后更新UI
        viewModel.selectedGroupProperty().addListener((obs, oldGroup, newGroup) -> {
            if (newGroup != null) {
                // 查找群组列表中对应的群组并选中
                for (final GroupInfo group : groupList.getItems()) {
                    if (group.getGroupId().equals(newGroup.getGroupId())) {
                        groupList.getSelectionModel().select(group);
                        groupDetailTitle.setText("群成员 - " + newGroup.getName());
                        viewModel.loadMembers(newGroup.getGroupId());
                        updateActionButtons(newGroup);
                        LOG.info("监听器触发：已选中群组 groupId={}, groupName={}",
                                newGroup.getGroupId(), newGroup.getName());
                        break;
                    }
                }
            }
        });

        // 群组点击事件：显示群成员
        groupList.setOnMouseClicked(event -> {
            final GroupInfo selected = groupList.getSelectionModel().getSelectedItem();
            if (selected != null && event.getButton() == MouseButton.PRIMARY) {
                viewModel.setSelectedGroup(selected);
                groupDetailTitle.setText("群成员 - " + selected.getName());
                viewModel.loadMembers(selected.getGroupId());
                updateActionButtons(selected);
            }
        });

        // 绑定群成员列表
        memberList.itemsProperty().bind(viewModel.membersProperty());
        memberList.setCellFactory(param -> new MemberCell());

        // 成员列表变化时重新更新操作按钮状态（群主转让后角色变化需要刷新按钮）
        viewModel.membersProperty().addListener((obs, oldVal, newVal) -> {
            final GroupInfo selected = viewModel.getSelectedGroup();
            if (selected != null) {
                updateActionButtons(selected);
            }
            // 强制刷新成员列表Cell，确保行内按钮（移除、转让等）也同步更新
            memberList.refresh();
        });

        // 绑定状态消息
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        successLabel.textProperty().bind(viewModel.successMessageProperty());

        // 绑定加载状态
        loadingIndicator.visibleProperty().bind(viewModel.loadingProperty());

        // 自动清除成功消息
        viewModel.successMessageProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    Platform.runLater(() -> viewModel.successMessageProperty().set(""));
                }).start();
            }
        });

        // 初始隐藏操作按钮
        setActionButtonsVisible(false);

        // 注册群组事件监听器，收到 WebSocket 群成员变更通知时刷新成员列表
        GroupListViewModel.setGroupEventListener(changedGroupId -> {
            final GroupInfo selected = viewModel.getSelectedGroup();
            if (selected != null && selected.getGroupId().equals(changedGroupId)) {
                LOG.info("收到群成员变更通知，刷新成员列表: groupId={}", changedGroupId);
                viewModel.loadMembers(changedGroupId);
            }
        });

        // 加载初始数据
        viewModel.loadGroups();

        LOG.info("群组面板控制器初始化完成");
    }

    /**
     * 更新操作按钮：群主显示"解散群组"和"修改群信息"，成员显示"退出群组"
     *
     * @param group 选中的群组
     */
    private void updateActionButtons(final GroupInfo group) {
        if (group == null) {
            setActionButtonsVisible(false);
            return;
        }
        setActionButtonsVisible(true);

        // 邀请按钮所有人可见
        inviteMemberButton.setVisible(true);
        inviteMemberButton.setManaged(true);

        // AI配置按钮所有人可见
        aiConfigButton.setVisible(true);
        aiConfigButton.setManaged(true);

        // 头像按钮所有人可见
        avatarButton.setVisible(true);
        avatarButton.setManaged(true);

        // 群主显示"解散群组"和"修改群信息"，成员显示"退出群组"
        if (viewModel.isOwnerOfSelectedGroup()) {
            editGroupButton.setVisible(true);
            editGroupButton.setManaged(true);
            dismissGroupButton.setVisible(true);
            dismissGroupButton.setManaged(true);
            leaveGroupButton.setVisible(false);
            leaveGroupButton.setManaged(false);
        } else {
            editGroupButton.setVisible(false);
            editGroupButton.setManaged(false);
            dismissGroupButton.setVisible(false);
            dismissGroupButton.setManaged(false);
            leaveGroupButton.setVisible(true);
            leaveGroupButton.setManaged(true);
        }
    }

    /**
     * 设置操作按钮可见性
     *
     * @param visible 是否可见
     */
    private void setActionButtonsVisible(final boolean visible) {
        inviteMemberButton.setVisible(visible);
        inviteMemberButton.setManaged(visible);
        aiConfigButton.setVisible(visible);
        aiConfigButton.setManaged(visible);
        avatarButton.setVisible(visible);
        avatarButton.setManaged(visible);
        editGroupButton.setVisible(visible);
        editGroupButton.setManaged(visible);
        leaveGroupButton.setVisible(visible);
        leaveGroupButton.setManaged(visible);
        dismissGroupButton.setVisible(visible);
        dismissGroupButton.setManaged(visible);
    }

    /**
     * 处理返回
     */
    @FXML
    private void handleBack() {
        LOG.info("返回主界面");
        org.example.client.App.switchToMainFromGroup();
    }

    /**
     * 根据群组ID选中群组
     *
     * @param groupId 群组ID
     */
    public void selectGroupById(final Long groupId) {
        if (groupId == null) {
            LOG.warn("群组ID为空，无法选中群组");
            return;
        }

        // 检查群组列表是否已加载
        if (groupList.getItems().isEmpty()) {
            LOG.info("群组列表未加载，设置待选中群组ID: {}", groupId);
            // 设置待选中的群组ID，等待群组列表加载完成后自动选中
            viewModel.setPendingSelectGroupId(groupId);
            return;
        }

        // 遍历群组列表，查找匹配的群组
        for (final GroupInfo group : groupList.getItems()) {
            if (group.getGroupId().equals(groupId)) {
                // 选中群组
                groupList.getSelectionModel().select(group);

                // 设置选中的群组并加载群成员
                viewModel.setSelectedGroup(group);
                groupDetailTitle.setText("群成员 - " + group.getName());
                viewModel.loadMembers(groupId);
                updateActionButtons(group);

                LOG.info("已选中群组: groupId={}, groupName={}", groupId, group.getName());
                return;
            }
        }

        LOG.warn("未找到群组ID为{}的群组", groupId);
    }

    /**
     * 刷新群组列表
     */
    @FXML
    private void handleRefreshGroups() {
        LOG.info("刷新群组列表");
        viewModel.loadGroups();
    }

    /**
     * 打开创建群组对话框
     */
    @FXML
    private void handleCreateGroup() {
        try {
            final FXMLLoader loader = new FXMLLoader(getClass().getResource(GROUP_CREATE_FXML));
            final Parent root = loader.load();

            final GroupCreateController controller = loader.getController();
            // 创建成功后刷新群组列表
            controller.setOnSuccess(() -> {
                LOG.info("群组创建成功，刷新列表");
                viewModel.loadGroups();
            });

            final Stage dialog = new Stage();
            dialog.setTitle("创建群组");
            dialog.setScene(new Scene(root, 440, 520));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(createGroupButton.getScene().getWindow());
            // 应用默认样式
            final java.net.URL cssUrl = getClass().getResource("/css/default.css");
            if (cssUrl != null) {
                dialog.getScene().getStylesheets().add(cssUrl.toExternalForm());
            }
            dialog.showAndWait();
        } catch (final Exception e) {
            LOG.error("打开创建群组对话框失败", e);
            errorLabel.setText("打开创建窗口失败");
        }
    }

    /**
     * 打开修改群信息对话框
     */
    @FXML
    private void handleEditGroupInfo() {
        final GroupInfo selected = viewModel.getSelectedGroup();
        if (selected == null) {
            return;
        }

        try {
            final FXMLLoader loader = new FXMLLoader(getClass().getResource(GROUP_INFO_DIALOG_FXML));
            final Parent root = loader.load();

            final GroupInfoController controller = loader.getController();
            // 传入当前群信息
            controller.initData(
                    selected.getGroupId(),
                    viewModel,
                    selected.getName(),
                    null,  // 当前公告（API 暂不返回，可后续扩展）
                    false,  // 当前置顶状态
                    selected.getAvatar()  // 当前头像URL
            );

            final Stage dialog = new Stage();
            dialog.setTitle("修改群信息");
            dialog.setScene(new Scene(root, 440, 480));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(editGroupButton.getScene().getWindow());
            final java.net.URL cssUrl = getClass().getResource("/css/default.css");
            if (cssUrl != null) {
                dialog.getScene().getStylesheets().add(cssUrl.toExternalForm());
            }
            dialog.showAndWait();
        } catch (final Exception e) {
            LOG.error("打开修改群信息对话框失败", e);
            errorLabel.setText("打开修改窗口失败");
        }
    }

    /**
     * 打开修改群头像对话框
     */
    @FXML
    private void handleEditAvatar() {
        final GroupInfo selected = viewModel.getSelectedGroup();
        if (selected == null) {
            return;
        }

        try {
            final FXMLLoader loader = new FXMLLoader(getClass().getResource(GROUP_AVATAR_DIALOG_FXML));
            final Parent root = loader.load();

            final GroupAvatarController controller = loader.getController();
            // 传入当前群信息
            controller.initData(
                    selected.getGroupId(),
                    viewModel,
                    selected.getName(),
                    selected.getAvatar()  // 当前头像URL
            );

            final Stage dialog = new Stage();
            dialog.setTitle("修改群头像");
            dialog.setScene(new Scene(root, 400, 380));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(avatarButton.getScene().getWindow());
            final java.net.URL cssUrl = getClass().getResource("/css/default.css");
            if (cssUrl != null) {
                dialog.getScene().getStylesheets().add(cssUrl.toExternalForm());
            }
            dialog.showAndWait();
        } catch (final Exception e) {
            LOG.error("打开修改群头像对话框失败", e);
            errorLabel.setText("打开头像窗口失败");
        }
    }

    /**
     * 打开邀请成员对话框
     */
    @FXML
    private void handleInviteMembers() {
        final GroupInfo selected = viewModel.getSelectedGroup();
        if (selected == null) {
            return;
        }

        try {
            final FXMLLoader loader = new FXMLLoader(getClass().getResource(GROUP_INVITE_DIALOG_FXML));
            final Parent root = loader.load();

            final GroupInviteController controller = loader.getController();
            // 邀请成功后刷新成员列表
            controller.initData(selected.getGroupId(), () -> {
                LOG.info("邀请成员成功，刷新成员列表: groupId={}", selected.getGroupId());
                viewModel.loadMembers(selected.getGroupId());
            });

            final Stage dialog = new Stage();
            dialog.setTitle("邀请成员");
            dialog.setScene(new Scene(root, 440, 520));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(inviteMemberButton.getScene().getWindow());
            final java.net.URL cssUrl = getClass().getResource("/css/default.css");
            if (cssUrl != null) {
                dialog.getScene().getStylesheets().add(cssUrl.toExternalForm());
            }
            dialog.showAndWait();
        } catch (final Exception e) {
            LOG.error("打开邀请成员对话框失败", e);
            errorLabel.setText("打开邀请窗口失败");
        }
    }

    /**
     * 打开群 AI 配置对话框
     */
    @FXML
    private void handleAiConfig() {
        final GroupInfo selected = viewModel.getSelectedGroup();
        if (selected == null) {
            return;
        }

        GroupAiConfigDialog.show(aiConfigButton.getScene().getWindow(), selected.getGroupId());
    }

    /**
     * 退出群组（成员）
     */
    @FXML
    private void handleLeaveGroup() {
        final GroupInfo selected = viewModel.getSelectedGroup();
        if (selected == null) {
            return;
        }

        final Alert confirm = new Alert(AlertType.CONFIRMATION);
        confirm.setTitle("退出群组");
        confirm.setHeaderText("确认退出群组");
        confirm.setContentText("确定要退出群「" + selected.getName() + "」吗？");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                viewModel.leaveGroup(selected.getGroupId());
                groupDetailTitle.setText("请选择群组");
                memberList.getItems().clear();
                groupList.getSelectionModel().clearSelection();
                setActionButtonsVisible(false);
            }
        });
    }

    /**
     * 解散群组（仅群主）
     */
    @FXML
    private void handleDismissGroup() {
        final GroupInfo selected = viewModel.getSelectedGroup();
        if (selected == null) {
            return;
        }

        final Alert confirm = new Alert(AlertType.CONFIRMATION);
        confirm.setTitle("解散群组");
        confirm.setHeaderText("确认解散群组");
        confirm.setContentText("解散群「" + selected.getName() + "」后，所有成员将被移除，且不可恢复。\n确定要解散吗？");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                viewModel.dismissGroup(selected.getGroupId());
                groupDetailTitle.setText("请选择群组");
                memberList.getItems().clear();
                groupList.getSelectionModel().clearSelection();
                setActionButtonsVisible(false);
            }
        });
    }

    /**
     * 群组列表 Cell
     */
    private final class GroupCell extends ListCell<GroupInfo> {

            @Override
            protected void updateItem(final GroupInfo item, final boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                final HBox cell = new HBox(10);
                cell.getStyleClass().add("group-cell");
                cell.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                // 头像
                final javafx.scene.shape.Circle avatarCircle = new javafx.scene.shape.Circle(18);
                avatarCircle.setFill(javafx.scene.paint.Color.valueOf("#E76F51"));
                final Label avatarText = new Label(
                        item.getName() != null && !item.getName().isEmpty()
                                ? String.valueOf(item.getName().charAt(0)) : "群");
                avatarText.setStyle("-fx-text-fill: white; -fx-font-size: 13; -fx-font-weight: bold;");
                final javafx.scene.layout.StackPane avatarPane = new javafx.scene.layout.StackPane(
                        avatarCircle, avatarText);

                final VBox info = new VBox(2);
                info.getStyleClass().add("group-info");

                final Label name = new Label(item.getName() != null ? item.getName() : "未知群组");
                name.getStyleClass().add("group-name");

                final Label meta = new Label(
                        (item.getMemberCount() != null ? item.getMemberCount() + "人" : "未知人数"));
                meta.getStyleClass().add("group-meta");

                info.getChildren().addAll(name, meta);

                final Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                cell.getChildren().addAll(avatarPane, info, spacer);
                setGraphic(cell);
                setText(null);
            }
        }

    /**
     * 群成员列表 Cell
     */
    private final class MemberCell extends ListCell<GroupMemberInfo> {

        @Override
        protected void updateItem(final GroupMemberInfo item, final boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            final HBox cell = new HBox(10);
            cell.getStyleClass().add("member-cell");
            cell.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            final VBox info = new VBox(2);

            final String displayName = item.getNickname() != null
                    ? item.getNickname() : item.getUsername();
            final Label name = new Label(displayName != null ? displayName : "未知用户");
            name.getStyleClass().add("member-name");

            final String roleText = roleToChinese(item.getRole());
            final Label role = new Label(roleText);
            role.getStyleClass().add("member-role");

            info.getChildren().addAll(name, role);

            final Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // 操作按钮区域
            final HBox actions = new HBox(4);
            actions.getStyleClass().add("member-actions");

            final Long selectedGroupId = viewModel.getSelectedGroup() != null
                    ? viewModel.getSelectedGroup().getGroupId() : null;
            final Long currentUserId = viewModel.getCurrentUserId();
            final boolean isOwner = viewModel.isOwnerOfSelectedGroup();
            final boolean isAdminOrOwner = viewModel.isAdminOrOwnerOfSelectedGroup();
            final boolean isSelf = currentUserId != null && currentUserId.equals(item.getUserId());
            final boolean isTargetOwner = "OWNER".equals(item.getRole());

            // 设置昵称（仅自己可见）
            if (isSelf) {
                final Button nicknameBtn = new Button("昵称");
                nicknameBtn.getStyleClass().add("btn-action-sm");
                nicknameBtn.setOnAction(e -> {
                    final javafx.scene.control.TextInputDialog dialog =
                            new javafx.scene.control.TextInputDialog(
                                    item.getNickname() != null ? item.getNickname() : "");
                    dialog.setTitle("设置群昵称");
                    dialog.setHeaderText("设置你在群中的昵称");
                    dialog.setContentText("昵称：");
                    dialog.showAndWait().ifPresent(nickname -> {
                        if (selectedGroupId != null) {
                            viewModel.setNickname(selectedGroupId, nickname);
                        }
                    });
                });
                actions.getChildren().add(nicknameBtn);
            }

            // 设为/取消管理员（仅群主，不可对自己操作）
            if (isOwner && !isSelf) {
                if ("MEMBER".equals(item.getRole())) {
                    final Button setAdminBtn = new Button("设管理");
                    setAdminBtn.getStyleClass().add("btn-action-sm");
                    setAdminBtn.setOnAction(e -> {
                        if (selectedGroupId != null) {
                            viewModel.setAdmin(selectedGroupId, item.getUserId(), "SET");
                        }
                    });
                    actions.getChildren().add(setAdminBtn);
                } else if ("ADMIN".equals(item.getRole())) {
                    final Button unsetAdminBtn = new Button("取消管理");
                    unsetAdminBtn.getStyleClass().add("btn-action-sm");
                    unsetAdminBtn.setOnAction(e -> {
                        if (selectedGroupId != null) {
                            viewModel.setAdmin(selectedGroupId, item.getUserId(), "REMOVE");
                        }
                    });
                    actions.getChildren().add(unsetAdminBtn);
                }
            }

            // 转让群主（仅群主，不可对自己操作）
            if (isOwner && !isSelf) {
                final Button transferBtn = new Button("转让");
                transferBtn.getStyleClass().add("btn-action-sm");
                transferBtn.setOnAction(e -> {
                    final Alert confirm = new Alert(AlertType.CONFIRMATION);
                    confirm.setTitle("转让群主");
                    confirm.setHeaderText("确认转让群主");
                    confirm.setContentText("确定将群主转让给「" + displayName + "」吗？");
                    confirm.showAndWait().ifPresent(response -> {
                        if (response == ButtonType.OK && selectedGroupId != null) {
                            viewModel.transferOwner(selectedGroupId, item.getUserId());
                        }
                    });
                });
                actions.getChildren().add(transferBtn);
            }

            // 移除成员（群主/管理员可见，不可移除自己和群主）
            if (isAdminOrOwner && !isSelf && !isTargetOwner) {
                final Button removeBtn = new Button("移除");
                removeBtn.getStyleClass().add("btn-action-sm");
                removeBtn.setOnAction(e -> {
                    final Alert confirm = new Alert(AlertType.CONFIRMATION);
                    confirm.setTitle("移除成员");
                    confirm.setHeaderText("确认移除成员");
                    confirm.setContentText("确定要移除成员「" + displayName + "」吗？");
                    confirm.showAndWait().ifPresent(response -> {
                        if (response == ButtonType.OK && selectedGroupId != null) {
                            viewModel.removeMember(selectedGroupId, item.getUserId());
                        }
                    });
                });
                actions.getChildren().add(removeBtn);
            }

            cell.getChildren().addAll(info, spacer, actions);
            setGraphic(cell);
            setText(null);
        }

        /**
         * 角色名转中文
         */
        private String roleToChinese(final String role) {
            if (role == null) {
                return "";
            }
            return switch (role.toUpperCase()) {
                case "OWNER" -> "群主";
                case "ADMIN" -> "管理员";
                default -> "成员";
            };
        }
    }
}