package org.example.client.controller;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;

/**
 * 图片预览对话框
 *
 * <p>用于选择图片后显示预览，用户确认后再上传。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public class ImagePreviewDialog {

    private static final Logger LOG = LoggerFactory.getLogger(ImagePreviewDialog.class);

    private final Stage stage;
    private final File imageFile;
    private boolean confirmed = false;

    /**
     * 构造图片预览对话框
     *
     * @param imageFile 图片文件
     */
    public ImagePreviewDialog(final File imageFile) {
        this.imageFile = imageFile;
        this.stage = new Stage();

        initDialog();
    }

    /** 预览窗口尺寸 */
    private static final double PREVIEW_WINDOW_WIDTH = 700;
    private static final double PREVIEW_WINDOW_HEIGHT = 560;

    /** 图片预览区域内边距 */
    private static final double IMAGE_CONTAINER_PADDING = 20;

    /**
     * 初始化对话框
     */
    private void initDialog() {
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.UTILITY);
        stage.setTitle("图片预览");
        stage.setResizable(false);

        // 创建根容器
        final BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f5f5f5;");
        root.setPadding(new Insets(15));

        // 文件信息（顶部）
        final VBox fileInfo = createFileInfo();
        root.setTop(fileInfo);

        // 操作按钮（底部）
        final HBox buttons = createButtons();
        root.setBottom(buttons);

        // 加载图片预览（中央区域，等比缩放适配）
        final ImageView imageView = new ImageView();
        try {
            // 使用同步加载，确保能获取到图片实际尺寸
            final Image image = new Image(imageFile.toURI().toString());
            imageView.setImage(image);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);

            // 计算可用的图片展示区域
            // 窗口高度 - 根容器内边距 - 文件信息区(约110px) - 按钮区(约60px) - 图片容器内边距
            final double availableWidth = PREVIEW_WINDOW_WIDTH - 30 - IMAGE_CONTAINER_PADDING * 2 - 10;
            final double availableHeight = PREVIEW_WINDOW_HEIGHT - 30 - 110 - 60 - IMAGE_CONTAINER_PADDING * 2;

            // 等比例缩放：计算缩放因子使整张图片都能在可用区域内显示
            final double scaleX = availableWidth / image.getWidth();
            final double scaleY = availableHeight / image.getHeight();
            final double scale = Math.min(scaleX, scaleY);

            final double displayWidth = image.getWidth() * scale;
            final double displayHeight = image.getHeight() * scale;

            imageView.setFitWidth(displayWidth);
            imageView.setFitHeight(displayHeight);

            // 图片容器：使用 StackPane 让图片居中
            final StackPane imageContainer = new StackPane(imageView);
            imageContainer.setAlignment(Pos.CENTER);
            imageContainer.setPadding(new Insets(IMAGE_CONTAINER_PADDING));
            imageContainer.setStyle("-fx-background-color: white; -fx-border-color: #ddd; -fx-border-radius: 5;");
            imageContainer.setMinHeight(200);

            root.setCenter(imageContainer);

        } catch (final Exception e) {
            LOG.error("图片预览加载失败: {}", imageFile.getAbsolutePath(), e);
            final Label errorLabel = new Label("图片预览加载失败");
            errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 16px;");
            root.setCenter(errorLabel);
        }

        // 创建场景
        final Scene scene = new Scene(root, PREVIEW_WINDOW_WIDTH, PREVIEW_WINDOW_HEIGHT);
        stage.setScene(scene);

        // ESC键取消
        scene.setOnKeyPressed(event -> {
            if (event.getCode().toString().equals("ESCAPE")) {
                cancel();
            }
        });
    }

    /**
     * 创建文件信息区域
     *
     * @return 文件信息
     */
    private VBox createFileInfo() {
        final VBox fileInfo = new VBox(5);
        fileInfo.setAlignment(Pos.CENTER_LEFT);
        fileInfo.setPadding(new Insets(10));
        fileInfo.setStyle("-fx-background-color: #e8e8e8; -fx-border-radius: 5;");

        // 文件名
        final Label nameLabel = new Label("文件名: " + imageFile.getName());
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        // 文件大小
        final long sizeInKB = imageFile.length() / 1024;
        final long sizeInMB = sizeInKB / 1024;
        final String sizeText = sizeInMB > 0 ? sizeInMB + " MB" : sizeInKB + " KB";
        final Label sizeLabel = new Label("文件大小: " + sizeText);
        sizeLabel.setStyle("-fx-font-size: 12px;");

        // 文件路径
        final Label pathLabel = new Label("文件路径: " + imageFile.getAbsolutePath());
        pathLabel.setStyle("-fx-font-size: 12px;");
        pathLabel.setWrapText(true);

        // 大小警告（超过10MB）
        if (sizeInMB >= 10) {
            final Label warningLabel = new Label("⚠️ 文件大小超过10MB，可能无法上传");
            warningLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #F44336; -fx-font-weight: bold;");
            fileInfo.getChildren().add(warningLabel);
        }

        fileInfo.getChildren().addAll(nameLabel, sizeLabel, pathLabel);

        return fileInfo;
    }

    /**
     * 创建操作按钮
     *
     * @return 按钮区域
     */
    private HBox createButtons() {
        final HBox buttons = new HBox(20);
        buttons.setAlignment(Pos.CENTER);
        buttons.setPadding(new Insets(15));

        // 确认按钮
        final Button confirmBtn = new Button("确认上传");
        confirmBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 10 20;");
        confirmBtn.setOnAction(e -> confirm());

        // 取消按钮
        final Button cancelBtn = new Button("取消");
        cancelBtn.setStyle("-fx-background-color: #F44336; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 10 20;");
        cancelBtn.setOnAction(e -> cancel());

        buttons.getChildren().addAll(confirmBtn, cancelBtn);

        return buttons;
    }

    /**
     * 确认上传
     */
    private void confirm() {
        confirmed = true;
        stage.close();
        LOG.info("用户确认上传图片: {}", imageFile.getName());
    }

    /**
     * 取消上传
     */
    private void cancel() {
        confirmed = false;
        stage.close();
        LOG.info("用户取消上传图片: {}", imageFile.getName());
    }

    /**
     * 显示对话框并等待用户确认
     *
     * @return 是否确认上传
     */
    public boolean showAndWait() {
        stage.showAndWait();
        return confirmed;
    }

    /**
     * 获取图片文件路径
     *
     * @return 图片文件路径
     */
    public Path getImagePath() {
        return imageFile.toPath();
    }

    /**
     * 获取Stage
     *
     * @return Stage
     */
    public Stage getStage() {
        return stage;
    }
}