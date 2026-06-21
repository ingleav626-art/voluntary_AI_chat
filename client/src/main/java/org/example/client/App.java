package org.example.client;

import java.io.IOException;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.client.config.ClientConfig;
import org.example.client.controller.MainController;
import org.example.client.model.LoginResponse;
import org.example.client.util.ServerDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JavaFX 客户端主应用
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

    /** 主界面 FXML */
    private static final String MAIN_FXML = "/fxml/main.fxml";

    /** 好友面板 FXML */
    private static final String FRIEND_FXML = "/fxml/friend_panel.fxml";

    /** 主舞台引用，用于页面切换 */
    private static Stage primaryStage;

    /** 当前登录响应，用于界面间切换保持登录态 */
    private static LoginResponse currentLoginResponse;

    @Override
    public void init() {
        // 检查环境变量 CLIENT_CONFIG，支持热点测试环境
        final String configEnv = System.getenv("CLIENT_CONFIG");
        if (configEnv != null && !configEnv.isEmpty()) {
            ClientConfig.getInstance().setConfigFile(configEnv);
            LOG.info("使用自定义配置文件: {}", configEnv);
            ClientConfig.getInstance().load();
        } else {
            // 尝试自动发现服务器
            LOG.info("尝试自动发现服务器...");
            final String discoveredServer = ServerDiscovery.autoDiscover();
            if (discoveredServer != null) {
                LOG.info("自动发现服务器成功: {}", discoveredServer);
                // 动态设置服务器地址
                ClientConfig.getInstance().setBaseUrl(discoveredServer);
            } else {
                LOG.info("自动发现失败，使用默认配置");
                ClientConfig.getInstance().load();
            }
        }

        LOG.info("客户端配置加载成功: baseUrl={}", ClientConfig.getInstance().getBaseUrl());
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
