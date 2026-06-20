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

    /** 窗口高度 */
    private static final int WINDOW_HEIGHT = 520;

    /** 默认样式文件 */
    private static final String DEFAULT_CSS = "/css/default.css";

    /** 登录界面 FXML */
    private static final String LOGIN_FXML = "/fxml/LOGin.fxml";

    @Override
    public void init() {
        // 初始化客户端配置
        ClientConfig.getInstance().load();
        LOG.info("客户端配置加载成功: baseUrl={}", ClientConfig.getInstance().getBaseUrl());
    }

    @Override
    public void start(final Stage primaryStage) throws Exception {
        // 加载登录界面
        final FXMLLoader loader = new FXMLLoader(getClass().getResource(LOGIN_FXML));
        final VBox root = loader.load();

        // 创建场景并应用样式
        final Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        final java.net.URL cssUrl = App.class.getResource(DEFAULT_CSS);
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        // 设置窗口属性
        primaryStage.setTitle("AI 聊天 - 登录");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();

        LOG.info("客户端启动成功，已加载登录界面");
    }

    @Override
    public void stop() {
        LOG.info("客户端关闭");
    }
}

