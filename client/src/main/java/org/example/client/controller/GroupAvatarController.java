package org.example.client.controller;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.client.service.UserService;
import org.example.client.util.ImageUtils;
import org.example.client.view.GroupListViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 修改群头像对话框控制器
 *
 * <p>提供群头像的修改功能（仅群主可操作），支持文件上传。</p>
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
    private Button chooseFileButton;

    @FXML
    private Label fileNameLabel;

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

    /** 选择的文件 */
    private File selectedFile;

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

        // 更新头像预览
        updateAvatarPreview(currentAvatar);
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
            showDefaultAvatar();
        } else {
            ImageUtils.loadAvatarToCircle(url, avatarCircle, avatarCircle.getRadius());
            avatarText.setVisible(false);
        }
    }

    /**
     * 显示默认头像
     */
    private void showDefaultAvatar() {
        avatarCircle.setFill(javafx.scene.paint.Color.valueOf("#E76F51"));
        avatarText.setText(groupName != null && !groupName.isEmpty()
                ? String.valueOf(groupName.charAt(0)) : "群");
        avatarText.setVisible(true);
        avatarImage.setVisible(false);
    }

    /**
     * 选择头像文件
     */
    @FXML
    private void handleChooseFile() {
        final FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择群头像");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("图片文件", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.webp"),
                new FileChooser.ExtensionFilter("所有文件", "*.*")
        );

        final Stage stage = (Stage) chooseFileButton.getScene().getWindow();
        final File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            selectedFile = file;
            fileNameLabel.setText(file.getName());
            errorLabel.setText("");

            // 预览选中的图片
            try {
                final Image preview = new Image(file.toURI().toString());
                final Circle clip = new Circle(50);
                avatarImage.setClip(clip);
                avatarImage.setImage(preview);
                avatarImage.setVisible(true);
                avatarText.setVisible(false);
                avatarCircle.setFill(javafx.scene.paint.Color.valueOf("#E76F51"));
            } catch (final Exception e) {
                LOG.warn("预览图片失败: {}", file.getName(), e);
                showDefaultAvatar();
            }
        }
    }

    /**
     * 处理确认修改
     */
    @FXML
    private void handleConfirm() {
        if (selectedFile == null) {
            errorLabel.setText("请先选择头像图片");
            return;
        }

        confirmButton.setDisable(true);
        chooseFileButton.setDisable(true);
        errorLabel.setText("");

        LOG.info("【群头像上传】开始上传: groupId={}, file={}", groupId, selectedFile.getName());

        // 先上传头像文件
        UserService.getInstance().uploadAvatar(selectedFile.toPath())
                .thenAcceptAsync(response -> {
                    if (response != null && response.isSuccess() && response.getData() != null) {
                        final String avatarUrl = response.getData();
                        LOG.info("【群头像上传】上传成功: avatarUrl={}", avatarUrl);

                        // 调用 ViewModel 更新群信息
                        viewModel.updateGroupInfo(
                                groupId,
                                null,  // 不修改群名称
                                null,  // 不修改公告
                                false, // 不修改置顶状态
                                avatarUrl
                        );

                        Platform.runLater(this::closeWindow);
                    } else {
                        final String msg = response != null ? response.getMessage() : "上传失败";
                        LOG.warn("【群头像上传】上传失败: {}", msg);
                        Platform.runLater(() -> {
                            errorLabel.setText("上传失败: " + msg);
                            confirmButton.setDisable(false);
                            chooseFileButton.setDisable(false);
                        });
                    }
                })
                .exceptionally(throwable -> {
                    LOG.error("【群头像上传】上传异常: {}", throwable.getMessage(), throwable);
                    Platform.runLater(() -> {
                        errorLabel.setText("上传异常: " + throwable.getMessage());
                        confirmButton.setDisable(false);
                        chooseFileButton.setDisable(false);
                    });
                    return null;
                });
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
