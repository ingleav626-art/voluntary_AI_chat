package org.example.client.controller;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.example.client.model.AiProfile;
import org.example.client.view.AiViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI 角色编辑对话框
 *
 * <p>提供创建和编辑 AI 角色的模态对话框。</p>
 */
public final class AiEditDialog {

    private static final Logger LOG = LoggerFactory.getLogger(AiEditDialog.class);

    private AiEditDialog() {
        // 工具类，禁止实例化
    }

    /**
     * 显示创建 AI 角色对话框
     *
     * @param owner     父窗口
     * @param viewModel 视图模型
     */
    public static void showCreate(final Window owner, final AiViewModel viewModel) {
        showDialog(owner, viewModel, null);
    }

    /**
     * 显示编辑 AI 角色对话框
     *
     * @param owner     父窗口
     * @param viewModel 视图模型
     * @param profile   要编辑的 AI 角色
     */
    public static void showEdit(final Window owner, final AiViewModel viewModel, final AiProfile profile) {
        showDialog(owner, viewModel, profile);
    }

    private static void showDialog(final Window owner, final AiViewModel viewModel,
                                    final AiProfile profile) {
        try {
            final javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    AiEditDialog.class.getResource("/fxml/ai_edit_dialog.fxml"));
            final Parent root = loader.load();

            final AiEditController controller = loader.getController();
            controller.setViewModel(viewModel);

            if (profile != null) {
                controller.setEditMode(profile);
            } else {
                controller.setCreateMode();
            }

            final Stage dialogStage = new Stage();
            dialogStage.setTitle(profile != null ? "编辑 AI 角色" : "创建 AI 角色");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initOwner(owner);

            final Scene scene = new Scene(root);
            final java.net.URL cssUrl = AiEditDialog.class.getResource("/css/default.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
            dialogStage.setScene(scene);
            dialogStage.setResizable(false);
            dialogStage.showAndWait();

        } catch (final Exception e) {
            LOG.error("加载AI编辑对话框失败", e);
            NotificationDialog.showWarning("无法打开编辑面板", "请稍后重试");
        }
    }
}
