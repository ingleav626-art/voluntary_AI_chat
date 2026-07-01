package org.example.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javafx.application.Application;
import org.example.client.config.ServerConnectionManager;
import org.example.client.config.ServerMode;
import org.example.client.engine.LocalAiEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * JavaFX 启动器（三模式启动策略）
 *
 * <p>
 * 支持三种启动模式：
 * <ul>
 * <li>LOCAL - 本地模式：内嵌后端，H2数据库，AI隐私数据本地存储</li>
 * <li>HOTSPOT - 热点模式：连接局域网测试服务器，用于开发测试</li>
 * <li>CLOUD - 云端模式：连接公网服务器，真人实时通信</li>
 * </ul>
 * </p>
 *
 * <p>
 * 启动策略：
 * <ol>
 * <li>检查环境变量 SERVER_MODE，确定启动模式</li>
 * <li>同时检查云端服务器连接状态（异步）</li>
 * <li>默认使用本地模式，云端可用时覆盖</li>
 * <li>隐私模式开启时强制使用本地模式</li>
 * </ol>
 * </p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class Launcher {

    private static final Logger LOG = LoggerFactory.getLogger(Launcher.class);

    private static final int SERVER_PORT = 8080;

    private static final int SERVER_STARTUP_TIMEOUT_MS = 60_000;

    private static final int POLL_INTERVAL_MS = 500;

    /** 单例检测端口（用于阻止多实例启动） */
    private static final int SINGLETON_PORT = 59999;

    /** 单例检测信号服务器 Socket */
    private static ServerSocket singletonServerSocket;

    private static ConfigurableApplicationContext serverContext;

    private Launcher() {
        // 工具类禁止实例化
    }

    /**
     * 应用程序入口（三模式启动策略）
     *
     * @param args 命令行参数
     */
    public static void main(final String[] args) {
        try {
            // 单例检测：防止多实例同时运行
            if (!tryClaimSingleton()) {
                // 已有实例在运行，通知其唤醒窗口，当前实例退出
                notifyExistingInstance();
                return;
            }

            // 启动前确保日志目录存在
            ensureDataDirectory();

            // 初始化服务器连接管理器
            final ServerConnectionManager connectionManager = ServerConnectionManager.getInstance();
            final ServerMode mode = connectionManager.getCurrentMode();

            LOG.info("启动模式: {}", mode.getDescription());

            // 根据启动模式决定启动策略
            switch (mode) {
                case HOTSPOT:
                    // 热点模式：先监听 UDP 发现局域网服务器，未发现则启动本地后端做主机
                    LOG.info("热点模式：开始监听局域网服务器广播...");
                    final String discoveredHotspot = org.example.client.util.ServerDiscovery.discoverByBroadcast();
                    if (discoveredHotspot != null) {
                        // 发现其他服务器，做客机，不启动本地后端
                        LOG.info("热点模式（客机）：发现服务器 {}，将连接该服务器", discoveredHotspot);
                        connectionManager.setHotspotServerUrl(discoveredHotspot);
                    } else if (isServerModuleAvailable()) {
                        // 未发现且含 server 模块，启动本地后端做主机
                        LOG.info("热点模式（主机）：未发现其他服务器，启动本地后端做主机");
                        startEmbeddedServer();
                    } else {
                        // 未发现且无 server 模块，回退到本地 AI 引擎
                        LOG.warn("热点模式：未发现服务器且无内嵌后端，回退到本地 AI 引擎");
                        initLocalAiEngineAsync();
                    }
                    break;

                case CLOUD:
                    // 云端模式：跳过内嵌后端，连接公网服务器
                    LOG.info("云端模式：跳过内嵌后端启动，将连接云端服务器");
                    break;

                case LOCAL:
                    // 自动检测：如果 server 模块存在，说明是测试包，启动内嵌后端
                    if (isServerModuleAvailable()) {
                        LOG.info("测试包模式：检测到内嵌后端组件，启动测试服务器...");
                        startEmbeddedServer();
                    } else {
                        // 客户包模式：使用 LocalAiEngine（POJO 引擎）
                        LOG.info("本地模式：使用 LocalAiEngine（POJO 引擎，懒加载）");
                        initLocalAiEngineAsync();
                    }
                    break;

                default:
                    // 默认启动内嵌后端（兼容）
                    startEmbeddedServer();
            }

            // 异步检查云端服务器连接状态（不阻塞UI启动）
            connectionManager.checkServerAvailabilityAsync()
                    .thenAccept(serverUrl -> {
                        LOG.info("服务器连接检查完成，最终连接地址: {}", serverUrl);
                    });

            Application.launch(App.class, args);
        } catch (final Throwable e) {
            writeErrorToFile("启动失败", e);
            throw e;
        }
    }

    /**
     * 异步初始化本地 AI 引擎（非阻塞，约 200ms）
     */
    private static void initLocalAiEngineAsync() {
        final Thread initThread = new Thread(() -> {
            try {
                LOG.info("预初始化本地 AI 引擎...");
                LocalAiEngine.getInstance().initialize();
                LOG.info("本地 AI 引擎预初始化完成");
            } catch (final Exception e) {
                LOG.error("本地 AI 引擎预初始化失败，将在首次使用时重试", e);
            }
        }, "local-ai-init");
        initThread.setDaemon(true);
        initThread.start();
    }

    /**
     * 在当前 JVM 内嵌启动 Spring Boot 后端（守护线程）
     *
     * <p>
     * 根据启动模式选择不同的配置文件：
     * <ul>
     * <li>LOCAL - 使用 application-local.yml（H2数据库，精简组件）</li>
     * <li>HOTSPOT - 不启动内嵌后端</li>
     * <li>CLOUD - 不启动内嵌后端</li>
     * </ul>
     * </p>
     */
    private static void startEmbeddedServer() {
        if (isPortInUse(SERVER_PORT)) {
            LOG.info("端口 {} 已被占用，跳过后端启动", SERVER_PORT);
            return;
        }

        final Thread serverThread = new Thread(() -> {
            try {
                LOG.info("正在启动内嵌后端服务...");

                // 本地模式使用精简配置（H2数据库，仅AI组件）
                final String profile = "local";

                // 程序化设置启动属性（比 YAML 优先级更高，确保生效）
                final SpringApplication app = new SpringApplication(
                        org.example.client.server.EmbeddedServerStarter.class);
                final java.util.Properties props = new java.util.Properties();
                props.setProperty("spring.profiles.active", profile);
                props.setProperty("server.port", String.valueOf(SERVER_PORT));
                // 懒加载：不急于创建所有 Bean，用到才创建
                props.setProperty("spring.main.lazy-initialization", "true");
                // Tomcat 最小化：客户包不需要高并发
                props.setProperty("server.tomcat.threads.max", "2");
                props.setProperty("server.tomcat.threads.min-spare", "1");
                props.setProperty("server.tomcat.accept-count", "5");
                // 本地测试包模式：启用 UDP 广播服务地址，让局域网其他设备发现
                props.setProperty("server.broadcast.enabled", "true");
                // 禁用 devtools 文件系统扫描
                props.setProperty("spring.devtools.restart.enabled", "false");
                app.setDefaultProperties(props);

                serverContext = app.run();
                LOG.info("内嵌后端服务启动成功，端口: {}, 配置: {}", SERVER_PORT, profile);
            } catch (final Exception e) {
                LOG.error("内嵌后端服务启动失败", e);
                writeErrorToFile("内嵌后端服务启动失败", e);
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
     * 检测 server 模块是否在 classpath 中（判断是否为测试包）
     *
     * <p>
     * 测试包 fat JAR 包含 voluntary-ai-chat-server，客户包 thin JAR 不包含。
     * 通过检查 {@code com.voluntary.chat.server.ServerApplication}
     * 类是否可加载来判断。
     * </p>
     *
     * @return true=测试包（含 server 模块），false=客户包（无 server 模块）
     */
    private static boolean isServerModuleAvailable() {
        try {
            Class.forName("com.voluntary.chat.server.VoluntaryAiChatApplication");
            return true;
        } catch (final ClassNotFoundException e) {
            return false;
        }
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

    /**
     * 确保 H2 数据目录存在，避免 H2 启动时因目录不存在而报错
     */
    private static void ensureDataDirectory() {
        String dataDir = System.getProperty("app.data.dir");
        if (dataDir == null) {
            final String appdata = System.getenv("APPDATA");
            if (appdata != null) {
                dataDir = appdata + "/Voluntary-AI-Chat/data";
            } else {
                dataDir = "./data";
            }
        }
        try {
            final Path dir = Path.of(dataDir);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                LOG.info("已创建数据目录: {}", dir.toAbsolutePath());
            }
        } catch (final IOException e) {
            LOG.warn("创建数据目录失败: {}", dataDir, e);
        }
    }

    /**
     * 获取数据目录路径
     */
    private static Path getDataDir() {
        String dataDir = System.getProperty("app.data.dir");
        if (dataDir == null) {
            final String appdata = System.getenv("APPDATA");
            dataDir = appdata != null ? appdata + "/Voluntary-AI-Chat/data" : "./data";
        }
        return Path.of(dataDir);
    }

    /**
     * 尝试声明单例实例。返回 true 表示当前是第一个实例，false 表示已有实例在运行。
     */
    private static boolean tryClaimSingleton() {
        try {
            singletonServerSocket = new ServerSocket(SINGLETON_PORT, 1, InetAddress.getByName("127.0.0.1"));
            // 绑定成功，当前是首个实例，启动信号监听线程
            final ServerSocket ss = singletonServerSocket;
            final Thread signalThread = new Thread(() -> {
                while (!ss.isClosed()) {
                    try (Socket client = ss.accept();
                            InputStream in = client.getInputStream()) {
                        final byte[] buf = new byte[1024];
                        final int n = in.read(buf);
                        if (n > 0) {
                            final String msg = new String(buf, 0, n, StandardCharsets.UTF_8).trim();
                            LOG.info("收到其他实例信号: {}", msg);
                            if ("show".equals(msg)) {
                                // 通知 JavaFX 线程唤醒窗口
                                javafx.application.Platform.runLater(() -> {
                                    try {
                                        org.example.client.App.showExistingWindow();
                                    } catch (final Exception e) {
                                        LOG.warn("唤醒窗口失败", e);
                                    }
                                });
                            }
                        }
                    } catch (final java.io.InterruptedIOException e) {
                        break;
                    } catch (final IOException e) {
                        if (!ss.isClosed()) {
                            LOG.debug("信号监听异常", e);
                        }
                        break;
                    }
                }
            }, "singleton-signal-listener");
            signalThread.setDaemon(true);
            signalThread.start();
            LOG.info("单例检测成功，绑定端口 {}", SINGLETON_PORT);
            return true;
        } catch (final IOException e) {
            LOG.info("端口 {} 已被占用，检测到已有实例在运行", SINGLETON_PORT);
            return false;
        }
    }

    /**
     * 通知已有实例唤醒窗口，然后当前实例退出。
     */
    private static void notifyExistingInstance() {
        try (Socket s = new Socket("127.0.0.1", SINGLETON_PORT);
                OutputStream out = s.getOutputStream()) {
            out.write("show\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            LOG.info("已通知现有实例唤醒窗口");
        } catch (final IOException e) {
            LOG.warn("通知现有实例失败，可能已退出", e);
        }
        // 短暂延迟确保信号发送完成
        try {
            Thread.sleep(200);
        } catch (final InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 优雅关闭内嵌后端和本地 AI 引擎
     *
     * <p>
     * 关闭 Spring 上下文（如果有），释放端口 8080。
     * 关闭 LocalAiEngine（如果有），释放 H2 连接和线程池。
     * 可以被 App.stop() 或系统托盘"退出"菜单调用。
     * 多次调用安全（幂等）。
     * </p>
     */
    public static void shutdown() {
        if (serverContext != null && serverContext.isRunning()) {
            LOG.info("正在关闭内嵌后端服务...");
            try {
                serverContext.close();
                LOG.info("内嵌后端服务已关闭");
            } catch (final Exception e) {
                LOG.error("关闭内嵌后端服务时出错", e);
            } finally {
                serverContext = null;
            }
        } else {
            LOG.debug("内嵌后端服务未运行，无需关闭");
        }

        // 关闭本地 AI 引擎
        try {
            final LocalAiEngine engine = LocalAiEngine.getInstance();
            if (engine.isInitialized()) {
                engine.close();
                LOG.info("本地 AI 引擎已关闭");
            }
        } catch (final Exception e) {
            LOG.warn("关闭本地 AI 引擎时出错", e);
        }

        // 关闭服务器连接管理器线程池
        ServerConnectionManager.getInstance().shutdown();

        // 释放单例检测端口
        if (singletonServerSocket != null && !singletonServerSocket.isClosed()) {
            try {
                singletonServerSocket.close();
                LOG.debug("单例检测端口已释放");
            } catch (final IOException e) {
                LOG.warn("关闭单例检测端口时出错", e);
            }
        }
    }

    /**
     * 将启动错误写入文件，方便无控制台环境（如 jpackage exe）排查问题
     */
    private static void writeErrorToFile(final String context, final Throwable e) {
        try {
            final Path dir = getDataDir();
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            final Path logFile = dir.resolve("startup-error.log");
            final StringWriter sw = new StringWriter();
            final PrintWriter pw = new PrintWriter(sw);
            pw.println("=== " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " ===");
            pw.println("Context: " + context);
            pw.println("Exception: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace(pw);
            pw.println();
            Files.writeString(logFile, sw.toString(), java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
            System.err.println("[Launcher] 错误已写入: " + logFile.toAbsolutePath());
        } catch (final IOException ex) {
            System.err.println("[Launcher] 无法写入错误日志: " + ex.getMessage());
        }
    }
}
