package org.example.client.controller;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import org.example.client.view.GroupListViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 修改群头像对话框控制器
 *
 * <p>提供群头像的修改功能（仅群主可操作）。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class GroupAvatarController implements Initializable {

    private static final Logger LOG = LoggerFactory.getLogger(GroupAvatarController.class);

    @FXML
    private Circle avatarCircle;

    @FXML
    private Label avatarText;

    @FXML
    private ImageView avatarImage;

    @FXML
    private StackPane avatarPreviewContainer;

    @FXML
    private TextField avatarUrlField;

    @FXML
    private Label errorLabel;

    @FXML
    private Button confirmButton;

    @FXML
    private Button cancelButton;

    /** 群组ID */
    private Long groupId;

    /** ViewModel 引用 */
    private GroupListViewModel viewModel;

    /** 当前群名称（用于显示默认头像文字） */
    private String groupName;

    /** 当前头像URL */
    private String currentAvatar;

    /**
     * 初始化对话框数据
     *
     * @param groupId  群组ID
     * @param viewModel ViewModel
     * @param groupName 群名称（用于默认头像）
     * @param currentAvatar 当前头像URL
     */
    public void initData(final Long groupId, final GroupListViewModel viewModel,
                         final String groupName, final String currentAvatar) {
        this.groupId = groupId;
        this.viewModel = viewModel;
        this.groupName = groupName;
        this.currentAvatar = currentAvatar;

        // 设置输入框初始值
        avatarUrlField.setText(currentAvatar != null ? currentAvatar : "");

        // 更新头像预览
        updateAvatarPreview(currentAvatar);

        // 监听输入框变化，实时预览
        avatarUrlField.textProperty().addListener((obs, oldVal, newVal) -> {
            updateAvatarPreview(newVal);
        });
    }

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        LOG.info("修改群头像对话框控制器初始化完成");
    }

    /**
     * 更新头像预览
     *
     * @param url 头像URL
     */
    private void updateAvatarPreview(final String url) {
        if (url == null || url.trim().isEmpty()) {
            // 显示默认头像（群名首字）
            avatarCircle.setFill(javafx.scene.paint.Color.valueOf("#E76F51"));
            avatarText.setText(groupName != null && !groupName.isEmpty()
                    ? String.valueOf(groupName.charAt(0)) : "群");
            avatarText.setVisible(true);
            avatarImage.setVisible(false);
        } else {
            // 尝试加载图片
            try {
                final Image image = new Image(url.trim(), true);
                image.errorProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal) {
                        // 图片加载失败，显示默认头像
                        avatarCircle.setFill(javafx.scene.paint.Color.valueOf("#E76F51"));
                        avatarText.setText(groupName != null && !groupName.isEmpty()
                                ? String.valueOf(groupName.charAt(0)) : "群");
                        avatarText.setVisible(true);
                        avatarImage.setVisible(false);
                        LOG.warn("头像图片加载失败: {}", url);
                    }
                });

                // 设置圆形裁剪
                final Circle clip = new Circle(50);
                avatarImage.setClip(clip);

                avatarImage.setImage(image);
                avatarImage.setVisible(true);
                avatarText.setVisible(false);
            } catch (final Exception e) {
                LOG.warn("头像图片URL无效: {}", url, e);
                // 显示默认头像
                avatarCircle.setFill(javafx.scene.paint.Color.valueOf("#E76F51"));
                avatarText.setText(groupName != null && !groupName.isEmpty()
                        ? String.valueOf(groupName.charAt(0)) : "群");
                avatarText.setVisible(true);
                avatarImage.setVisible(false);
            }
        }
    }

    /**
     * 处理确认修改
     */
    @FXML
    private void handleConfirm() {
        final String avatarUrl = avatarUrlField.getText();
        // URL可以为空（表示清除头像）

        // 简单校验URL格式（如果有输入）
        if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
            final String trimmed = avatarUrl.trim();
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                errorLabel.setText("头像URL需以 http:// 或 https:// 开头");
                return;
            }
            // 校验图片格式
            if (!trimmed.matches(".*\\.(jpg|jpeg|png|gif|webp|bmp)(\\?.*)?$")) {
                errorLabel.setText("请输入有效的图片URL（jpg/png/gif/webp）");
                return;
            }
        }

        confirmButton.setDisable(true);
        errorLabel.setText("");

        LOG.info("修改群头像: groupId={}, avatarUrl={}", groupId, avatarUrl);

        // 调用 ViewModel 更新群信息（仅更新头像）
        viewModel.updateGroupInfo(
                groupId,
                null,  // 不修改群名称
                null,  // 不修改公告
                false, // 不修改置顶状态
                avatarUrl != null ? avatarUrl.trim() : null
        );

        closeWindow();
    }

    /**
     * 处理取消
     */
    @FXML
    private void handleCancel() {
        closeWindow();
    }

    /**
     * 关闭窗口
     */
    private void closeWindow() {
        final Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }
}