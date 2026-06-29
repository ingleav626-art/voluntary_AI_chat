package org.example.client.controller;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 群 AI 配置管理对话框入口
 */
public final class GroupAiConfigDialog {

    private static final Logger LOG = LoggerFactory.getLogger(GroupAiConfigDialog.class);

    private GroupAiConfigDialog() {
    }

    /**
     * 显示群 AI 配置管理弹窗
     *
     * @param owner   父窗口
     * @param groupId 群组ID
     */
    public static void show(final Window owner, final Long groupId) {
        try {
            final javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    GroupAiConfigDialog.class.getResource("/fxml/group_ai_config_dialog.fxml"));
            final Parent root = loader.load();

            final GroupAiConfigController controller = loader.getController();
            controller.setGroupId(groupId);

            final Stage dialogStage = new Stage();
            dialogStage.setTitle("群 AI 配置");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initOwner(owner);

            final Scene scene = new Scene(root);
            final java.net.URL cssUrl = GroupAiConfigDialog.class.getResource("/css/default.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
            dialogStage.setScene(scene);
            dialogStage.setResizable(false);
            dialogStage.showAndWait();

        } catch (final Exception e) {
            LOG.error("加载群AI配置对话框失败", e);
            NotificationDialog.showWarning("无法打开配置面板", "请稍后重试");
        }
    }
}
