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

        // 加载初始数据
        viewModel.loadGroups();

        LOG.info("群组面板控制器初始化完成");
    }

    /**
     * 更新操作按钮：群主显示"解散群组"，成员显示"退出群组"
     *
     * @param group 选中的群组
     */
    private void updateActionButtons(final GroupInfo group) {
        if (group == null) {
            setActionButtonsVisible(false);
            return;
        }
        setActionButtonsVisible(true);

        // 群主显示"解散群组"，成员显示"退出群组"
        if (viewModel.isOwnerOfSelectedGroup()) {
            dismissGroupButton.setVisible(true);
            dismissGroupButton.setManaged(true);
            leaveGroupButton.setVisible(false);
            leaveGroupButton.setManaged(false);
        } else {
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

            cell.getChildren().addAll(info, spacer);
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

            cell.getChildren().addAll(info, spacer);
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