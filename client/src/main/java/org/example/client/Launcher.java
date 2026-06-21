package org.example.client;

import java.io.IOException;
import java.net.Socket;

import javafx.application.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * JavaFX 启动器
 *
 * <p>JavaFX 17 要求主类不能直接继承 {@link Application}，
 * 否则启动时会报 "缺少 JavaFX 运行时组件"。
 * 此启动器作为入口，内嵌启动后端服务，再启动前端 GUI。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class Launcher {

    private static final Logger LOG = LoggerFactory.getLogger(Launcher.class);

    private static final int SERVER_PORT = 8080;

    private static final int SERVER_STARTUP_TIMEOUT_MS = 60_000;

    private static final int POLL_INTERVAL_MS = 500;

    private static ConfigurableApplicationContext serverContext;

    private Launcher() {
        // 工具类禁止实例化
    }

    /**
     * 应用程序入口
     *
     * @param args 命令行参数
     */
    public static void main(final String[] args) {
        // 热点模式下跳过内嵌后端启动，连接远程服务器
        final String skipServer = System.getenv("SKIP_EMBEDDED_SERVER");
        if ("true".equalsIgnoreCase(skipServer)) {
            LOG.info("SKIP_EMBEDDED_SERVER=true，跳过内嵌后端启动，将连接远程服务器");
        } else {
            startEmbeddedServer();
        }
        Application.launch(App.class, args);
    }

    /**
     * 在当前 JVM 内嵌启动 Spring Boot 后端（守护线程）
     */
    private static void startEmbeddedServer() {
        if (isPortInUse(SERVER_PORT)) {
            LOG.info("端口 {} 已被占用，跳过后端启动", SERVER_PORT);
            return;
        }

        final Thread serverThread = new Thread(() -> {
            try {
                LOG.info("正在启动内嵌后端服务...");
                serverContext = SpringApplication.run(
                        org.example.client.server.EmbeddedServerStarter.class,
                        "--spring.profiles.active=local",
                        "--server.port=" + SERVER_PORT
                );
                LOG.info("内嵌后端服务启动成功，端口: {}", SERVER_PORT);
            } catch (final Exception e) {
                LOG.error("内嵌后端服务启动失败", e);
            }
        }, "embedded-server-thread");
        serverThread.setDaemon(true);
        serverThread.start();

        waitForServer();
    }

    /**
     * 轮询等待后端端口就绪
     */
    private static void waitForServer() {
        final long deadline = System.currentTimeMillis() + SERVER_STARTUP_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (isPortInUse(SERVER_PORT)) {
                LOG.info("后端服务已就绪，端口 {}", SERVER_PORT);
                return;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("等待后端启动被中断");
                return;
            }
        }
        LOG.warn("等待后端服务超时（{}ms），前端将继续启动", SERVER_STARTUP_TIMEOUT_MS);
    }

    /**
     * 检测指定端口是否已被占用
     */
    private static boolean isPortInUse(final int port) {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            return true;
        } catch (final IOException e) {
            return false;
        }
    }
}
