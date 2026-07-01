package org.example.client.util;

import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import org.example.client.config.ClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 图片工具类
 *
 * <p>提供图片 URL 解析、头像加载等通用方法。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class ImageUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ImageUtils.class);

    private ImageUtils() {
        // 工具类禁止实例化
    }

    /**
     * 将相对路径 URL 解析为完整的可访问 URL
     *
     * <p>服务端返回的 avatar 等图片 URL 可能是相对路径（如 /avatars/xxx.jpg），
     * 需要拼接服务器地址才能在 JavaFX Image 中加载。</p>
     *
     * @param relativeUrl 相对路径 URL 或完整 URL
     * @return 完整的可访问 URL
     */
    public static String resolveImageUrl(final String relativeUrl) {
        if (relativeUrl == null || relativeUrl.isEmpty()) {
            return relativeUrl;
        }
        // 已经是完整 URL，直接返回
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            return relativeUrl;
        }
        // 文件协议 URL（本地文件），直接返回
        if (relativeUrl.startsWith("file:")) {
            return relativeUrl;
        }
        // 相对路径：拼接服务器地址（去掉 /api 后缀）
        final String baseUrl = ClientConfig.getInstance().getBaseUrl();
        // baseUrl 格式: http://192.168.104.113:8080/api
        final String serverRoot = baseUrl.endsWith("/api")
                ? baseUrl.substring(0, baseUrl.length() - "/api".length())
                : baseUrl;
        return serverRoot + relativeUrl;
    }

    /**
     * 加载头像图片到 Circle 组件
     *
     * <p>将头像 URL 加载为图片，并设置为 Circle 的 ImagePattern 填充。
     * 图片加载失败时保持 Circle 原有的默认填充。</p>
     *
     * @param avatarUrl  头像 URL（相对路径或绝对路径）
     * @param avatarCircle 头像 Circle 组件
     * @param radius      Circle 半径（用于 ImagePattern 缩放）
     */
    public static void loadAvatarToCircle(final String avatarUrl, final Circle avatarCircle, final double radius) {
        if (avatarUrl == null || avatarUrl.isEmpty() || avatarCircle == null) {
            return;
        }
        try {
            final String fullUrl = resolveImageUrl(avatarUrl);
            final Image image = new Image(fullUrl, true);
            image.errorProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    LOG.warn("头像图片加载失败: {}", fullUrl);
                }
            });
            // 图片加载成功后设置为 Circle 的填充
            image.progressProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.doubleValue() >= 1.0 && !image.isError()) {
                    avatarCircle.setFill(new ImagePattern(image));
                }
            });
            // 如果图片已经加载完成（缓存）
            if (!image.isBackgroundLoading() || image.getProgress() >= 1.0) {
                if (!image.isError()) {
                    avatarCircle.setFill(new ImagePattern(image));
                }
            }
        } catch (final Exception e) {
            LOG.warn("头像URL无效: {}", avatarUrl, e);
        }
    }

    /**
     * 加载头像图片到 StackPane（替换 Circle+Label 为 ImageView）
     *
     * <p>将头像 URL 加载为图片，成功时用 ImageView 替换默认头像，
     * 失败时保持 Circle+Label 默认头像。</p>
     *
     * @param avatarUrl  头像 URL（相对路径或绝对路径）
     * @param avatarPane 包含 Circle 和 Label 的 StackPane
     * @param size       头像尺寸（直径）
     */
    public static void loadAvatarToPane(final String avatarUrl,
                                         final StackPane avatarPane, final double size) {
        if (avatarUrl == null || avatarUrl.isEmpty() || avatarPane == null) {
            return;
        }
        try {
            final String fullUrl = resolveImageUrl(avatarUrl);
            final Image image = new Image(fullUrl, true);
            final javafx.scene.image.ImageView avatarImage = new javafx.scene.image.ImageView(image);
            avatarImage.setFitWidth(size);
            avatarImage.setFitHeight(size);
            // 圆形裁剪
            final Circle clip = new Circle(size / 2);
            avatarImage.setClip(clip);
            // 图片加载失败时保持默认头像
            image.errorProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    LOG.warn("头像图片加载失败: {}", fullUrl);
                }
            });
            // 图片加载成功后替换默认头像
            image.progressProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.doubleValue() >= 1.0 && !image.isError()) {
                    avatarPane.getChildren().setAll(avatarImage);
                }
            });
            // 如果图片已经加载完成（缓存）
            if (!image.isBackgroundLoading() || image.getProgress() >= 1.0) {
                if (!image.isError()) {
                    avatarPane.getChildren().setAll(avatarImage);
                }
            }
        } catch (final Exception e) {
            LOG.warn("头像URL无效: {}", avatarUrl, e);
        }
    }
}
