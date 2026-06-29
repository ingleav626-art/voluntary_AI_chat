package org.example.client.util;

import java.util.concurrent.atomic.AtomicBoolean;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.SnapshotParameters;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/**
 * 毛玻璃磨砂效果工具（方案A：Snapshot + GaussianBlur）
 *
 * <p>通过截取背景区域快照并应用高斯模糊，模拟 Win11/macOS 的毛玻璃效果。
 * 由于 JavaFX 原生不支持 CSS backdrop-filter，本方案采用纯 JavaFX 实现的折中方案。</p>
 *
 * <p>性能优化策略：</p>
 * <ul>
 *   <li>节流更新：仅在窗口尺寸变化时更新快照，避免每帧截图</li>
 *   <li>降频检查：默认每 200ms 检查一次是否需要更新</li>
 *   <li>模糊半径适中：默认 20px，兼顾效果与性能</li>
 * </ul>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class FrostGlassEffect {

    /** 默认高斯模糊半径（像素） */
    private static final double DEFAULT_BLUR_RADIUS = 20.0;

    /** 默认半透明白色叠加（模拟毛玻璃底色） */
    private static final Color DEFAULT_TINT = Color.rgb(255, 255, 255, 0.55);

    /** 检查更新间隔（毫秒） */
    private static final long UPDATE_CHECK_INTERVAL_MS = 200L;

    /** 目标节点（应用毛玻璃的节点，如顶栏、功能栏） */
    private final Region target;

    /** 背景源节点（被截图的节点，如根节点） */
    private final Region backgroundSource;

    /** 模糊半径 */
    private final double blurRadius;

    /** 底色叠加 */
    private final Color tint;

    /** 用于显示模糊背景的 ImageView（放在 target 父容器底层） */
    private ImageView blurView;

    /** 上次截图时的宽度，用于检测变化 */
    private double lastWidth = -1;

    /** 上次截图时的高度，用于检测变化 */
    private double lastHeight = -1;

    /** 上次更新时间戳 */
    private long lastUpdateTime = 0;

    /** 动画定时器（节流检查） */
    private AnimationTimer timer;

    /** 是否已挂载 */
    private final AtomicBoolean attached = new AtomicBoolean(false);

    private FrostGlassEffect(final Region target, final Region backgroundSource,
                             final double blurRadius, final Color tint) {
        this.target = target;
        this.backgroundSource = backgroundSource;
        this.blurRadius = blurRadius;
        this.tint = tint;
    }

    /**
     * 创建毛玻璃效果实例（默认参数）
     *
     * @param target          目标节点（如顶栏）
     * @param backgroundSource 背景源节点（如根节点）
     * @return 毛玻璃效果实例
     */
    public static FrostGlassEffect create(final Region target, final Region backgroundSource) {
        return new FrostGlassEffect(target, backgroundSource, DEFAULT_BLUR_RADIUS, DEFAULT_TINT);
    }

    /**
     * 创建毛玻璃效果实例（自定义参数）
     *
     * @param target          目标节点
     * @param backgroundSource 背景源节点
     * @param blurRadius      模糊半径
     * @param tint            底色叠加
     * @return 毛玻璃效果实例
     */
    public static FrostGlassEffect create(final Region target, final Region backgroundSource,
                                          final double blurRadius, final Color tint) {
        return new FrostGlassEffect(target, backgroundSource, blurRadius, tint);
    }

    /**
     * 启动毛玻璃效果
     *
     * <p>在 target 父容器（需为 Pane）底层插入一个 ImageView 显示模糊背景，
     * 并启动动画定时器节流检查是否需要更新快照。</p>
     */
    public void attach() {
        if (!attached.compareAndSet(false, true)) {
            return;
        }
        Platform.runLater(() -> {
            setupBlurView();
            startTimer();
        });
    }

    /**
     * 停止毛玻璃效果
     */
    public void detach() {
        if (!attached.compareAndSet(true, false)) {
            return;
        }
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        Platform.runLater(() -> {
            if (blurView != null && blurView.getParent() instanceof Pane) {
                ((Pane) blurView.getParent()).getChildren().remove(blurView);
            }
            blurView = null;
        });
    }

    /**
     * 强制立即刷新一次背景快照
     */
    public void refresh() {
        Platform.runLater(this::updateSnapshot);
    }

    /**
     * 设置模糊背景的 ImageView
     *
     * <p>将 ImageView 放入 target 的父容器（需为 Pane）的最底层，
     * 并设置鼠标透明，不影响 target 的交互。位置和大小绑定到 target。</p>
     */
    private void setupBlurView() {
        if (target.getParent() == null || !(target.getParent() instanceof Pane)) {
            return;
        }
        final Pane parent = (Pane) target.getParent();
        blurView = new ImageView();
        blurView.setMouseTransparent(true);
        blurView.setManaged(false);
        blurView.setPreserveRatio(false);
        blurView.setEffect(new GaussianBlur(blurRadius));
        // 插入到最底层（index 0），确保在 target 之下
        parent.getChildren().add(0, blurView);
        // 绑定位置和大小到 target
        blurView.layoutXProperty().bind(target.layoutXProperty());
        blurView.layoutYProperty().bind(target.layoutYProperty());
        blurView.fitWidthProperty().bind(target.widthProperty());
        blurView.fitHeightProperty().bind(target.heightProperty());
    }

    /**
     * 启动节流定时器
     *
     * <p>每帧检查是否需要更新（间隔超过 UPDATE_CHECK_INTERVAL_MS 且尺寸有变化），
     * 避免每帧截图造成性能问题。</p>
     */
    private void startTimer() {
        timer = new AnimationTimer() {
            @Override
            public void handle(final long now) {
                final long nowMs = now / 1_000_000L;
                if (nowMs - lastUpdateTime < UPDATE_CHECK_INTERVAL_MS) {
                    return;
                }
                lastUpdateTime = nowMs;
                checkAndUpdate();
            }
        };
        timer.start();
    }

    /**
     * 检查是否需要更新快照（尺寸变化时触发）
     */
    private void checkAndUpdate() {
        final double w = target.getWidth();
        final double h = target.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (Double.compare(w, lastWidth) == 0 && Double.compare(h, lastHeight) == 0) {
            return;
        }
        lastWidth = w;
        lastHeight = h;
        updateSnapshot();
    }

    /**
     * 截取背景源快照并应用到 blurView
     *
     * <p>截取整个背景源的快照，ImageView 会自动按 target 的位置和尺寸显示对应区域。
     * 由于 ImageView 绑定了 target 的 layoutX/Y 和 fitWidth/Height，
     * 显示的是背景源中与 target 重叠的区域（需 backgroundSource 与 target 在同一坐标系）。</p>
     */
    private void updateSnapshot() {
        if (blurView == null || backgroundSource == null) {
            return;
        }
        final double srcW = backgroundSource.getWidth();
        final double srcH = backgroundSource.getHeight();
        if (srcW <= 0 || srcH <= 0) {
            return;
        }
        try {
            final SnapshotParameters params = new SnapshotParameters();
            final Image snapshot = backgroundSource.snapshot(params, null);
            if (snapshot != null && !snapshot.isError()) {
                blurView.setImage(snapshot);
            }
        } catch (final IllegalStateException e) {
            // 快照失败时静默处理，避免影响主流程
        }
    }
}
