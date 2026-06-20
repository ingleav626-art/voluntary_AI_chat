package org.example.client;

import javafx.application.Application;

/**
 * JavaFX 启动器
 *
 * <p>JavaFX 17 要求主类不能直接继承 {@link Application}，
 * 否则启动时会报 "缺少 JavaFX 运行时组件"。
 * 此启动器作为入口，调用 {@link App} 的 launch 方法。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class Launcher {

    private Launcher() {
        // 工具类禁止实例化
    }

    /**
     * 应用程序入口
     *
     * @param args 命令行参数
     */
    public static void main(final String[] args) {
        Application.launch(App.class, args);
    }
}

