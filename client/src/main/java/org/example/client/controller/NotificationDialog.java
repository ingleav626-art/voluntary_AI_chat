package org.example.client.controller;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 通用通知弹窗
 *
 * <p>可复用的成功/提示弹窗，显示标题、消息和确认按钮。
 * 其他功能模块也可使用此弹窗显示操作结果。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class NotificationDialog {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationDialog.class);

    /** 默认弹窗宽度 */
    private static final double DEFAULT_WIDTH = 380;

    private NotificationDialog() {
        // 工具类禁止实例化
    }

    /**
     * 显示成功通知弹窗
     *
     * @param title   标题
     * @param message 消息内容
     */
    public static void showSuccess(final String title, final String message) {
        show(title, message, Type.SUCCESS);
    }

    /**
     * 显示信息通知弹窗
     *
     * @param title   标题
     * @param message 消息内容
     */
    public static void showInfo(final String title, final String message) {
        show(title, message, Type.INFO);
    }

    /**
     * 显示警告通知弹窗
     *
     * @param title   标题
     * @param message 消息内容
     */
    public static void showWarning(final String title, final String message) {
        show(title, message, Type.WARNING);
    }

    /**
     * 显示错误通知弹窗
     *
     * @param title   标题
     * @param message 消息内容
     */
    public static void showError(final String title, final String message) {
        show(title, message, Type.ERROR);
    }

    /**
     * 显示通知弹窗
     *
     * @param title   标题
     * @param message 消息内容
     * @param type    通知类型
     */
    private static void show(final String title, final String message, final Type type) {
        final Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setResizable(false);
        dialog.setTitle(title);

        // 根类型图标
        final Label iconLabel = new Label(type.getIcon());
        iconLabel.setStyle("-fx-font-size: 32px;");

        // 标题
        final Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // 消息内容
        final Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(300);
        messageLabel.setStyle("-fx-font-size: 14px;");

        // 确认按钮
        final Button confirmBtn = new Button("确定");
        confirmBtn.setDefaultButton(true);
        confirmBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; "
                + "-fx-font-size: 14px; -fx-font-weight: bold; "
                + "-fx-padding: 10px 32px; -fx-background-radius: 5; "
                + "-fx-cursor: hand;");
        confirmBtn.setOnAction(e -> dialog.close());

        // 布局
        final VBox content = new VBox(12);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(24, 20, 16, 20));
        content.getChildren().addAll(iconLabel, titleLabel, messageLabel);

        final HBox buttonBar = new HBox();
        buttonBar.setAlignment(Pos.CENTER);
        buttonBar.setPadding(new Insets(8, 20, 16, 20));
        buttonBar.getChildren().add(confirmBtn);

        final VBox root = new VBox(content, buttonBar);
        root.setStyle("-fx-background-color: white; -fx-background-radius: 8;");

        // 根据类型设置标题颜色
        titleLabel.setStyle(titleLabel.getStyle() + " -fx-text-fill: " + type.getColor() + ";");

        final Scene scene = new Scene(root, DEFAULT_WIDTH, -1);
        dialog.setScene(scene);

        // 应用默认样式
        final java.net.URL cssUrl = NotificationDialog.class.getResource("/css/default.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        dialog.showAndWait();

        LOG.info("通知弹窗: type={}, title={}", type, title);
    }

    /**
     * 通知类型
     */
    private enum Type {
        SUCCESS("#4CAF50", "\u2714"),
        INFO("#2196F3", "\u2139"),
        WARNING("#FF9800", "\u26A0"),
        ERROR("#F44336", "\u2716");

        private final String color;
        private final String icon;

        Type(final String color, final String icon) {
            this.color = color;
            this.icon = icon;
        }

        public String getColor() {
            return color;
        }

        public String getIcon() {
            return icon;
        }
    }
}
