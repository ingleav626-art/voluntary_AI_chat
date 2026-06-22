package org.example.client.controller;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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
 * <p>用于点击图片后弹出窗口显示原图，支持缩放、拖拽移动和滚动。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public class ImageViewerDialog {

    private static final Logger LOG = LoggerFactory.getLogger(ImageViewerDialog.class);

    private final Stage stage;
    private final ImageView imageView;
    private final String imageUrl;
    private ScrollPane scrollPane;
    private StackPane imageContainer;
    private final DoubleProperty currentScale = new SimpleDoubleProperty(1.0);
    private static final double MIN_SCALE = 0.1;
    private static final double MAX_SCALE = 5.0;
    private static final double SCALE_STEP = 0.2;

    private static final double DEFAULT_PREVIEW_WIDTH = 800;
    private static final double DEFAULT_PREVIEW_HEIGHT = 600;

    private double imageOriginalWidth = DEFAULT_PREVIEW_WIDTH;
    private double imageOriginalHeight = DEFAULT_PREVIEW_HEIGHT;

    /** 拖拽相关 */
    private double dragStartX;
    private double dragStartY;
    private double imageTranslateX;
    private double imageTranslateY;

    public ImageViewerDialog(final String imageUrl) {
        this.imageUrl = imageUrl;
        this.stage = new Stage();
        this.imageView = new ImageView();
        initDialog();
    }

    private void initDialog() {
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle("图片查看");

        // 根容器：VBox，工具栏固定顶部，ScrollPane 居中可滚动，信息栏固定底部
        final VBox root = new VBox();
        root.setStyle("-fx-background-color: rgba(0, 0, 0, 0.9);");

        // 1. 顶部工具栏（固定，不随图片滚动/缩放移动）
        final HBox toolbar = createToolbar();
        root.getChildren().add(toolbar);

        // 2. 图片展示区（ScrollPane 包裹，滚动条在缩放后出现）
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setFitWidth(DEFAULT_PREVIEW_WIDTH);
        imageView.setFitHeight(DEFAULT_PREVIEW_HEIGHT);

        imageContainer = new StackPane(imageView);
        imageContainer.setAlignment(Pos.CENTER);
        imageContainer.setPadding(new Insets(20));
        imageContainer.setStyle("-fx-background-color: transparent;");

        scrollPane = new ScrollPane(imageContainer);
        scrollPane.setStyle("-fx-background: rgba(0,0,0,0.9); -fx-background-color: transparent;");
        // fitToWidth/fitToHeight = true 使 imageContainer 填满 ScrollPane 视口，
        // 配合 StackPane 的 Pos.CENTER，小图也能上下左右居中显示
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setPannable(false);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scrollPane, javafx.scene.layout.Priority.ALWAYS);

        root.getChildren().add(scrollPane);

        // 3. 底部信息栏（固定）
        final VBox infoBar = createInfoBar();
        root.getChildren().add(infoBar);

        // 鼠标滚轮缩放
        scrollPane.setOnScroll(event -> {
            final double delta = event.getDeltaY();
            if (delta > 0) {
                zoomIn();
            } else if (delta < 0) {
                zoomOut();
            }
            event.consume();
        });

        // 双击恢复原始大小
        scrollPane.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                resetZoom();
            }
        });

        // 鼠标拖拽移动图片（缩放后拖动浏览）
        scrollPane.setOnMousePressed(event -> {
            dragStartX = event.getScreenX();
            dragStartY = event.getScreenY();
            imageTranslateX = imageView.getTranslateX();
            imageTranslateY = imageView.getTranslateY();
        });

        scrollPane.setOnMouseDragged(event -> {
            final double deltaX = event.getScreenX() - dragStartX;
            final double deltaY = event.getScreenY() - dragStartY;
            imageView.setTranslateX(imageTranslateX + deltaX);
            imageView.setTranslateY(imageTranslateY + deltaY);
        });

        // 异步加载图片
        org.example.client.service.ChatService.getInstance()
                .loadImageBytes(imageUrl)
                .thenAccept(bytes -> javafx.application.Platform.runLater(() -> {
                    if (bytes != null && bytes.length > 0) {
                        try {
                            final Image image = new Image(
                                    new java.io.ByteArrayInputStream(bytes));
                            imageView.setImage(image);
                            imageOriginalWidth = image.getWidth();
                            imageOriginalHeight = image.getHeight();
                            final double fitWidth = Math.min(imageOriginalWidth, DEFAULT_PREVIEW_WIDTH);
                            final double fitHeight = Math.min(imageOriginalHeight, DEFAULT_PREVIEW_HEIGHT);
                            imageView.setFitWidth(fitWidth);
                            imageView.setFitHeight(fitHeight);
                        } catch (final Exception e) {
                            LOG.error("图片解码失败: {}", imageUrl, e);
                            showError("图片解码失败");
                        }
                    } else {
                        LOG.warn("图片数据为空: {}", imageUrl);
                        showError("图片数据为空");
                    }
                }))
                .exceptionally(ex -> {
                    LOG.error("图片加载失败: {}", imageUrl, ex);
                    javafx.application.Platform.runLater(() -> showError("图片加载失败"));
                    return null;
                });

        final Scene scene = new Scene(root, 900, 700);
        stage.setScene(scene);

        scene.setOnKeyPressed(event -> {
            if (event.getCode().toString().equals("ESCAPE")) {
                close();
            }
        });
    }

    private HBox createToolbar() {
        final HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_RIGHT);
        toolbar.setPadding(new Insets(10));
        toolbar.setStyle("-fx-background-color: rgba(50, 50, 50, 0.8);");

        final Button zoomInBtn = new Button("放大 (+)");
        zoomInBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        zoomInBtn.setOnAction(e -> zoomIn());

        final Button zoomOutBtn = new Button("缩小 (-)");
        zoomOutBtn.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white;");
        zoomOutBtn.setOnAction(e -> zoomOut());

        final Button resetBtn = new Button("重置 (双击)");
        resetBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
        resetBtn.setOnAction(e -> resetZoom());

        final Button closeBtn = new Button("关闭 (ESC)");
        closeBtn.setStyle("-fx-background-color: #F44336; -fx-text-fill: white;");
        closeBtn.setOnAction(e -> close());

        toolbar.getChildren().addAll(zoomInBtn, zoomOutBtn, resetBtn, closeBtn);
        return toolbar;
    }

    private VBox createInfoBar() {
        final VBox infoBar = new VBox(5);
        infoBar.setAlignment(Pos.CENTER);
        infoBar.setPadding(new Insets(10));
        infoBar.setStyle("-fx-background-color: rgba(50, 50, 50, 0.8);");

        final Label urlLabel = new Label("URL: " + imageUrl);
        urlLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px;");
        urlLabel.setWrapText(true);

        final Label scaleLabel = new Label();
        scaleLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px;");
        scaleLabel.textProperty().bind(
                javafx.beans.binding.Bindings.format("缩放: %.0f%%", currentScale.multiply(100)));

        infoBar.getChildren().addAll(urlLabel, scaleLabel);
        return infoBar;
    }

    private void zoomIn() {
        if (currentScale.get() < MAX_SCALE) {
            currentScale.set(currentScale.get() + SCALE_STEP);
            applyZoom();
        }
    }

    private void zoomOut() {
        if (currentScale.get() > MIN_SCALE) {
            currentScale.set(currentScale.get() - SCALE_STEP);
            applyZoom();
        }
    }

    private void resetZoom() {
        currentScale.set(1.0);
        imageView.setTranslateX(0);
        imageView.setTranslateY(0);
        applyZoom();
    }

    private void applyZoom() {
        final double baseWidth = Math.min(imageOriginalWidth, DEFAULT_PREVIEW_WIDTH);
        final double baseHeight = Math.min(imageOriginalHeight, DEFAULT_PREVIEW_HEIGHT);
        final double scale = currentScale.get();
        imageView.setFitWidth(baseWidth * scale);
        imageView.setFitHeight(baseHeight * scale);
        LOG.debug("图片缩放: scale={}", scale);
    }

    public void show() {
        stage.show();
        LOG.info("显示图片查看对话框: url={}", imageUrl);
    }

    public void close() {
        stage.close();
        LOG.info("关闭图片查看对话框");
    }

    private void showError(final String message) {
        if (scrollPane != null) {
            final Label errorLabel = new Label(message);
            errorLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16px;");
            errorLabel.setAlignment(Pos.CENTER);
            final StackPane errorPane = new StackPane(errorLabel);
            scrollPane.setContent(errorPane);
        }
    }

    public Stage getStage() {
        return stage;
    }
}
