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

/**
 * 图片查看对话框
 *
 * <p>用于点击图片后弹出窗口显示原图，支持缩放和滚动。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public class ImageViewerDialog {

    private static final Logger LOG = LoggerFactory.getLogger(ImageViewerDialog.class);

    private final Stage stage;
    private final ImageView imageView;
    private final String imageUrl;
    private StackPane imageContainer; // 图片容器（成员变量）
    private double currentScale = 1.0;
    private static final double MIN_SCALE = 0.1;
    private static final double MAX_SCALE = 5.0;
    private static final double SCALE_STEP = 0.2;

    /**
     * 构造图片查看对话框
     *
     * @param imageUrl 图片URL
     */
    public ImageViewerDialog(final String imageUrl) {
        this.imageUrl = imageUrl;
        this.stage = new Stage();
        this.imageView = new ImageView();

        initDialog();
    }

    /**
     * 初始化对话框
     */
    private void initDialog() {
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle("图片查看");

        // 创建根容器
        final BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: rgba(0, 0, 0, 0.9);");

        // 加载图片
        try {
            final Image image = new Image(imageUrl, true);
            imageView.setImage(image);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);

            // 初始尺寸：适应窗口
            final double initialWidth = Math.min(image.getWidth(), 800);
            final double initialHeight = Math.min(image.getHeight(), 600);
            imageView.setFitWidth(initialWidth);
            imageView.setFitHeight(initialHeight);

            // 图片容器（支持滚动）
            imageContainer = new StackPane(imageView);
            imageContainer.setAlignment(Pos.CENTER);
            imageContainer.setPadding(new Insets(20));

            // 鼠标滚轮缩放
            imageContainer.setOnScroll(event -> {
                final double delta = event.getDeltaY();
                if (delta > 0) {
                    zoomIn();
                } else if (delta < 0) {
                    zoomOut();
                }
                event.consume();
            });

            // 双击恢复原始大小
            imageContainer.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    resetZoom();
                }
            });

            root.setCenter(imageContainer);

        } catch (final Exception e) {
            LOG.error("图片加载失败: {}", imageUrl, e);
            final Label errorLabel = new Label("图片加载失败");
            errorLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16px;");
            root.setCenter(errorLabel);
        }

        // 顶部工具栏
        final HBox toolbar = createToolbar();
        root.setTop(toolbar);

        // 底部信息栏
        final VBox infoBar = createInfoBar();
        root.setBottom(infoBar);

        // 创建场景
        final Scene scene = new Scene(root, 900, 700);
        stage.setScene(scene);

        // ESC键关闭
        scene.setOnKeyPressed(event -> {
            if (event.getCode().toString().equals("ESCAPE")) {
                close();
            }
        });

        // 点击空白区域关闭
        root.setOnMouseClicked(event -> {
            if (event.getTarget() == root || event.getTarget() == imageContainer) {
                close();
            }
        });
    }

    /**
     * 创建顶部工具栏
     *
     * @return 工具栏
     */
    private HBox createToolbar() {
        final HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_RIGHT);
        toolbar.setPadding(new Insets(10));
        toolbar.setStyle("-fx-background-color: rgba(50, 50, 50, 0.8);");

        // 放大按钮
        final Button zoomInBtn = new Button("放大 (+)");
        zoomInBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        zoomInBtn.setOnAction(e -> zoomIn());

        // 缩小按钮
        final Button zoomOutBtn = new Button("缩小 (-)");
        zoomOutBtn.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white;");
        zoomOutBtn.setOnAction(e -> zoomOut());

        // 重置按钮
        final Button resetBtn = new Button("重置 (双击)");
        resetBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
        resetBtn.setOnAction(e -> resetZoom());

        // 关闭按钮
        final Button closeBtn = new Button("关闭 (ESC)");
        closeBtn.setStyle("-fx-background-color: #F44336; -fx-text-fill: white;");
        closeBtn.setOnAction(e -> close());

        toolbar.getChildren().addAll(zoomInBtn, zoomOutBtn, resetBtn, closeBtn);

        return toolbar;
    }

    /**
     * 创建底部信息栏
     *
     * @return 信息栏
     */
    private VBox createInfoBar() {
        final VBox infoBar = new VBox(5);
        infoBar.setAlignment(Pos.CENTER);
        infoBar.setPadding(new Insets(10));
        infoBar.setStyle("-fx-background-color: rgba(50, 50, 50, 0.8);");

        // 图片URL
        final Label urlLabel = new Label("URL: " + imageUrl);
        urlLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px;");
        urlLabel.setWrapText(true);

        // 缩放比例
        final Label scaleLabel = new Label("缩放: 100%");
        scaleLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px;");
        scaleLabel.textProperty().bind(
                javafx.beans.binding.Bindings.format("缩放: %.0f%%", currentScale * 100));

        infoBar.getChildren().addAll(urlLabel, scaleLabel);

        return infoBar;
    }

    /**
     * 放大图片
     */
    private void zoomIn() {
        if (currentScale < MAX_SCALE) {
            currentScale += SCALE_STEP;
            applyZoom();
        }
    }

    /**
     * 缩小图片
     */
    private void zoomOut() {
        if (currentScale > MIN_SCALE) {
            currentScale -= SCALE_STEP;
            applyZoom();
        }
    }

    /**
     * 重置缩放
     */
    private void resetZoom() {
        currentScale = 1.0;
        applyZoom();
    }

    /**
     * 应用缩放
     */
    private void applyZoom() {
        final Image image = imageView.getImage();
        if (image != null) {
            final double baseWidth = Math.min(image.getWidth(), 800);
            final double baseHeight = Math.min(image.getHeight(), 600);
            imageView.setFitWidth(baseWidth * currentScale);
            imageView.setFitHeight(baseHeight * currentScale);
            LOG.debug("图片缩放: scale={}", currentScale);
        }
    }

    /**
     * 显示对话框
     */
    public void show() {
        stage.show();
        LOG.info("显示图片查看对话框: url={}", imageUrl);
    }

    /**
     * 关闭对话框
     */
    public void close() {
        stage.close();
        LOG.info("关闭图片查看对话框");
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