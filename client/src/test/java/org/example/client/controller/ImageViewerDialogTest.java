package org.example.client.controller;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.stage.Stage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ImageViewerDialog 单元测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("ImageViewerDialog 测试")
class ImageViewerDialogTest extends JavaFxTestBase {

    private ImageViewerDialog dialog;

    @BeforeEach
    void setUp() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            dialog = new ImageViewerDialog("http://example.com/test.png");
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("构造 - 不抛异常")
    void constructor_shouldNotThrow() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            assertDoesNotThrow(() -> new ImageViewerDialog("http://example.com/test.png"));
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("构造 - null URL 不抛异常")
    void constructor_nullUrl_shouldNotThrow() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            assertDoesNotThrow(() -> new ImageViewerDialog(null));
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("getStage - 返回非 null Stage")
    void getStage_shouldReturnNonNull() {
        final Stage stage = dialog.getStage();
        assertNotNull(stage);
    }

    @Test
    @DisplayName("zoomIn - 不抛异常")
    void zoomIn_shouldNotThrow() throws Exception {
        assertDoesNotThrow(() -> invokeNoArgMethod(dialog, "zoomIn"));
    }

    @Test
    @DisplayName("zoomOut - 不抛异常")
    void zoomOut_shouldNotThrow() throws Exception {
        assertDoesNotThrow(() -> invokeNoArgMethod(dialog, "zoomOut"));
    }

    @Test
    @DisplayName("resetZoom - 不抛异常")
    void resetZoom_shouldNotThrow() throws Exception {
        assertDoesNotThrow(() -> invokeNoArgMethod(dialog, "resetZoom"));
    }

    @Test
    @DisplayName("zoomIn - 缩放比例增加")
    void zoomIn_shouldIncreaseScale() throws Exception {
        final DoubleProperty currentScale = (DoubleProperty) getField(dialog, "currentScale");
        final double initialScale = currentScale.get();

        invokeNoArgMethod(dialog, "zoomIn");

        assertTrue(currentScale.get() > initialScale);
    }

    @Test
    @DisplayName("zoomOut - 缩放比例减少")
    void zoomOut_shouldDecreaseScale() throws Exception {
        invokeNoArgMethod(dialog, "zoomIn");
        final DoubleProperty currentScale = (DoubleProperty) getField(dialog, "currentScale");
        final double afterZoomIn = currentScale.get();

        invokeNoArgMethod(dialog, "zoomOut");

        assertTrue(currentScale.get() < afterZoomIn);
    }

    @Test
    @DisplayName("resetZoom - 重置缩放为1.0")
    void resetZoom_shouldResetTo1() throws Exception {
        final DoubleProperty currentScale = (DoubleProperty) getField(dialog, "currentScale");

        invokeNoArgMethod(dialog, "zoomIn");
        invokeNoArgMethod(dialog, "zoomIn");
        invokeNoArgMethod(dialog, "resetZoom");

        assertEquals(1.0, currentScale.get(), 0.001);
    }

    @Test
    @DisplayName("zoomIn - 达到最大缩放不再增加")
    void zoomIn_maxScale_shouldNotExceed() throws Exception {
        final DoubleProperty currentScale = (DoubleProperty) getField(dialog, "currentScale");
        currentScale.set(5.0); // MAX_SCALE

        invokeNoArgMethod(dialog, "zoomIn");

        assertEquals(5.0, currentScale.get(), 0.001);
    }

    @Test
    @DisplayName("zoomOut - 达到最小缩放不再减少")
    void zoomOut_minScale_shouldNotGoBelow() throws Exception {
        final DoubleProperty currentScale = (DoubleProperty) getField(dialog, "currentScale");
        currentScale.set(0.1); // MIN_SCALE

        invokeNoArgMethod(dialog, "zoomOut");

        assertEquals(0.1, currentScale.get(), 0.001);
    }

    @Test
    @DisplayName("applyZoom - 不抛异常")
    void applyZoom_shouldNotThrow() throws Exception {
        assertDoesNotThrow(() -> invokeNoArgMethod(dialog, "applyZoom"));
    }

    @Test
    @DisplayName("createToolbar - 不抛异常")
    void createToolbar_shouldNotThrow() throws Exception {
        assertDoesNotThrow(() -> invokeNoArgMethod(dialog, "createToolbar"));
    }

    @Test
    @DisplayName("createInfoBar - 不抛异常")
    void createInfoBar_shouldNotThrow() throws Exception {
        assertDoesNotThrow(() -> invokeNoArgMethod(dialog, "createInfoBar"));
    }

    @Test
    @DisplayName("showError - 不抛异常")
    void showError_shouldNotThrow() throws Exception {
        assertDoesNotThrow(() -> invokeMethod(dialog, "showError", String.class, "测试错误"));
    }

    @Test
    @DisplayName("close - 不抛异常")
    void close_shouldNotThrow() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final Throwable[] error = {null};
        Platform.runLater(() -> {
            try {
                dialog.close();
            } catch (final Exception e) {
                error[0] = e;
            }
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(error[0]);
    }

    // ============ 辅助方法 ============

    private static void invokeNoArgMethod(final Object obj, final String name) throws Exception {
        final Method method = obj.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(obj);
    }

    @SuppressWarnings("unchecked")
    private static <T> void invokeMethod(final Object obj, final String name,
            final Class<T> paramType, final T paramValue) throws Exception {
        final Method method = obj.getClass().getDeclaredMethod(name, paramType);
        method.setAccessible(true);
        method.invoke(obj, paramValue);
    }

    private static Object getField(final Object obj, final String name) throws Exception {
        final Field field = obj.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(obj);
    }
}
