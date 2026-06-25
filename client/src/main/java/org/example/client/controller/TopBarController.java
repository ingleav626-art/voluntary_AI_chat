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

        final MenuItem settingsItem = new MenuItem("设置（个人）");
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
        avatarMenu.show(avatarCircle, javafx.geometry.Side.BOTTOM, 0, 0);
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
            scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
            dialogStage.setScene(scene);

            controller.setDialogStage(dialogStage);
            dialogStage.showAndWait();

        } catch (final java.io.IOException e) {
            LOG.error("加载个人设置对话框失败", e);
            NotificationDialog.showWarning("无法打开设置面板", "请稍后重试");
        }
    }

    /**
     * 处理退出登录
     */
    private void handleLogout() {
        LOG.info("退出登录");
        if (viewModel != null) {
            viewModel.logout();
        }
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
