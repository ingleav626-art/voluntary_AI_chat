package org.example.client.controller;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Callback;

import org.example.client.model.ConversationInfo;
import org.example.client.model.LoginResponse;
import org.example.client.model.MessageInfo;
import org.example.client.model.UserInfo;
import org.example.client.view.ChatViewModel;
import org.example.client.view.MainViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 主界面控制器
 *
 * <p>负责会话列表展示、会话切换、聊天区域交互、WebSocket 连接状态展示。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class MainController implements Initializable {

    private static final Logger LOG = LoggerFactory.getLogger(MainController.class);

    @FXML
    private BorderPane rootPane;

    @FXML
    private Circle avatarCircle;

    @FXML
    private Label avatarText;

    @FXML
    private Label usernameLabel;

    @FXML
    private Circle statusDot;

    @FXML
    private Label statusLabel;

    @FXML
    private Button settingsButton;

    @FXML
    private TextField searchField;

    @FXML
    private ListView<ConversationInfo> conversationList;

    @FXML
    private Button refreshButton;

    @FXML
    private Button friendButton;

    @FXML
    private Button logoutButton;

    @FXML
    private Label chatTitleLabel;

    @FXML
    private Label connectionLabel;

    @FXML
    private ListView<MessageInfo> messageList;

    @FXML
    private TextArea inputArea;

    @FXML
    private Label errorLabel;

    @FXML
    private Button sendButton;

    private MainViewModel viewModel;

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        viewModel = new MainViewModel();

        // 绑定用户信息
        usernameLabel.textProperty().bind(
                Bindings.createStringBinding(() -> {
                    final UserInfo user = viewModel.currentUserProperty().get();
                    return user != null && user.getUsername() != null ? user.getUsername() : "用户";
                }, viewModel.currentUserProperty()));

        avatarText.textProperty().bind(
                Bindings.createStringBinding(() -> {
                    final UserInfo user = viewModel.currentUserProperty().get();
                    if (user != null && user.getUsername() != null && !user.getUsername().isEmpty()) {
                        return String.valueOf(user.getUsername().charAt(0));
                    }
                    return "?";
                }, viewModel.currentUserProperty()));

        // 绑定连接状态
        statusLabel.textProperty().bind(
                Bindings.createStringBinding(() ->
                        viewModel.connectedProperty().get() ? "在线" : "离线",
                        viewModel.connectedProperty()));

        statusDot.fillProperty().bind(
                Bindings.createObjectBinding(() ->
                        viewModel.connectedProperty().get() ? Color.valueOf("#4CAF50") : Color.valueOf("#9E9E9E"),
                        viewModel.connectedProperty()));

        connectionLabel.textProperty().bind(
                Bindings.createStringBinding(() ->
                        viewModel.connectedProperty().get() ? "已连接" : "连接中...",
                        viewModel.connectedProperty()));

        // 绑定会话列表
        conversationList.itemsProperty().bind(viewModel.conversationsProperty());

        // 设置会话列表 Cell
        conversationList.setCellFactory(param -> new ConversationCell());

        // 会话点击事件
        conversationList.setOnMouseClicked(this::handleConversationClick);

        // 绑定聊天标题
        chatTitleLabel.textProperty().bind(
                Bindings.createStringBinding(() -> {
                    final ChatViewModel chatVm = viewModel.chatViewModelProperty().get();
                    return chatVm != null ? chatVm.getConversationName() : "请选择会话";
                }, viewModel.chatViewModelProperty()));

        // 绑定消息列表
        messageList.itemsProperty().bind(
                Bindings.createObjectBinding(() -> {
                    final ChatViewModel chatVm = viewModel.chatViewModelProperty().get();
                    return chatVm != null ? chatVm.messagesProperty().get() : FXCollections.observableArrayList();
                }, viewModel.chatViewModelProperty()));

        // 设置消息列表 Cell
        messageList.setCellFactory(param -> new MessageCell());

        // 绑定输入框
        final ChatViewModel chatVm = viewModel.chatViewModelProperty().get();
        if (chatVm != null) {
            inputArea.textProperty().bindBidirectional(chatVm.inputTextProperty());
        }
        // 监听 chatViewModel 变化，重新绑定输入框
        viewModel.chatViewModelProperty().addListener((obs, oldVm, newVm) -> {
            if (oldVm != null) {
                inputArea.textProperty().unbindBidirectional(oldVm.inputTextProperty());
            }
            if (newVm != null) {
                inputArea.textProperty().bindBidirectional(newVm.inputTextProperty());
            } else {
                inputArea.clear();
            }
        });

        // 绑定错误消息
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());

        // 设置退出登录回调
        viewModel.setOnLogout(e -> {
            Platform.runLater(() -> {
                org.example.client.App.switchToLogin();
            });
        });

        LOG.info("主界面控制器初始化完成");
    }

    /**
     * 初始化主界面数据
     *
     * @param loginResponse 登录响应
     */
    public void initData(final LoginResponse loginResponse) {
        viewModel.initialize(loginResponse);
    }

    /**
     * 处理会话点击
     *
     * @param event 鼠标事件
     */
    private void handleConversationClick(final MouseEvent event) {
        final ConversationInfo selected = conversationList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            viewModel.selectConversation(selected);
        }
    }

    /**
     * 处理发送消息
     */
    @FXML
    private void handleSend() {
        final ChatViewModel chatVm = viewModel.chatViewModelProperty().get();
        if (chatVm != null) {
            chatVm.sendMessage();
        }
    }

    /**
     * 处理输入框按键
     *
     * @param event 键盘事件
     */
    @FXML
    private void handleInputKeyPress(final KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()) {
            event.consume();
            handleSend();
        }
    }

    /**
     * 处理刷新
     */
    @FXML
    private void handleRefresh() {
        LOG.info("刷新会话列表");
        viewModel.loadConversations();
    }

    /**
     * 处理设置
     */
    @FXML
    private void handleSettings() {
        LOG.info("点击设置");
        // TODO: 打开设置面板
    }

    /**
     * 处理好友管理
     */
    @FXML
    private void handleFriend() {
        LOG.info("切换到好友面板");
        org.example.client.App.switchToFriend();
    }

    /**
     * 处理退出登录
     */
    @FXML
    private void handleLogout() {
        LOG.info("退出登录");
        viewModel.logout();
    }

    /**
     * 会话列表 Cell
     */
    private static final class ConversationCell extends ListCell<ConversationInfo> {

        @Override
        protected void updateItem(final ConversationInfo item, final boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            final VBox cell = new VBox(4);
            cell.getStyleClass().add("conversation-cell");

            final HBox topBox = new HBox(8);
            topBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            final Label name = new Label(item.getTargetName() != null ? item.getTargetName() : "未知");
            name.getStyleClass().add("conv-name");

            final Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            final Label time = new Label(formatTime(item.getLastMessageTime()));
            time.getStyleClass().add("conv-time");

            topBox.getChildren().addAll(name, spacer, time);

            final HBox bottomBox = new HBox(8);
            bottomBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            final Label lastMsg = new Label(item.getLastMessage() != null ? item.getLastMessage() : "");
            lastMsg.getStyleClass().add("conv-last-msg");
            HBox.setHgrow(lastMsg, Priority.ALWAYS);

            if (item.getUnreadCount() > 0) {
                final Label badge = new Label(String.valueOf(item.getUnreadCount()));
                badge.getStyleClass().add("unread-badge");
                bottomBox.getChildren().addAll(lastMsg, badge);
            } else {
                bottomBox.getChildren().add(lastMsg);
            }

            cell.getChildren().addAll(topBox, bottomBox);
            setGraphic(cell);
            setText(null);
        }

        /**
         * 格式化时间
         *
         * @param time 时间
         * @return 格式化字符串
         */
        private String formatTime(final java.time.LocalDateTime time) {
            if (time == null) {
                return "";
            }
            final java.time.LocalDate today = java.time.LocalDate.now();
            if (time.toLocalDate().equals(today)) {
                return time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            }
            return time.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd"));
        }
    }

    /**
     * 消息列表 Cell
     */
    private static final class MessageCell extends ListCell<MessageInfo> {

        @Override
        protected void updateItem(final MessageInfo item, final boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            if (item.isRecalled()) {
                final Label recalled = new Label("消息已撤回");
                recalled.getStyleClass().add("message-recalled");
                setGraphic(recalled);
                setText(null);
                return;
            }

            final VBox bubble = new VBox(4);

            if (item.isSentByMe()) {
                bubble.getStyleClass().add("message-bubble-sent");
                bubble.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
            } else {
                bubble.getStyleClass().add("message-bubble-received");
                bubble.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            }

            final Label content = new Label(item.getContent() != null ? item.getContent() : "");
            content.getStyleClass().add("message-content");
            content.setWrapText(true);
            content.setMaxWidth(400);

            final Label time = new Label(formatTime(item.getCreateTime()));
            time.getStyleClass().add("message-time");

            bubble.getChildren().addAll(content, time);
            setGraphic(bubble);
            setText(null);
        }

        /**
         * 格式化时间
         *
         * @param time 时间
         * @return 格式化字符串
         */
        private String formatTime(final java.time.LocalDateTime time) {
            if (time == null) {
                return "";
            }
            return time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        }
    }
}
