package org.example.client.controller;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;

import javafx.application.Platform;

/**
 * JavaFX 测试基类
 *
 * <p>在 @BeforeAll 中初始化 JavaFX 工具包，使测试可以创建 JavaFX 控件和属性。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
abstract class JavaFxTestBase {

    private static boolean toolkitInitialized = false;

    @BeforeAll
    static void initJavaFx() throws InterruptedException {
        if (!toolkitInitialized) {
            final CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            latch.await(5, TimeUnit.SECONDS);
            Platform.setImplicitExit(false);
            toolkitInitialized = true;
        }
    }
}
