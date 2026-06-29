package org.example.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 功能侧边栏控制器
 *
 * <p>管理最左侧功能图标栏的交互，包括 AI 设置和消息切换。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public class FunctionBarController {

    private static final Logger LOG = LoggerFactory.getLogger(FunctionBarController.class);

    @FXML
    private VBox functionBar;

    /**
     * 获取功能栏节点（供 MainController 应用毛玻璃效果）
     *
     * @return 功能栏 VBox
     */
    public VBox getFunctionBar() {
        return functionBar;
    }

    @FXML
    private Button aiSettingBtn;

    @FXML
    private Button messageBtn;

    @FXML
    private Button logoutBtn;

    /** 当前选中的功能按钮 */
    private Button selectedBtn;

    @FXML
    public void initialize() {
        selectedBtn = messageBtn;

        // 设置 Tooltip
        aiSettingBtn.setTooltip(new Tooltip("AI 设置"));
        messageBtn.setTooltip(new Tooltip("消息"));
        logoutBtn.setTooltip(new Tooltip("退出登录"));

        LOG.info("功能侧边栏控制器初始化完成");
    }

    /**
     * 处理 AI 设置按钮点击
     */
    @FXML
    private void handleAiSetting() {
        selectButton(aiSettingBtn);
        LOG.info("切换到AI设置");
        org.example.client.App.switchToAi();
    }

    /**
     * 处理消息按钮点击
     */
    @FXML
    private void handleMessage() {
        selectButton(messageBtn);
        LOG.info("切换到消息");
        org.example.client.App.switchToMainFromAi();
    }

    /**
     * 处理退出登录按钮点击
     */
    @FXML
    private void handleLogout() {
        LOG.info("退出登录");

        // 显示确认对话框
        final javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION);
        confirm.setTitle("退出登录");
        confirm.setHeaderText("确认退出登录");
        confirm.setContentText("确定要退出登录吗？");
        confirm.showAndWait().ifPresent(response -> {
            if (response == javafx.scene.control.ButtonType.OK) {
                // 调用App.switchToLogin()切换到登录界面
                org.example.client.App.switchToLogin();
            }
        });
    }

    /**
     * 选中指定按钮，取消其他按钮的选中态
     *
     * @param btn 要选中的按钮
     */
    private void selectButton(final Button btn) {
        if (selectedBtn != null) {
            selectedBtn.getStyleClass().remove("func-btn-selected");
        }
        btn.getStyleClass().add("func-btn-selected");
        selectedBtn = btn;
    }
}
