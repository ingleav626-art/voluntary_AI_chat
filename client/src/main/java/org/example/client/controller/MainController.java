package org.example.client.controller;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
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
    private Button groupButton;

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

    @FXML
    private Button plusButton;

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

        // 搜索会话：监听搜索框文本变化，防抖避免频繁请求
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            viewModel.searchConversations(newVal);
        });

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

        // 监听消息列表滚动，滚动到顶部时加载更多历史消息
        messageList.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) {
                setupScrollListener();
            }
        });

        // 监听搜索框文本变化，实时过滤会话列表
        searchField.textProperty().addListener((obs, oldVal, newVal) ->
                viewModel.filterConversations(newVal));

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

        // 调试：输出加号按钮状态
        if (plusButton != null) {
            LOG.info("加号按钮初始化: text='{}', visible={}, styleClass={}, width={}, height={}",
                    plusButton.getText(), plusButton.isVisible(),
                    plusButton.getStyleClass(), plusButton.getWidth(), plusButton.getHeight());
        } else {
            LOG.warn("加号按钮为 null！FXML 注入可能失败");
        }

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

    /** 标记正在加载更多历史消息，防止重复触发 */
    private volatile boolean isLoadingMoreHistory;

    /**
     * 设置消息列表滚动监听
     * 使用垂直滚动条监听滚动位置，滚动到顶部时加载更多历史消息
     */
    private void setupScrollListener() {
        // 延迟到 VirtualFlow 渲染完成后查找 ScrollBar
        Platform.runLater(() -> {
            for (final javafx.scene.Node node : messageList.lookupAll(".scroll-bar")) {
                if (node instanceof javafx.scene.control.ScrollBar scrollBar
                        && scrollBar.getOrientation() == javafx.geometry.Orientation.VERTICAL) {
                    scrollBar.valueProperty().addListener((obs, oldVal, newVal) -> {
                        // 滚动到顶部（value <= 0.01）且没有正在加载
                        if (newVal.doubleValue() <= 0.01 && !isLoadingMoreHistory) {
                            final ChatViewModel chatVm = viewModel.chatViewModelProperty().get();
                            if (chatVm != null && !chatVm.loadingProperty().get()) {
                                isLoadingMoreHistory = true;
                                final int currentSize = chatVm.getMessages().size();
                                chatVm.loadMoreHistory();
                                // 加载完成后恢复状态
                                Platform.runLater(() -> {
                                    isLoadingMoreHistory = false;
                                });
                            }
                        }
                    });
                    LOG.debug("消息列表滚动监听已注册");
                    break;
                }
            }
        });
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
     * 处理发送图片
     * 打开文件选择器，选择图片后上传并发送
     */
    @FXML
    private void handleSendImage() {
        final ChatViewModel chatVm = viewModel.chatViewModelProperty().get();
        if (chatVm == null) {
            return;
        }

        final javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("选择图片");
        fileChooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("图片文件", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.webp"),
                new javafx.stage.FileChooser.ExtensionFilter("所有文件", "*.*")
        );

        final java.io.File selectedFile = fileChooser.showOpenDialog(plusButton.getScene().getWindow());
        if (selectedFile != null) {
            // 校验文件大小（10MB）
            if (selectedFile.length() > 10 * 1024 * 1024) {
                errorLabel.setText("图片大小不能超过10MB");
                return;
            }

            // 显示图片预览对话框
            final ImagePreviewDialog previewDialog = new ImagePreviewDialog(selectedFile);
            final boolean confirmed = previewDialog.showAndWait();

            // 用户确认后上传
            if (confirmed) {
                chatVm.sendImage(selectedFile.toPath());
                LOG.info("用户确认上传图片: {}", selectedFile.getName());
            } else {
                LOG.info("用户取消上传图片: {}", selectedFile.getName());
            }
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
     * 处理 + 按钮点击
     * 弹出快捷菜单：发送图片、发送文件
     */
    @FXML
    private void handlePlusButton() {
        final javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        final javafx.scene.control.MenuItem imageItem = new javafx.scene.control.MenuItem("发送图片");
        imageItem.setOnAction(e -> handleSendImage());
        final javafx.scene.control.MenuItem fileItem = new javafx.scene.control.MenuItem("发送文件");
        fileItem.setOnAction(e -> handleSendFile());
        menu.getItems().addAll(imageItem, fileItem);
        menu.show(plusButton, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    /**
     * 处理发送文件
     * 打开文件选择器，选择文件后发送
     */
    @FXML
    private void handleSendFile() {
        final ChatViewModel chatVm = viewModel.chatViewModelProperty().get();
        if (chatVm == null) {
            return;
        }

        final javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("选择文件");
        fileChooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("所有文件", "*.*")
        );

        final java.io.File selectedFile = fileChooser.showOpenDialog(plusButton.getScene().getWindow());
        if (selectedFile != null) {
            // 校验文件大小（50MB）
            if (selectedFile.length() > 50 * 1024 * 1024) {
                errorLabel.setText("文件大小不能超过50MB");
                return;
            }
            chatVm.sendFile(selectedFile.toPath());
            LOG.info("用户选择发送文件: {}", selectedFile.getName());
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
     * 处理群组管理
     */
    @FXML
    private void handleGroup() {
        LOG.info("切换到群组面板");
        org.example.client.App.switchToGroup();
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
     * 非静态内部类，可访问外部类的 viewModel 实现撤回
     */
    private final class MessageCell extends ListCell<MessageInfo> {

        @Override
        protected void updateItem(final MessageInfo item, final boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setContextMenu(null);
                return;
            }

            if (item.isRecalled()) {
                final Label recalled = new Label("消息已撤回");
                recalled.getStyleClass().add("message-recalled");
                setGraphic(recalled);
                setText(null);
                setContextMenu(null);
                return;
            }

            // 系统消息（群成员加入等）居中灰色文字显示
            if ("SYSTEM".equals(item.getType())) {
                final Label systemLabel = new Label(item.getContent());
                systemLabel.getStyleClass().add("message-system");
                systemLabel.setWrapText(true);
                final HBox systemBox = new HBox(systemLabel);
                systemBox.setAlignment(javafx.geometry.Pos.CENTER);
                systemBox.setMaxWidth(Double.MAX_VALUE);
                setGraphic(systemBox);
                setText(null);
                setContextMenu(null);
                return;
            }

            final VBox bubble = new VBox(4);

            final boolean sentByMe = item.isSentByMe();
            if (sentByMe) {
                bubble.getStyleClass().add("message-bubble-sent");
                bubble.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
            } else {
                bubble.getStyleClass().add("message-bubble-received");
                bubble.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            }

            // 根据消息类型渲染内容
            if ("IMAGE".equals(item.getType())) {
                // 图片消息：不带气泡框，直接显示图片 + 底部时间
                final String thumbnailUrl = item.getThumbnailUrl();
                final String originalUrl = item.getContent();
                final String imageUrl = thumbnailUrl != null && !thumbnailUrl.isEmpty() ? thumbnailUrl : originalUrl;

                final javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView();
                final double thumbWidth = 200;
                final double thumbHeight = 150;
                imageView.setPreserveRatio(true);
                imageView.setFitWidth(thumbWidth);
                imageView.setFitHeight(thumbHeight);
                imageView.getStyleClass().add("message-image");
                imageView.setCursor(javafx.scene.Cursor.HAND);

                // 添加点击事件（点击显示原图）
                imageView.setOnMouseClicked(e -> {
                    LOG.info("点击图片: originalUrl={}", originalUrl);
                    final ImageViewerDialog dialog = new ImageViewerDialog(originalUrl);
                    dialog.show();
                });

                // 时间标签
                final Label time = new Label(formatTime(item.getCreateTime()));
                time.getStyleClass().add("message-time");
                // 图片消息的时间颜色
                time.setStyle("-fx-text-fill: #999;");

                // 图片 + 时间垂直排列
                final VBox imageCell = new VBox(4, imageView, time);
                imageCell.setAlignment(sentByMe
                        ? javafx.geometry.Pos.CENTER_RIGHT
                        : javafx.geometry.Pos.CENTER_LEFT);

                // 左右对齐
                final HBox alignBox = new HBox(imageCell);
                if (sentByMe) {
                    alignBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
                } else {
                    alignBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                }
                alignBox.setMaxWidth(Double.MAX_VALUE);
                setGraphic(alignBox);
                setText(null);

                // 异步加载图片（携带认证 Token）
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    final MessageInfo currentItem = item;
                    org.example.client.service.ChatService.getInstance()
                            .loadImageBytes(imageUrl)
                            .thenAccept(bytes -> Platform.runLater(() -> {
                                if (getItem() == currentItem && bytes != null && bytes.length > 0) {
                                    try {
                                        final javafx.scene.image.Image image =
                                                new javafx.scene.image.Image(
                                                        new java.io.ByteArrayInputStream(bytes));
                                        imageView.setImage(image);
                                    } catch (final Exception e) {
                                        LOG.warn("图片解码失败: {}", imageUrl, e);
                                    }
                                }
                            }))
                            .exceptionally(ex -> {
                                LOG.warn("图片加载失败: {} - {}", imageUrl, ex.getMessage());
                                return null;
                            });
                }

                // 图片消息不走后续通用逻辑，直接返回
                return;
            } else if ("FILE".equals(item.getType())) {
                // 文件消息：显示文件名和大小
                final String fileName = item.getContent();
                final HBox fileBox = new HBox(10);
                fileBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                final Label fileIcon = new Label("\uD83D\uDCC4");
                fileIcon.setStyle("-fx-font-size: 24px;");

                final VBox fileInfo = new VBox(2);
                final Label nameLabel = new Label(fileName != null ? fileName : "未知文件");
                nameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
                nameLabel.setWrapText(true);
                nameLabel.setMaxWidth(250);

                // 解析文件大小
                String sizeText = "";
                if (item.getExtra() != null) {
                    try {
                        final com.fasterxml.jackson.databind.ObjectMapper mapper =
                                new com.fasterxml.jackson.databind.ObjectMapper();
                        final com.fasterxml.jackson.databind.JsonNode extra =
                                mapper.readTree(item.getExtra());
                        if (extra.has("fileSize")) {
                            final long fileSize = extra.get("fileSize").asLong();
                            if (fileSize >= 1024 * 1024) {
                                sizeText = String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
                            } else if (fileSize >= 1024) {
                                sizeText = String.format("%.1f KB", fileSize / 1024.0);
                            } else {
                                sizeText = fileSize + " B";
                            }
                        }
                    } catch (final Exception ignored) {
                        // 忽略解析错误
                    }
                }
                final Label sizeLabel = new Label(sizeText);
                sizeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");

                fileInfo.getChildren().addAll(nameLabel, sizeLabel);
                fileBox.getChildren().addAll(fileIcon, fileInfo);
                bubble.getChildren().add(fileBox);
            } else {
                // 文本消息：渲染 Label
                final Label content = new Label(item.getContent() != null ? item.getContent() : "");
                content.getStyleClass().add("message-content");
                content.setWrapText(true);
                content.setMaxWidth(400);
                bubble.getChildren().add(content);
            }

            final Label time = new Label(formatTime(item.getCreateTime()));
            time.getStyleClass().add("message-time");

            // 已读状态标签（仅自己发送的消息显示）
            if (item.isSentByMe() && item.getMessageId() != null && item.getMessageId() > 0) {
                final Label readStatus = new Label(item.isRead() ? "已读" : "未读");
                readStatus.getStyleClass().add("message-read-status");
                bubble.getChildren().addAll(time, readStatus);
            } else {
                bubble.getChildren().add(time);
            }

            setGraphic(bubble);
            setText(null);

            // 通过外层 HBox 控制气泡在单元格内的左右对齐
            final HBox alignBox = new HBox(bubble);
            HBox.setHgrow(bubble, javafx.scene.layout.Priority.NEVER);
            if (sentByMe) {
                alignBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
            } else {
                alignBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            }
            alignBox.setMaxWidth(Double.MAX_VALUE);
            setGraphic(alignBox);

            // 为自己发送且未撤回的消息添加右键菜单
            if (item.isSentByMe() && item.getMessageId() != null) {
                final ContextMenu contextMenu = new ContextMenu();
                final MenuItem recallItem = new MenuItem("撤回");
                recallItem.setOnAction(e -> handleRecallMessage(item));
                contextMenu.getItems().add(recallItem);
                setContextMenu(contextMenu);
            } else {
                setContextMenu(null);
            }

            // 为所有文本消息添加复制菜单
            if (item.getContent() != null && !item.getContent().isEmpty()
                    && !"IMAGE".equals(item.getType())) {
                final ContextMenu copyMenu = new ContextMenu();
                final MenuItem copyItem = new MenuItem("复制");
                copyItem.setOnAction(e -> copyMessageText(item.getContent()));
                copyMenu.getItems().add(copyItem);

                // 如果已有撤回菜单，合并到同一个菜单
                if (getContextMenu() != null) {
                    getContextMenu().getItems().add(new javafx.scene.control.SeparatorMenuItem());
                    getContextMenu().getItems().add(copyItem);
                } else {
                    setContextMenu(copyMenu);
                }
            }
        }

        /**
         * 处理撤回消息
         *
         * @param message 消息
         */
        private void handleRecallMessage(final MessageInfo message) {
            final ChatViewModel chatVm = viewModel.chatViewModelProperty().get();
            if (chatVm != null) {
                chatVm.recallMessage(message);
            }
        }

        /**
         * 复制消息文本到剪贴板
         *
         * @param text 消息文本
         */
        private void copyMessageText(final String text) {
            final javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(text);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
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
