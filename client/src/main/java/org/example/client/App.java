package org.example.client;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javafx.application.Application;
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
                ClientConfig.getInstance().setBaseUrl(ServerConnectionManager.getInstance().getCurrentMode().getDefaultBaseUrl());
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
        LOG.info("客户端关闭");
    }
}
