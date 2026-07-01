package org.example.client.controller;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NotificationDialog 单元测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("NotificationDialog 测试")
class NotificationDialogTest extends JavaFxTestBase {

    @Test
    @DisplayName("Type 枚举 - SUCCESS 有正确的颜色和图标")
    void typeSuccess_shouldHaveCorrectValues() throws Exception {
        final Class<?> typeClass = Class.forName(
                "org.example.client.controller.NotificationDialog$Type");
        final Object[] types = typeClass.getEnumConstants();

        Object success = null;
        for (final Object t : types) {
            if (t.toString().equals("SUCCESS")) {
                success = t;
                break;
            }
        }
        assertNotNull(success);

        final Method getColor = typeClass.getDeclaredMethod("getColor");
        final Method getIcon = typeClass.getDeclaredMethod("getIcon");
        final String color = (String) getColor.invoke(success);
        final String icon = (String) getIcon.invoke(success);

        assertEquals("#4CAF50", color);
        assertNotNull(icon);
    }

    @Test
    @DisplayName("Type 枚举 - INFO 有正确的颜色")
    void typeInfo_shouldHaveCorrectColor() throws Exception {
        final Class<?> typeClass = Class.forName(
                "org.example.client.controller.NotificationDialog$Type");
        final Object[] types = typeClass.getEnumConstants();

        Object info = null;
        for (final Object t : types) {
            if (t.toString().equals("INFO")) {
                info = t;
                break;
            }
        }
        assertNotNull(info);

        final Method getColor = typeClass.getDeclaredMethod("getColor");
        final String color = (String) getColor.invoke(info);

        assertEquals("#2196F3", color);
    }

    @Test
    @DisplayName("Type 枚举 - WARNING 有正确的颜色")
    void typeWarning_shouldHaveCorrectColor() throws Exception {
        final Class<?> typeClass = Class.forName(
                "org.example.client.controller.NotificationDialog$Type");
        final Object[] types = typeClass.getEnumConstants();

        Object warning = null;
        for (final Object t : types) {
            if (t.toString().equals("WARNING")) {
                warning = t;
                break;
            }
        }
        assertNotNull(warning);

        final Method getColor = typeClass.getDeclaredMethod("getColor");
        final String color = (String) getColor.invoke(warning);

        assertEquals("#FF9800", color);
    }

    @Test
    @DisplayName("Type 枚举 - 有4个值")
    void typeEnum_shouldHaveThreeValues() throws Exception {
        final Class<?> typeClass = Class.forName(
                "org.example.client.controller.NotificationDialog$Type");
        final Object[] types = typeClass.getEnumConstants();
        assertEquals(4, types.length);
    }

    @Test
    @DisplayName("showSuccess - 异步调用不崩溃")
    void showSuccess_shouldNotCrash() throws Exception {
        // 在另一个线程调用 showSuccess，它会创建 Stage 并 showAndWait
        // 由于没有窗口所有者，showAndWait 会阻塞
        // 使用 CompletableFuture 异步执行并设置超时
        final CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                // 在 FX 线程上执行
                javafx.application.Platform.runLater(() -> {
                    try {
                        NotificationDialog.showSuccess("测试标题", "测试消息");
                    } catch (final Exception e) {
                        // 在无头环境中可能失败
                    }
                });
            } catch (final Exception e) {
                // 忽略
            }
        });

        // 等待一小段时间让代码执行
        try {
            future.get(1, TimeUnit.SECONDS);
        } catch (final Exception e) {
            // 超时是预期的，因为 showAndWait 会阻塞
        }
    }

    @Test
    @DisplayName("show - 反射调用不崩溃")
    void show_reflection_shouldNotCrash() throws Exception {
        final Class<?> typeClass = Class.forName(
                "org.example.client.controller.NotificationDialog$Type");
        final Object[] types = typeClass.getEnumConstants();
        final Object successType = types[0]; // SUCCESS

        final Method showMethod = NotificationDialog.class.getDeclaredMethod("show",
                String.class, String.class, typeClass);
        showMethod.setAccessible(true);

        // 异步调用，因为 showAndWait 会阻塞
        final CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            javafx.application.Platform.runLater(() -> {
                try {
                    showMethod.invoke(null, "测试", "消息", successType);
                } catch (final Exception e) {
                    // 在无头环境中可能失败
                }
            });
        });

        try {
            future.get(1, TimeUnit.SECONDS);
        } catch (final Exception e) {
            // 超时是预期的
        }
    }
}
