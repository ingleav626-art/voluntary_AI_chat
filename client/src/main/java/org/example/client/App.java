package org.example.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.client.config.ClientConfig;
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

    /** 默认样式文件 */
    private static final String DEFAULT_CSS = "/css/default.css";

    /** 登录界面 FXML */
    private static final String LOGIN_FXML = "/fxml/login.fxml";

    /** 注册界面 FXML */
    private static final String REGISTER_FXML = "/fxml/register.fxml";

    /** 主舞台引用，用于页面切换 */
    private static Stage primaryStage;

    @Override
    public void init() {
        // 初始化客户端配置
        ClientConfig.getInstance().load();
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
            final Scene scene = createScene(root);
            primaryStage.setTitle("AI 聊天 - 登录");
            primaryStage.setScene(scene);
            primaryStage.setHeight(LOGIN_WINDOW_HEIGHT);
            LOG.info("已切换到登录界面");
        } catch (final Exception e) {
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
            final Scene scene = createScene(root);
            primaryStage.setTitle("AI 聊天 - 注册");
            primaryStage.setScene(scene);
            primaryStage.setHeight(REGISTER_WINDOW_HEIGHT);
            LOG.info("已切换到注册界面");
        } catch (final Exception e) {
            LOG.error("加载注册界面失败", e);
        }
    }

    /**
     * 创建场景并应用样式
     */
    private static Scene createScene(final VBox root) {
        final Scene scene = new Scene(root, WINDOW_WIDTH, primaryStage.getHeight());
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
