package org.example.client.controller;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.example.client.model.UserInfo;
import org.example.client.view.MainViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * 顶部标题栏控制器
 *
 * <p>管理顶部全局标题栏的交互，包括用户信息展示、搜索、快捷操作按钮。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public class TopBarController implements Initializable {

    private static final Logger LOG = LoggerFactory.getLogger(TopBarController.class);

    @FXML
    private HBox topBar;

    /**
     * 获取顶栏节点（供 MainController 应用毛玻璃效果）
     *
     * @return 顶栏 HBox
     */
    public HBox getTopBar() {
        return topBar;
    }

    @FXML
    private HBox avatarBox;

    @FXML
    private Circle avatarCircle;

    @FXML
    private Label avatarText;

    @FXML
    private Label usernameLabel;

    @FXML
    private Circle statusDot;

    @FXML
    private Label statusLabel;

    @FXML
    private TextField searchField;

    @FXML
    private Button addButton;

    /** 主视图模型引用 */
    private MainViewModel viewModel;

    /** 头像下拉菜单 */
    private ContextMenu avatarMenu;

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        setupAvatarMenu();
        LOG.info("顶部标题栏控制器初始化完成");
    }

    /**
     * 设置主视图模型引用，绑定用户信息和搜索
     *
     * @param viewModel 主视图模型
     */
    public void setViewModel(final MainViewModel viewModel) {
        this.viewModel = viewModel;

        // 绑定用户信息
        usernameLabel.textProperty().bind(
                Bindings.createStringBinding(() -> {
                    final UserInfo user = viewModel.currentUserProperty().get();
                    return user != null && user.getUsername() != null ? user.getUsername() : "用户";
                }, viewModel.currentUserProperty()));

        avatarText.textProperty().bind(
                Bindings.createStringBinding(() -> {
                    final UserInfo user = viewModel.currentUserProperty().get();
                    if (user != null && user.getUsername() != null && !user.getUsername().isEmpty()) {
                        return String.valueOf(user.getUsername().charAt(0));
                    }
                    return "?";
                }, viewModel.currentUserProperty()));

        // 监听用户头像变化，加载真实头像图片
        viewModel.currentUserProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getAvatar() != null && !newVal.getAvatar().isEmpty()) {
                org.example.client.util.ImageUtils.loadAvatarToCircle(
                        newVal.getAvatar(), avatarCircle, avatarCircle.getRadius());
                avatarText.setVisible(false);
            } else {
                avatarText.setVisible(true);
            }
        });
        // 初始加载
        final UserInfo initUser = viewModel.currentUserProperty().get();
        if (initUser != null && initUser.getAvatar() != null && !initUser.getAvatar().isEmpty()) {
            org.example.client.util.ImageUtils.loadAvatarToCircle(
                    initUser.getAvatar(), avatarCircle, avatarCircle.getRadius());
            avatarText.setVisible(false);
        }

        // 绑定连接状态
        statusLabel.textProperty().bind(
                Bindings.createStringBinding(() -> viewModel.connectedProperty().get() ? "在线" : "离线",
                        viewModel.connectedProperty()));

        statusDot.fillProperty().bind(
                Bindings.createObjectBinding(
                        () -> viewModel.connectedProperty().get() ? Color.valueOf("#4CAF50") : Color.valueOf("#9E9E9E"),
                        viewModel.connectedProperty()));

        // 搜索框实时过滤
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            viewModel.filterConversations(newVal);
            viewModel.searchConversations(newVal);
        });
    }

    /**
     * 获取搜索框引用，供 MainController 使用
     *
     * @return 搜索框
     */
    public TextField getSearchField() {
        return searchField;
    }

    /**
     * 设置头像下拉菜单
     */
    private void setupAvatarMenu() {
        avatarMenu = new ContextMenu();

        final MenuItem settingsItem = new MenuItem("个人设置");
        settingsItem.setOnAction(e -> handleSettings());

        final MenuItem logoutItem = new MenuItem("退出登录");
        logoutItem.setOnAction(e -> handleLogout());

        avatarMenu.getItems().addAll(settingsItem, logoutItem);
    }

    /**
     * 处理头像点击，弹出下拉菜单
     */
    @FXML
    private void handleAvatarClick() {
        try {
            if (avatarBox != null && avatarBox.getScene() != null) {
                avatarMenu.show(avatarBox, javafx.geometry.Side.BOTTOM, 0, 0);
            } else {
                LOG.warn("avatarBox 或其 Scene 为空，无法显示菜单");
            }
        } catch (final Exception e) {
            LOG.error("显示头像菜单失败", e);
        }
    }

    /**
     * 处理设置
     */
    private void handleSettings() {
        LOG.info("点击设置");
        try {
            final javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/fxml/profile_dialog.fxml"));
            final javafx.scene.Parent root = loader.load();

            final ProfileController controller = loader.getController();
            controller.setMainViewModel(viewModel);

            final javafx.stage.Stage dialogStage = new javafx.stage.Stage();
            dialogStage.setTitle("个人设置");
            dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialogStage.initOwner(topBar.getScene().getWindow());

            final javafx.scene.Scene scene = new javafx.scene.Scene(root);
            final java.net.URL cssUrl = getClass().getResource("/css/default.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            } else {
                LOG.warn("未找到默认样式表 /css/default.css");
            }
            dialogStage.setScene(scene);

            controller.setDialogStage(dialogStage);
            dialogStage.showAndWait();

        } catch (final Exception e) {
            LOG.error("加载个人设置对话框失败", e);
            NotificationDialog.showWarning("无法打开设置面板", "请稍后重试");
        }
    }

    /**
     * 处理退出登录
     */
    private void handleLogout() {
        LOG.info("退出登录");
        final javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION);
        confirm.setTitle("退出登录");
        confirm.setHeaderText("确认退出登录");
        confirm.setContentText("确定要退出登录吗？");

        // 应用粉色主题样式
        final javafx.scene.control.DialogPane dialogPane = confirm.getDialogPane();
        dialogPane.setStyle("-fx-background-color: #FFF0F4; "
                + "-fx-border-color: #FFB6C1; "
                + "-fx-border-width: 2; "
                + "-fx-border-radius: 12; "
                + "-fx-background-radius: 12;");

        // 标题样式
        dialogPane.lookup(".header-panel").setStyle(
                "-fx-background-color: #FFE0EC; "
                        + "-fx-text-fill: #FF6B9D;");
        dialogPane.lookup(".header-panel .label").setStyle(
                "-fx-text-fill: #FF6B9D; "
                        + "-fx-font-size: 16; "
                        + "-fx-font-weight: bold;");

        // 内容样式
        dialogPane.lookup(".content.label").setStyle(
                "-fx-text-fill: #333333; "
                        + "-fx-font-size: 14;");

        // 确认按钮样式（粉色）
        final javafx.scene.control.Button okButton = (javafx.scene.control.Button) dialogPane.lookupButton(
                javafx.scene.control.ButtonType.OK);
        okButton.setStyle("-fx-background-color: #FF6B9D; "
                + "-fx-text-fill: white; "
                + "-fx-font-size: 13; "
                + "-fx-font-weight: bold; "
                + "-fx-background-radius: 8; "
                + "-fx-padding: 8 20; "
                + "-fx-cursor: hand;");
        okButton.setOnMouseEntered(e -> okButton.setStyle("-fx-background-color: #E8507A; "
                + "-fx-text-fill: white; "
                + "-fx-font-size: 13; "
                + "-fx-font-weight: bold; "
                + "-fx-background-radius: 8; "
                + "-fx-padding: 8 20; "
                + "-fx-cursor: hand;"));
        okButton.setOnMouseExited(e -> okButton.setStyle("-fx-background-color: #FF6B9D; "
                + "-fx-text-fill: white; "
                + "-fx-font-size: 13; "
                + "-fx-font-weight: bold; "
                + "-fx-background-radius: 8; "
                + "-fx-padding: 8 20; "
                + "-fx-cursor: hand;"));

        // 取消按钮样式（浅粉色）
        final javafx.scene.control.Button cancelButton = (javafx.scene.control.Button) dialogPane.lookupButton(
                javafx.scene.control.ButtonType.CANCEL);
        cancelButton.setStyle("-fx-background-color: #FFE0EC; "
                + "-fx-text-fill: #FF6B9D; "
                + "-fx-font-size: 13; "
                + "-fx-background-radius: 8; "
                + "-fx-padding: 8 20; "
                + "-fx-cursor: hand; "
                + "-fx-border-color: #FFB6C1; "
                + "-fx-border-width: 1;");
        cancelButton.setOnMouseEntered(e -> cancelButton.setStyle("-fx-background-color: #FFB6C1; "
                + "-fx-text-fill: white; "
                + "-fx-font-size: 13; "
                + "-fx-background-radius: 8; "
                + "-fx-padding: 8 20; "
                + "-fx-cursor: hand; "
                + "-fx-border-color: #FF6B9D; "
                + "-fx-border-width: 1;"));
        cancelButton.setOnMouseExited(e -> cancelButton.setStyle("-fx-background-color: #FFE0EC; "
                + "-fx-text-fill: #FF6B9D; "
                + "-fx-font-size: 13; "
                + "-fx-background-radius: 8; "
                + "-fx-padding: 8 20; "
                + "-fx-cursor: hand; "
                + "-fx-border-color: #FFB6C1; "
                + "-fx-border-width: 1;"));

        confirm.showAndWait().ifPresent(response -> {
            if (response == javafx.scene.control.ButtonType.OK) {
                org.example.client.App.switchToLogin();
            }
        });
    }

    /**
     * 处理 ＋ 按钮点击，弹出快捷菜单
     */
    @FXML
    private void handleAddButton() {
        final ContextMenu menu = new ContextMenu();

        final MenuItem createAiItem = new MenuItem("创建 AI 助手");
        createAiItem.setOnAction(e -> {
            LOG.info("创建AI助手");
            org.example.client.App.switchToAi();
        });

        final MenuItem addFriendItem = new MenuItem("添加好友");
        addFriendItem.setOnAction(e -> {
            LOG.info("添加好友");
            org.example.client.App.switchToFriend();
        });

        final MenuItem createGroupItem = new MenuItem("创建群聊");
        createGroupItem.setOnAction(e -> {
            LOG.info("创建群聊");
            org.example.client.App.switchToGroup();
        });

        menu.getItems().addAll(createAiItem, addFriendItem, createGroupItem);
        menu.show(addButton, javafx.geometry.Side.BOTTOM, 0, 0);
    }
}
