package org.example.client;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.client.config.ClientConfig;
import org.example.client.config.ServerConnectionManager;
import org.example.client.controller.MainController;
import org.example.client.model.ConversationInfo;
import org.example.client.model.LoginResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JavaFX 客户端主应用（三模式启动策略）
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
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public class App extends Application {

    private static final Logger LOG = LoggerFactory.getLogger(App.class);

    /** 窗口宽度 */
    private static final int WINDOW_WIDTH = 400;

    /** 登录窗口高度 */
    private static final int LOGIN_WINDOW_HEIGHT = 520;

    /** 注册窗口高度 */
    private static final int REGISTER_WINDOW_HEIGHT = 620;

    /** 忘记密码窗口高度 */
    private static final int FORGOT_PASSWORD_WINDOW_HEIGHT = 580;

    /** 主界面窗口宽度 */
    private static final int MAIN_WINDOW_WIDTH = 1000;

    /** 主界面窗口高度 */
    private static final int MAIN_WINDOW_HEIGHT = 700;

    /** 默认样式文件 */
    private static final String DEFAULT_CSS = "/css/default.css";

    /** 登录界面 FXML */
    private static final String LOGIN_FXML = "/fxml/login.fxml";

    /** 注册界面 FXML */
    private static final String REGISTER_FXML = "/fxml/register.fxml";

    /** 忘记密码界面 FXML */
    private static final String FORGOT_PASSWORD_FXML = "/fxml/forgot_password.fxml";

    /** 主界面 FXML */
    private static final String MAIN_FXML = "/fxml/main.fxml";

    /** 好友面板 FXML */
    private static final String FRIEND_FXML = "/fxml/friend_panel.fxml";

    /** 群组面板 FXML */
    private static final String GROUP_FXML = "/fxml/group_panel.fxml";

    /** AI 助手面板 FXML */
    private static final String AI_FXML = "/fxml/ai_panel.fxml";

    /** 主舞台引用，用于页面切换 */
    private static Stage primaryStage;

    /** 当前登录响应，用于界面间切换保持登录态 */
    private static LoginResponse currentLoginResponse;

    /** 待跳转的 AI 会话（从 AI 面板跳转到聊天时使用） */
    private static ConversationInfo pendingAiConversation;

    /** 系统托盘图标 */
    private static TrayIcon trayIcon;

    /** 是否已启用系统托盘 */
    private static boolean trayEnabled;

    /** 托盘图标资源路径 */
    private static final String TRAY_ICON_PATH = "/images/icon.png";

    @Override
    public void init() {
        // 检查环境变量 CLIENT_CONFIG，支持热点测试环境
        final String configEnv = System.getenv("CLIENT_CONFIG");
        if (configEnv != null && !configEnv.isEmpty()) {
            ClientConfig.getInstance().setConfigFile(configEnv);
            LOG.info("使用自定义配置文件: {}", configEnv);
            ClientConfig.getInstance().load();
        } else {
            // 使用三模式启动策略
            LOG.info("使用三模式启动策略...");
            final ServerConnectionManager connectionManager = ServerConnectionManager.getInstance();

            // 异步检查服务器连接状态，等待结果（最多5秒）
            try {
                final String finalServerUrl = connectionManager.checkServerAvailabilityAsync()
                        .get(5, TimeUnit.SECONDS);

                // 设置最终的服务器地址
                ClientConfig.getInstance().setBaseUrl(finalServerUrl);
                LOG.info("服务器连接检查完成，使用服务器: {}", finalServerUrl);
            } catch (final Exception e) {
                LOG.warn("服务器连接检查超时或失败，使用默认本地服务器", e);
                ClientConfig.getInstance()
                        .setBaseUrl(ServerConnectionManager.getInstance().getCurrentMode().getDefaultBaseUrl());
            }

            ClientConfig.getInstance().load();
        }

        LOG.info("客户端配置加载成功: baseUrl={}, mode={}",
                ClientConfig.getInstance().getBaseUrl(),
                ServerConnectionManager.getInstance().getCurrentMode().getDescription());
    }

    @Override
    public void start(final Stage stage) throws Exception {
        primaryStage = stage;

        // 阻止 JavaFX 在窗口关闭时自动退出（由系统托盘控制生命周期）
        Platform.setImplicitExit(false);

        // 窗口关闭时最小化到系统托盘，不退出
        primaryStage.setOnCloseRequest(event -> {
            event.consume();
            hideToTray();
        });

        // 初始化系统托盘（仅支持托盘的桌面环境）
        initSystemTray();

        switchToLogin();
        primaryStage.setResizable(false);
        primaryStage.show();
        LOG.info("客户端启动成功，已加载登录界面");
    }

    /**
     * 切换到登录界面
     */
    public static void switchToLogin() {
        try {
            final FXMLLoader loader = new FXMLLoader(App.class.getResource(LOGIN_FXML));
            final VBox root = loader.load();
            final Scene scene = createScene(root, WINDOW_WIDTH, LOGIN_WINDOW_HEIGHT);
            primaryStage.setTitle("AI 聊天 - 登录");
            primaryStage.setScene(scene);
            primaryStage.setWidth(WINDOW_WIDTH);
            primaryStage.setHeight(LOGIN_WINDOW_HEIGHT);
            primaryStage.setResizable(false);
            LOG.info("已切换到登录界面");
        } catch (final IOException e) {
            LOG.error("加载登录界面失败", e);
        }
    }

    /**
     * 切换到注册界面
     */
    public static void switchToRegister() {
        try {
            final FXMLLoader loader = new FXMLLoader(App.class.getResource(REGISTER_FXML));
            final VBox root = loader.load();
            final Scene scene = createScene(root, WINDOW_WIDTH, REGISTER_WINDOW_HEIGHT);
            primaryStage.setTitle("AI 聊天 - 注册");
            primaryStage.setScene(scene);
            primaryStage.setWidth(WINDOW_WIDTH);
            primaryStage.setHeight(REGISTER_WINDOW_HEIGHT);
            primaryStage.setResizable(false);
            LOG.info("已切换到注册界面");
        } catch (final IOException e) {
            LOG.error("加载注册界面失败", e);
        }
    }

    /**
     * 切换到忘记密码界面
     */
    public static void switchToForgotPassword() {
        try {
            final FXMLLoader loader = new FXMLLoader(App.class.getResource(FORGOT_PASSWORD_FXML));
            final VBox root = loader.load();
            final Scene scene = createScene(root, WINDOW_WIDTH, FORGOT_PASSWORD_WINDOW_HEIGHT);
            primaryStage.setTitle("AI 聊天 - 重置密码");
            primaryStage.setScene(scene);
            primaryStage.setWidth(WINDOW_WIDTH);
            primaryStage.setHeight(FORGOT_PASSWORD_WINDOW_HEIGHT);
            primaryStage.setResizable(false);
            LOG.info("已切换到忘记密码界面");
        } catch (final IOException e) {
            LOG.error("加载忘记密码界面失败", e);
        }
    }

    /**
     * 切换到登录界面并预填手机号
     *
     * @param phone 手机号
     */
    public static void switchToLoginWithPhone(final String phone) {
        switchToLogin();
        // 登录界面加载后设置手机号
        // 通过事件机制在下一帧设置，确保控制器已初始化
        javafx.application.Platform.runLater(() -> {
            final javafx.scene.Scene scene = primaryStage.getScene();
            if (scene != null) {
                final javafx.scene.control.TextField phoneField = (javafx.scene.control.TextField) scene
                        .lookup("#phoneField");
                if (phoneField != null) {
                    phoneField.setText(phone);
                }
            }
        });
    }

    /**
     * 切换到主聊天界面
     *
     * @param loginResponse 登录响应，包含用户信息和 Token
     */
    public static void switchToMain(final LoginResponse loginResponse) {
        currentLoginResponse = loginResponse;
        try {
            final FXMLLoader loader = new FXMLLoader(App.class.getResource(MAIN_FXML));
            final Parent root = loader.load();

            // 传递登录数据给主界面控制器
            final MainController controller = loader.getController();
            if (controller != null) {
                controller.initData(loginResponse);
                // 如果有待跳转的 AI 会话，自动选中
                if (pendingAiConversation != null) {
                    final ConversationInfo conv = pendingAiConversation;
                    pendingAiConversation = null;
                    javafx.application.Platform.runLater(() -> controller.selectAiConversation(conv));
                }
            }

            final Scene scene = createScene(root, MAIN_WINDOW_WIDTH, MAIN_WINDOW_HEIGHT);
            primaryStage.setTitle("AI 聊天 - " + (loginResponse.getUser() != null
                    ? loginResponse.getUser().getUsername()
                    : ""));
            primaryStage.setScene(scene);
            primaryStage.setWidth(MAIN_WINDOW_WIDTH);
            primaryStage.setHeight(MAIN_WINDOW_HEIGHT);
            primaryStage.setResizable(true);
            primaryStage.centerOnScreen();
            LOG.info("已切换到主聊天界面");
        } catch (final Exception e) {
            LOG.error("加载主聊天界面失败", e);
        }
    }

    /**
     * 切换到好友面板
     */
    public static void switchToFriend() {
        try {
            final FXMLLoader loader = new FXMLLoader(App.class.getResource(FRIEND_FXML));
            final Parent root = loader.load();

            final Scene scene = createScene(root, MAIN_WINDOW_WIDTH, MAIN_WINDOW_HEIGHT);
            primaryStage.setTitle("AI 聊天 - 好友管理");
            primaryStage.setScene(scene);
            primaryStage.setWidth(MAIN_WINDOW_WIDTH);
            primaryStage.setHeight(MAIN_WINDOW_HEIGHT);
            primaryStage.setResizable(true);
            primaryStage.centerOnScreen();
            LOG.info("已切换到好友面板");
        } catch (final Exception e) {
            LOG.error("加载好友面板失败", e);
        }
    }

    /**
     * 从好友面板返回主聊天界面
     */
    public static void switchToMainFromFriend() {
        if (currentLoginResponse != null) {
            switchToMain(currentLoginResponse);
        } else {
            switchToLogin();
        }
    }

    /**
     * 切换到群组面板
     */
    public static void switchToGroup() {
        try {
            final FXMLLoader loader = new FXMLLoader(App.class.getResource(GROUP_FXML));
            final Parent root = loader.load();

            final Scene scene = createScene(root, MAIN_WINDOW_WIDTH, MAIN_WINDOW_HEIGHT);
            primaryStage.setTitle("AI 聊天 - 群组管理");
            primaryStage.setScene(scene);
            primaryStage.setWidth(MAIN_WINDOW_WIDTH);
            primaryStage.setHeight(MAIN_WINDOW_HEIGHT);
            primaryStage.setResizable(true);
            primaryStage.centerOnScreen();
            LOG.info("已切换到群组面板");
        } catch (final Exception e) {
            LOG.error("加载群组面板失败", e);
        }
    }

    /**
     * 切换到群组面板并选中指定群组
     *
     * @param groupId 群组ID
     */
    public static void switchToGroupPanel(final Long groupId) {
        try {
            final FXMLLoader loader = new FXMLLoader(App.class.getResource(GROUP_FXML));
            final Parent root = loader.load();

            // 获取控制器并设置选中的群组
            final org.example.client.controller.GroupController controller = loader.getController();
            if (controller != null && groupId != null) {
                javafx.application.Platform.runLater(() -> {
                    controller.selectGroupById(groupId);
                });
            }

            final Scene scene = createScene(root, MAIN_WINDOW_WIDTH, MAIN_WINDOW_HEIGHT);
            primaryStage.setTitle("AI 聊天 - 群组管理");
            primaryStage.setScene(scene);
            primaryStage.setWidth(MAIN_WINDOW_WIDTH);
            primaryStage.setHeight(MAIN_WINDOW_HEIGHT);
            primaryStage.setResizable(true);
            primaryStage.centerOnScreen();
            LOG.info("已切换到群组面板，群组ID: {}", groupId);
        } catch (final Exception e) {
            LOG.error("加载群组面板失败", e);
        }
    }

    /**
     * 从群组面板返回主聊天界面
     */
    public static void switchToMainFromGroup() {
        if (currentLoginResponse != null) {
            switchToMain(currentLoginResponse);
        } else {
            switchToLogin();
        }
    }

    /**
     * 切换到 AI 助手面板
     */
    public static void switchToAi() {
        try {
            final FXMLLoader loader = new FXMLLoader(App.class.getResource(AI_FXML));
            final Parent root = loader.load();

            final Scene scene = createScene(root, MAIN_WINDOW_WIDTH, MAIN_WINDOW_HEIGHT);
            primaryStage.setTitle("AI 聊天 - AI 助手");
            primaryStage.setScene(scene);
            primaryStage.setWidth(MAIN_WINDOW_WIDTH);
            primaryStage.setHeight(MAIN_WINDOW_HEIGHT);
            primaryStage.setResizable(true);
            primaryStage.centerOnScreen();
            LOG.info("已切换到AI助手面板");
        } catch (final Exception e) {
            LOG.error("加载AI助手面板失败", e);
        }
    }

    /**
     * 从 AI 助手面板跳转到与指定 AI 的聊天界面
     *
     * @param aiId   AI 角色ID
     * @param aiName AI 名称
     * @param avatar AI 头像URL
     */
    public static void switchToMainWithAiConversation(final Long aiId, final String aiName, final String avatar) {
        if (currentLoginResponse == null) {
            LOG.warn("未登录，无法跳转到AI聊天");
            switchToLogin();
            return;
        }
        final ConversationInfo conv = new ConversationInfo();
        conv.setSessionId("a_" + aiId);
        conv.setTargetId(aiId);
        conv.setTargetType("AI");
        conv.setTargetName(aiName != null ? aiName : "AI助手");
        conv.setTargetAvatar(avatar);
        pendingAiConversation = conv;
        switchToMain(currentLoginResponse);
    }

    /**
     * 从 AI 助手面板返回主聊天界面（不选中特定会话）
     */
    public static void switchToMainFromAi() {
        if (currentLoginResponse != null) {
            switchToMain(currentLoginResponse);
        } else {
            switchToLogin();
        }
    }

    /**
     * 被其他实例唤醒时调用（单例检测信号），唤出窗口到前台。
     */
    public static void showExistingWindow() {
        Platform.runLater(() -> {
            if (primaryStage != null) {
                primaryStage.show();
                primaryStage.toFront();
                LOG.info("收到其他实例信号，已唤醒窗口");
            }
        });
    }

    /**
     * 创建场景并应用样式
     *
     * @param root   根节点
     * @param width  宽度
     * @param height 高度
     * @return 场景
     */
    private static Scene createScene(final Parent root, final int width, final int height) {
        final Scene scene = new Scene(root, width, height);
        final java.net.URL cssUrl = App.class.getResource(DEFAULT_CSS);
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
        return scene;
    }

    @Override
    public void stop() {
        LOG.info("客户端关闭，开始清理资源...");
        // 移除托盘图标
        removeTrayIcon();
        // 优雅关闭内嵌后端（释放端口 8080）
        Launcher.shutdown();
        LOG.info("客户端已完全关闭");
    }

    // ==================== 系统托盘 ====================

    /**
     * 初始化系统托盘
     *
     * <p>
     * 设置托盘图标 + AWT PopupMenu 右键菜单（自动定位到图标旁）。
     * 左键单击唤出窗口。
     * </p>
     */
    private static void initSystemTray() {
        if (!SystemTray.isSupported()) {
            LOG.info("当前系统不支持系统托盘，窗口关闭将直接退出");
            Platform.setImplicitExit(true);
            return;
        }

        try {
            // 加载托盘图标
            final Image trayImage = loadTrayIcon();
            trayIcon = new TrayIcon(trayImage, "Voluntary AI Chat");
            trayIcon.setImageAutoSize(true);

            // 创建右键弹出菜单（AWT PopupMenu 自动出现在图标旁）
            final PopupMenu popup = new PopupMenu();
            // 设置中文字体
            popup.setFont(new Font("微软雅黑", Font.PLAIN, 12));

            final MenuItem showItem = new MenuItem("显示窗口");
            showItem.addActionListener(e -> showWindow());

            final MenuItem exitItem = new MenuItem("退出");
            exitItem.addActionListener(e -> exitApp());

            popup.add(showItem);
            popup.add(exitItem);
            trayIcon.setPopupMenu(popup);

            // 鼠标事件：左键单击→显示窗口
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(final MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1) {
                        showWindow();
                    }
                }
            });

            SystemTray.getSystemTray().add(trayIcon);
            trayEnabled = true;
            LOG.info("系统托盘初始化成功");
        } catch (final Exception e) {
            LOG.warn("系统托盘初始化失败: {}", e.getMessage());
            trayEnabled = false;
            Platform.setImplicitExit(true);
        }
    }

    /**
     * 最小化窗口到系统托盘
     */
    private static void hideToTray() {
        if (trayEnabled) {
            LOG.debug("最小化到系统托盘");
            primaryStage.hide();
        } else {
            // 没有托盘时关闭窗口就是退出
            LOG.info("系统托盘未启用，直接退出");
            exitApp();
        }
    }

    /**
     * 从系统托盘恢复窗口显示
     */
    private static void showWindow() {
        Platform.runLater(() -> {
            if (primaryStage != null) {
                primaryStage.show();
                primaryStage.toFront();
                LOG.debug("从托盘恢复窗口显示");
            }
        });
    }

    /**
     * 完全退出应用（托盘"退出"菜单调用）
     *
     * <p>
     * 移除托盘图标 → 清理资源 → 退出 JVM。
     * 确保进程完全终止。
     * </p>
     */
    private static void exitApp() {
        LOG.info("用户通过系统托盘退出应用");
        try {
            removeTrayIcon();
            Launcher.shutdown();
        } finally {
            javafx.application.Platform.exit();
            // 强制退出 JVM（兜底，确保进程终止）
            java.lang.System.exit(0);
        }
    }

    /**
     * 移除托盘图标
     */
    private static void removeTrayIcon() {
        if (trayEnabled && trayIcon != null) {
            try {
                SystemTray.getSystemTray().remove(trayIcon);
                trayEnabled = false;
                LOG.debug("已移除系统托盘图标");
            } catch (final Exception e) {
                LOG.warn("移除系统托盘图标失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 加载托盘图标
     *
     * <p>
     * 优先从资源文件加载图标，找不到时程序生成一个简单的圆角矩形图标。
     * </p>
     *
     * @return 托盘图标 Image
     */
    private static Image loadTrayIcon() {
        final URL iconUrl = App.class.getResource(TRAY_ICON_PATH);
        if (iconUrl != null) {
            return Toolkit.getDefaultToolkit().getImage(iconUrl);
        }
        // 资源文件不存在时程序生成一个 32x32 的蓝色圆角图标
        LOG.info("托盘图标资源未找到，使用程序生成的图标");
        return createDefaultTrayIcon();
    }

    /**
     * 创建默认托盘图标（32x32 蓝色圆角图标）
     *
     * @return 生成的图标 Image
     */
    private static Image createDefaultTrayIcon() {
        final int size = 32;
        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g2d = image.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(new Color(0x2196F3)); // Material Blue
            g2d.fillRoundRect(2, 2, size - 4, size - 4, 8, 8);
            // 画个简单的 "AI" 文字
            g2d.setColor(Color.WHITE);
            g2d.setFont(g2d.getFont().deriveFont(java.awt.Font.BOLD, 12f));
            final java.awt.FontMetrics metrics = g2d.getFontMetrics();
            final String text = "AI";
            final int x = (size - metrics.stringWidth(text)) / 2;
            final int y = ((size - metrics.getHeight()) / 2) + metrics.getAscent();
            g2d.drawString(text, x, y);
        } finally {
            g2d.dispose();
        }
        return image;
    }
}
