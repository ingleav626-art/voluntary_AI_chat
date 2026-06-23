package org.example.client.controller;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javafx.application.Platform;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ImagePreviewDialog 单元测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("ImagePreviewDialog 测试")
class ImagePreviewDialogTest extends JavaFxTestBase {

    @TempDir
    Path tempDir;

    private File testImageFile;

    @BeforeEach
    void setUp() throws Exception {
        testImageFile = createTestImageFile();
    }

    @Test
    @DisplayName("构造 - 有效图片文件不抛异常")
    void constructor_validFile_shouldNotThrow() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final Throwable[] error = {null};
        Platform.runLater(() -> {
            try {
                new ImagePreviewDialog(testImageFile);
            } catch (final Exception e) {
                error[0] = e;
            }
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(error[0]);
    }

    @Test
    @DisplayName("构造 - 不存在的文件不抛异常（加载失败分支）")
    void constructor_nonExistentFile_shouldNotThrow() throws Exception {
        final File imageFile = new File(tempDir.toFile(), "nonexistent.png");
        final CountDownLatch latch = new CountDownLatch(1);
        final Throwable[] error = {null};
        Platform.runLater(() -> {
            try {
                new ImagePreviewDialog(imageFile);
            } catch (final Exception e) {
                error[0] = e;
            }
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(error[0]);
    }

    @Test
    @DisplayName("getImagePath - 返回正确的路径")
    void getImagePath_shouldReturnCorrectPath() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final Path[] result = {null};
        Platform.runLater(() -> {
            final ImagePreviewDialog dialog = new ImagePreviewDialog(testImageFile);
            result[0] = dialog.getImagePath();
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(testImageFile.toPath(), result[0]);
    }

    @Test
    @DisplayName("getStage - 返回非 null Stage")
    void getStage_shouldReturnNonNull() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] success = {false};
        Platform.runLater(() -> {
            final ImagePreviewDialog dialog = new ImagePreviewDialog(testImageFile);
            success[0] = dialog.getStage() != null;
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(success[0]);
    }

    @Test
    @DisplayName("confirm - 设置 confirmed=true")
    void confirm_shouldSetConfirmedTrue() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] confirmed = {false};
        Platform.runLater(() -> {
            try {
                final ImagePreviewDialog dialog = new ImagePreviewDialog(testImageFile);
                invokeNoArgMethod(dialog, "confirm");
                confirmed[0] = (boolean) getField(dialog, "confirmed");
            } catch (final Exception e) {
                // ignore
            }
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(confirmed[0]);
    }

    @Test
    @DisplayName("cancel - 设置 confirmed=false")
    void cancel_shouldSetConfirmedFalse() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] confirmed = {true};
        Platform.runLater(() -> {
            try {
                final ImagePreviewDialog dialog = new ImagePreviewDialog(testImageFile);
                invokeNoArgMethod(dialog, "cancel");
                confirmed[0] = (boolean) getField(dialog, "confirmed");
            } catch (final Exception e) {
                // ignore
            }
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertFalse(confirmed[0]);
    }

    @Test
    @DisplayName("createButtons - 不抛异常")
    void createButtons_shouldNotThrow() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final Throwable[] error = {null};
        Platform.runLater(() -> {
            try {
                final ImagePreviewDialog dialog = new ImagePreviewDialog(testImageFile);
                invokeNoArgMethod(dialog, "createButtons");
            } catch (final Exception e) {
                error[0] = e;
            }
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(error[0]);
    }

    @Test
    @DisplayName("createFileInfo - 不抛异常")
    void createFileInfo_shouldNotThrow() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final Throwable[] error = {null};
        Platform.runLater(() -> {
            try {
                final ImagePreviewDialog dialog = new ImagePreviewDialog(testImageFile);
                invokeNoArgMethod(dialog, "createFileInfo");
            } catch (final Exception e) {
                error[0] = e;
            }
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(error[0]);
    }

    private File createTestImageFile() throws IOException {
        final File file = tempDir.resolve("test.png").toFile();
        // 创建一个最小的 PNG 文件（1x1 像素红色 PNG）
        final byte[] minimalPng = {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53,
                (byte) 0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
                0x54, 0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xCF,
                (byte) 0xC0, 0x00, 0x00, 0x00, 0x02, 0x00, 0x01, (byte) 0xE2,
                0x21, (byte) 0xBC, 0x33, 0x00, 0x00, 0x00, 0x00, 0x49,
                0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };
        Files.write(file.toPath(), minimalPng);
        return file;
    }

    private static void invokeNoArgMethod(final Object obj, final String name) throws Exception {
        final Method method = obj.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(obj);
    }

    private static Object getField(final Object obj, final String name) throws Exception {
        final Field field = obj.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(obj);
    }
}
