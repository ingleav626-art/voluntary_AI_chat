package org.example.client.controller;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
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
import org.example.client.model.ConversationInfo;
import org.example.client.model.LoginResponse;
import org.example.client.model.MessageInfo;
import org.example.client.view.ChatViewModel;
import org.example.client.view.MainViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 主界面控制器
 *
 * <p>
 * 负责会话列表展示、会话切换、聊天区域交互。
 * </p>
 * <p>
 * 顶部标题栏和功能侧边栏由 TopBarController 和 FunctionBarController 分别管理。
 * </p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class MainController implements Initializable {

    private static final Logger LOG = LoggerFactory.getLogger(MainController.class);

    @FXML
    private BorderPane rootPane;

    @FXML
    private VBox conversationPanel;

    @FXML
    private ListView<ConversationInfo> conversationList;

    @FXML
    private Label chatTitleLabel;

    @FXML
    private Label connectionLabel;

    @FXML
    private Button groupManageButton;

    @FXML
    private VBox announcementBox;

    @FXML
    private Label announcementContent;

    @FXML
    private Button toggleAnnouncementBtn;

    @FXML
    private ListView<MessageInfo> messageList;

    @FXML
    private VBox inputAreaContainer;

    @FXML
    private TextArea inputArea;

    @FXML
    private Label errorLabel;

    @FXML
    private Button sendButton;

    @FXML
    private Button plusButton;

    /** 嵌套的顶部标题栏控制器（由 fx:include 自动注入） */
    @FXML
    private TopBarController topBarController;

    /** 嵌套的功能侧边栏控制器（由 fx:include 自动注入） */
    @FXML
    private FunctionBarController functionBarController;

    private MainViewModel viewModel;

    /** 响应式隐藏阈值：窗口宽度低于此值时隐藏会话列表 */
    private static final double COLLAPSE_THRESHOLD = 760.0;

    /** 时间分割线显示阈值：与上一条消息间隔超过此分钟数时显示分割线 */
    private static final long TIME_SEPARATOR_INTERVAL_MINUTES = 5L;

    /** 毛玻璃效果：顶栏 */
    private org.example.client.util.FrostGlassEffect topBarFrost;

    /** 毛玻璃效果：功能栏 */
    private org.example.client.util.FrostGlassEffect functionBarFrost;

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        viewModel = new MainViewModel();

        // 绑定会话列表
        conversationList.itemsProperty().bind(viewModel.conversationsProperty());
        conversationList.setCellFactory(param -> new ConversationCell());
        conversationList.setOnMouseClicked(this::handleConversationClick);

        // 绑定聊天标题
        chatTitleLabel.textProperty().bind(
                Bindings.createStringBinding(() -> {
                    final ChatViewModel chatVm = viewModel.chatViewModelProperty().get();
                    return chatVm != null ? chatVm.getConversationName() : "请选择会话";
                }, viewModel.chatViewModelProperty()));

        // 绑定连接状态
        connectionLabel.textProperty().bind(
                Bindings.createStringBinding(() -> viewModel.connectedProperty().get() ? "已连接" : "连接中...",
                        viewModel.connectedProperty()));

        // 绑定消息列表
        messageList.itemsProperty().bind(
                Bindings.createObjectBinding(() -> {
                    final ChatViewModel chatVm = viewModel.chatViewModelProperty().get();
                    return chatVm != null ? chatVm.messagesProperty().get() : FXCollections.observableArrayList();
                }, viewModel.chatViewModelProperty()));

        messageList.setCellFactory(param -> new MessageCell());

        // 监听消息列表滚动，滚动到顶部时加载更多历史消息
        messageList.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) {
                setupScrollListener();
            }
        });

        // 监听 chatViewModel 变化，重新绑定输入框并更新管理按钮显示状态
        viewModel.chatViewModelProperty().addListener((obs, oldVm, newVm) -> {
            if (oldVm != null) {
                inputArea.textProperty().unbindBidirectional(oldVm.inputTextProperty());
            }
            if (newVm != null) {
                inputArea.textProperty().bindBidirectional(newVm.inputTextProperty());
                inputAreaContainer.setVisible(true);
                inputAreaContainer.setManaged(true);

                // 根据会话类型显示/隐藏群管理按钮
                final ConversationInfo conv = newVm.getConversation();
                if (conv != null && "GROUP".equals(conv.getTargetType())) {
                    groupManageButton.setVisible(true);
                    groupManageButton.setManaged(true);
                } else {
                    groupManageButton.setVisible(false);
                    groupManageButton.setManaged(false);
                }
            } else {
                inputArea.clear();
                inputAreaContainer.setVisible(false);
                inputAreaContainer.setManaged(false);
                groupManageButton.setVisible(false);
                groupManageButton.setManaged(false);
            }
        });

        // 初始状态：未选择会话时隐藏输入区域
        final ChatViewModel initialChatVm = viewModel.chatViewModelProperty().get();
        if (initialChatVm == null) {
            inputAreaContainer.setVisible(false);
            inputAreaContainer.setManaged(false);
        }

        // 绑定错误消息
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());

        // 设置退出登录回调
        viewModel.setOnLogout(e -> Platform.runLater(org.example.client.App::switchToLogin));

        // 设置被踢下线回调
        viewModel.setOnKickedOut(message -> Platform.runLater(() -> {
            final Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("账号被踢下线");
            alert.setHeaderText("您的账号已在其他设备登录");
            alert.setContentText(message);
            alert.getDialogPane().setMinWidth(380);
            alert.getDialogPane().setMinHeight(180);
            alert.setOnCloseRequest(e -> org.example.client.App.switchToLogin());
            alert.show();
        }));

        // 响应式：窗口宽度变化时自动隐藏/显示会话列表
        rootPane.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() < COLLAPSE_THRESHOLD) {
                conversationPanel.setVisible(false);
                conversationPanel.setManaged(false);
            } else {
                conversationPanel.setVisible(true);
                conversationPanel.setManaged(true);
            }
        });

        // 应用毛玻璃磨砂效果（方案A：Snapshot + GaussianBlur）
        // 延迟到 UI 渲染完成后挂载，确保节点已加入场景图
        Platform.runLater(this::setupFrostGlass);

        LOG.info("主界面控制器初始化完成");
    }

    /**
     * 挂载毛玻璃效果到顶栏和功能栏
     *
     * <p>
     * 方案A 实现：截取根节点快照并应用高斯模糊，
     * 在顶栏和功能栏底层显示模糊背景，模拟 Win11/macOS 毛玻璃质感。
     * 仅在窗口尺寸变化时更新快照，避免性能问题。
     * </p>
     */
    private void setupFrostGlass() {
        try {
            // 顶栏毛玻璃：背景源为根节点
            if (topBarController != null && topBarController.getTopBar() != null) {
                topBarFrost = org.example.client.util.FrostGlassEffect.create(
                        topBarController.getTopBar(), rootPane);
                topBarFrost.attach();
            }
            // 功能栏毛玻璃：背景源为根节点
            if (functionBarController != null && functionBarController.getFunctionBar() != null) {
                functionBarFrost = org.example.client.util.FrostGlassEffect.create(
                        functionBarController.getFunctionBar(), rootPane);
                functionBarFrost.attach();
            }
        } catch (final RuntimeException e) {
            LOG.warn("毛玻璃效果挂载失败，回退到半透明背景", e);
        }
    }

    /**
     * 获取主视图模型，供 TopBarController 使用
     *
     * @return 主视图模型
     */
    public MainViewModel getViewModel() {
        return viewModel;
    }

    /**
     * 初始化主界面数据
     *
     * @param loginResponse 登录响应
     */
    public void initData(final LoginResponse loginResponse) {
        viewModel.initialize(loginResponse);

        // 将 viewModel 传递给 TopBarController
        if (topBarController != null) {
            topBarController.setViewModel(viewModel);
        }
    }

    /**
     * 选中 AI 会话（从 AI 面板跳转时调用）
     *
     * @param conv AI 会话信息
     */
    public void selectAiConversation(final ConversationInfo conv) {
        if (conv == null) {
            return;
        }
        boolean found = false;
        for (final ConversationInfo existing : viewModel.conversationsProperty()) {
            if (conv.getSessionId().equals(existing.getSessionId())) {
                viewModel.selectConversation(existing);
                found = true;
                break;
            }
        }
        if (!found) {
            viewModel.selectConversation(conv);
        }
        LOG.info("已选中AI会话: sessionId={}, name={}", conv.getSessionId(), conv.getTargetName());
    }

    /**
     * 处理会话点击
     */
    private void handleConversationClick(final MouseEvent event) {
        final ConversationInfo selected = conversationList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            viewModel.selectConversation(selected);
        }
    }

    /** 标记正在加载更多历史消息 */
    private volatile boolean isLoadingMoreHistory;

    /**
     * 设置消息列表滚动监听
     */
    private void setupScrollListener() {
        Platform.runLater(() -> {
            for (final javafx.scene.Node node : messageList.lookupAll(".scroll-bar")) {
                if (node instanceof javafx.scene.control.ScrollBar scrollBar
                        && scrollBar.getOrientation() == javafx.geometry.Orientation.VERTICAL) {
                    scrollBar.valueProperty().addListener((obs, oldVal, newVal) -> {
                        if (newVal.doubleValue() <= 0.01 && !isLoadingMoreHistory) {
                            final ChatViewModel chatVm = viewModel.chatViewModelProperty().get();
                            if (chatVm != null && !chatVm.loadingProperty().get()) {
                                isLoadingMoreHistory = true;
                                chatVm.loadMoreHistory();
                                Platform.runLater(() -> isLoadingMoreHistory = false);
                            }
                        }
                    });
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
     * 处理群公告展开/折叠
     */
    @FXML
    private void handleToggleAnnouncement() {
        final boolean isExpanded = announcementContent.isVisible();
        announcementContent.setVisible(!isExpanded);
        announcementContent.setManaged(!isExpanded);
        toggleAnnouncementBtn.setText(isExpanded ? "展开" : "折叠");
    }

    /**
     * 处理发送图片
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
                new javafx.stage.FileChooser.ExtensionFilter("所有文件", "*.*"));

        final java.io.File selectedFile = fileChooser.showOpenDialog(plusButton.getScene().getWindow());
        if (selectedFile != null) {
            if (selectedFile.length() > 10 * 1024 * 1024) {
                errorLabel.setText("图片大小不能超过10MB");
                return;
            }
            final ImagePreviewDialog previewDialog = new ImagePreviewDialog(selectedFile);
            final boolean confirmed = previewDialog.showAndWait();
            if (confirmed) {
                chatVm.sendImage(selectedFile.toPath());
            }
        }
    }

    /**
     * 处理输入框按键
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
     */
    @FXML
    private void handlePlusButton() {
        final ContextMenu menu = new ContextMenu();
        final MenuItem imageItem = new MenuItem("发送图片");
        imageItem.setOnAction(e -> handleSendImage());
        final MenuItem fileItem = new MenuItem("发送文件");
        fileItem.setOnAction(e -> handleSendFile());
        menu.getItems().addAll(imageItem, fileItem);
        menu.show(plusButton, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    /**
     * 处理群管理按钮点击，跳转到群管理界面
     */
    @FXML
    private void handleGroupManage() {
        final ChatViewModel chatVm = viewModel.chatViewModelProperty().get();
        if (chatVm == null) {
            return;
        }

        final ConversationInfo conv = chatVm.getConversation();
        if (conv == null || !"GROUP".equals(conv.getTargetType())) {
            return;
        }

        LOG.info("点击群管理按钮: groupId={}, groupName={}", conv.getTargetId(), conv.getTargetName());

        try {
            // 切换到群管理界面
            org.example.client.App.switchToGroupPanel(conv.getTargetId());
        } catch (final Exception e) {
            LOG.error("跳转群管理界面失败", e);
            final Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("错误");
            alert.setHeaderText("无法打开群管理界面");
            alert.setContentText("请稍后重试");
            alert.show();
        }
    }

    /**
     * 处理发送文件
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
                new javafx.stage.FileChooser.ExtensionFilter("所有文件", "*.*"));

        final java.io.File selectedFile = fileChooser.showOpenDialog(plusButton.getScene().getWindow());
        if (selectedFile != null) {
            if (selectedFile.length() > 50 * 1024 * 1024) {
                errorLabel.setText("文件大小不能超过50MB");
                return;
            }
            chatVm.sendFile(selectedFile.toPath());
        }
    }

    /**
     * 会话列表 Cell
     */
    private static final class ConversationCell extends ListCell<ConversationInfo> {
        private final HBox cell;
        private final Circle avatarCircle;
        private final Label avatarText;
        private final javafx.scene.layout.StackPane avatarPane;
        private final VBox infoBox;
        private final Label name;
        private final Label time;
        private final Label lastMsg;
        private final Label badge;
        private final HBox bottomBox;

        ConversationCell() {
            cell = new HBox(10);
            cell.getStyleClass().add("conversation-cell");
            cell.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            avatarCircle = new Circle(18);
            avatarCircle.setFill(Color.valueOf("#FF6B9D"));
            avatarText = new Label("?");
            avatarText.setStyle("-fx-text-fill: white; -fx-font-size: 13; -fx-font-weight: bold;");
            avatarPane = new javafx.scene.layout.StackPane(avatarCircle, avatarText);
            avatarPane.setMouseTransparent(true);

            infoBox = new VBox(4);
            HBox.setHgrow(infoBox, Priority.ALWAYS);

            final HBox topBox = new HBox(8);
            topBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            name = new Label();
            name.getStyleClass().add("conv-name");

            final Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            time = new Label();
            time.getStyleClass().add("conv-time");

            topBox.getChildren().addAll(name, spacer, time);

            bottomBox = new HBox(8);
            bottomBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            lastMsg = new Label();
            lastMsg.getStyleClass().add("conv-last-msg");
            HBox.setHgrow(lastMsg, Priority.ALWAYS);

            badge = new Label();
            badge.getStyleClass().add("unread-badge");

            infoBox.getChildren().addAll(topBox, bottomBox);
            cell.getChildren().addAll(avatarPane, infoBox);
        }

        @Override
        protected void updateItem(final ConversationInfo item, final boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            final String displayName = item.getTargetName() != null ? item.getTargetName() : "?";
            if ("AI".equals(item.getTargetType())) {
                avatarCircle.setFill(Color.valueOf("#FF6B9D"));
            } else if ("GROUP".equals(item.getTargetType())) {
                avatarCircle.setFill(Color.valueOf("#9C27B0"));
            } else {
                avatarCircle.setFill(Color.valueOf("#42A5F5"));
            }
            avatarText.setText(!displayName.isEmpty() ? String.valueOf(displayName.charAt(0)) : "?");

            // 加载真实头像图片（如果有）
            if (item.getTargetAvatar() != null && !item.getTargetAvatar().isEmpty()) {
                org.example.client.util.ImageUtils.loadAvatarToPane(
                        item.getTargetAvatar(), avatarPane, 36);
            }

            name.setText(displayName);
            time.setText(formatTime(item.getLastMessageTime()));
            lastMsg.setText(item.getLastMessage() != null ? item.getLastMessage() : "");

            bottomBox.getChildren().clear();
            if (item.getUnreadCount() > 0) {
                badge.setText(String.valueOf(item.getUnreadCount()));
                bottomBox.getChildren().addAll(lastMsg, badge);
            } else {
                bottomBox.getChildren().add(lastMsg);
            }

            setGraphic(cell);
            setText(null);
        }

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
    private final class MessageCell extends ListCell<MessageInfo> {

        /** 是否显示时间分割线 */
        private boolean showTimeSeparator;

        /** 当前渲染的消息（用于时间分割线包装） */
        private MessageInfo currentMessage;

        @Override
        protected void updateItem(final MessageInfo item, final boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setContextMenu(null);
                return;
            }

            // 计算是否需要显示时间分割线（跨日期或与上一条间隔超过5分钟）
            currentMessage = item;
            showTimeSeparator = shouldShowTimeSeparator(item);

            if (item.isRecalled()) {
                final Label recalled = new Label("消息已撤回");
                recalled.getStyleClass().add("message-recalled");
                setGraphic(wrapWithSeparator(recalled));
                setText(null);
                setContextMenu(null);
                return;
            }

            if ("SYSTEM".equals(item.getType())) {
                final Label systemLabel = new Label(item.getContent());
                systemLabel.getStyleClass().add("message-system");
                systemLabel.setWrapText(true);
                final HBox systemBox = new HBox(systemLabel);
                systemBox.setAlignment(javafx.geometry.Pos.CENTER);
                systemBox.setMaxWidth(Double.MAX_VALUE);
                setGraphic(wrapWithSeparator(systemBox));
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

            if ("IMAGE".equals(item.getType())) {
                final String thumbnailUrl = item.getThumbnailUrl();
                final String originalUrl = item.getContent();
                final String imageUrl = thumbnailUrl != null && !thumbnailUrl.isEmpty() ? thumbnailUrl : originalUrl;

                final javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView();
                imageView.setPreserveRatio(true);
                imageView.setFitWidth(200);
                imageView.setFitHeight(150);
                imageView.getStyleClass().add("message-image");
                imageView.setCursor(javafx.scene.Cursor.HAND);

                imageView.setOnMouseClicked(e -> new ImageViewerDialog(originalUrl).show());

                final Label time = new Label(formatTime(item.getCreateTime()));
                time.getStyleClass().add("message-time");
                time.setStyle("-fx-text-fill: #999;");

                final VBox imageCell = new VBox(4, imageView, time);
                imageCell.setAlignment(sentByMe ? javafx.geometry.Pos.CENTER_RIGHT : javafx.geometry.Pos.CENTER_LEFT);

                final HBox alignBox = new HBox(imageCell);
                alignBox.setAlignment(sentByMe ? javafx.geometry.Pos.CENTER_RIGHT : javafx.geometry.Pos.CENTER_LEFT);
                alignBox.setMaxWidth(Double.MAX_VALUE);
                setGraphic(wrapWithSeparator(alignBox));
                setText(null);

                if (item.isSentByMe() && item.getMessageId() != null) {
                    final ContextMenu imageContextMenu = new ContextMenu();
                    if (isRecallable(item)) {
                        final MenuItem recallItem = new MenuItem("撤回");
                        recallItem.setOnAction(e -> handleRecallMessage(item));
                        imageContextMenu.getItems().add(recallItem);
                    }
                    if (!imageContextMenu.getItems().isEmpty()) {
                        setContextMenu(imageContextMenu);
                    }
                }

                if (imageUrl != null && !imageUrl.isEmpty()) {
                    final MessageInfo currentItem = item;
                    org.example.client.service.ChatService.getInstance()
                            .loadImageBytes(imageUrl)
                            .thenAccept(bytes -> Platform.runLater(() -> {
                                if (getItem() == currentItem && bytes != null && bytes.length > 0) {
                                    try {
                                        final javafx.scene.image.Image image = new javafx.scene.image.Image(
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
                return;
            } else if ("FILE".equals(item.getType())) {
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

                String sizeText = "";
                if (item.getExtra() != null) {
                    try {
                        final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        final com.fasterxml.jackson.databind.JsonNode extra = mapper.readTree(item.getExtra());
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
                    } catch (final Exception e) {
                        // 解析 extra 失败，记录日志不影响 UI 显示
                        LOG.debug("解析消息 extra 字段失败: messageId={}, extra={}",
                                item.getMessageId(), item.getExtra(), e);
                    }
                }
                final Label sizeLabel = new Label(sizeText);
                sizeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");
                fileInfo.getChildren().addAll(nameLabel, sizeLabel);
                fileBox.getChildren().addAll(fileIcon, fileInfo);
                bubble.getChildren().add(fileBox);
            } else {
                final Label content = new Label(item.getContent() != null ? item.getContent() : "");
                content.getStyleClass().add("message-content");
                content.setWrapText(true);
                content.setMaxWidth(400);
                bubble.getChildren().add(content);
            }

            final Label time = new Label(formatTime(item.getCreateTime()));
            time.getStyleClass().add("message-time");

            final boolean isGroupChat = item.getSessionId() != null && item.getSessionId().startsWith("g_");
            if (item.isSentByMe() && !isGroupChat) {
                final boolean isPending = item.getMessageId() == null || item.getMessageId() <= 0;
                final Label readStatus = new Label(isPending ? "未读" : (item.isRead() ? "已读" : "未读"));
                readStatus.getStyleClass().add("message-read-status");
                bubble.getChildren().addAll(time, readStatus);
            } else {
                bubble.getChildren().add(time);
            }

            final HBox alignBox = new HBox(bubble);
            HBox.setHgrow(bubble, Priority.NEVER);
            alignBox.setAlignment(sentByMe ? javafx.geometry.Pos.CENTER_RIGHT : javafx.geometry.Pos.CENTER_LEFT);
            alignBox.setMaxWidth(Double.MAX_VALUE);
            setGraphic(wrapWithSeparator(alignBox));
            setText(null);

            // 右键菜单
            if (item.isSentByMe() && item.getMessageId() != null) {
                final ContextMenu contextMenu = new ContextMenu();
                if (isRecallable(item)) {
                    final MenuItem recallItem = new MenuItem("撤回");
                    recallItem.setOnAction(e -> handleRecallMessage(item));
                    contextMenu.getItems().add(recallItem);
                }
                if (!contextMenu.getItems().isEmpty()) {
                    setContextMenu(contextMenu);
                } else {
                    setContextMenu(null);
                }
            } else {
                setContextMenu(null);
            }

            // 复制菜单
            if (item.getContent() != null && !item.getContent().isEmpty() && !"IMAGE".equals(item.getType())) {
                final MenuItem copyItem = new MenuItem("复制");
                copyItem.setOnAction(e -> copyMessageText(item.getContent()));
                if (getContextMenu() != null) {
                    getContextMenu().getItems().add(new javafx.scene.control.SeparatorMenuItem());
                    getContextMenu().getItems().add(copyItem);
                } else {
                    final ContextMenu copyMenu = new ContextMenu();
                    copyMenu.getItems().add(copyItem);
                    setContextMenu(copyMenu);
                }
            }
        }

        private void handleRecallMessage(final MessageInfo message) {
            final ChatViewModel chatVm = viewModel.chatViewModelProperty().get();
            if (chatVm != null) {
                chatVm.recallMessage(message);
            }
        }

        private boolean isRecallable(final MessageInfo message) {
            final String sessionId = message.getSessionId();
            if (sessionId != null && sessionId.startsWith("a_")) {
                return true;
            }
            if (message.getCreateTime() == null) {
                return false;
            }
            final java.time.Duration duration = java.time.Duration.between(
                    message.getCreateTime(), java.time.LocalDateTime.now());
            return duration.toMinutes() < 2;
        }

        private void copyMessageText(final String text) {
            final javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(text);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        }

        private String formatTime(final java.time.LocalDateTime time) {
            if (time == null) {
                return "";
            }
            final java.time.LocalDate today = java.time.LocalDate.now();
            if (time.toLocalDate().equals(today)) {
                return time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            }
            return time.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"));
        }

        /**
         * 判断是否需要显示时间分割线
         *
         * <p>
         * 规则：第一条消息、跨日期、或与上一条间隔超过5分钟时显示。
         * </p>
         *
         * @param current 当前消息
         * @return 是否显示分割线
         */
        private boolean shouldShowTimeSeparator(final MessageInfo current) {
            final int index = getIndex();
            final ListView<MessageInfo> list = getListView();
            if (list == null || list.getItems() == null || index <= 0) {
                return true;
            }
            if (index - 1 >= list.getItems().size()) {
                return true;
            }
            final MessageInfo previous = list.getItems().get(index - 1);
            if (previous == null || previous.getCreateTime() == null) {
                return true;
            }
            if (current.getCreateTime() == null) {
                return false;
            }
            // 跨日期显示分割线
            if (!current.getCreateTime().toLocalDate().equals(previous.getCreateTime().toLocalDate())) {
                return true;
            }
            // 同一天间隔超过阈值显示分割线
            final long minutes = java.time.Duration.between(
                    previous.getCreateTime(), current.getCreateTime()).toMinutes();
            return minutes >= TIME_SEPARATOR_INTERVAL_MINUTES;
        }

        /**
         * 创建时间分割线节点（居中灰色横线 + 时间文字）
         *
         * @param time 消息时间
         * @return 分割线节点
         */
        private javafx.scene.Node createTimeSeparator(final java.time.LocalDateTime time) {
            final HBox separator = new HBox();
            separator.getStyleClass().add("message-time-separator");
            separator.setAlignment(javafx.geometry.Pos.CENTER);

            final Region leftLine = new Region();
            HBox.setHgrow(leftLine, Priority.ALWAYS);
            leftLine.getStyleClass().add("separator-line");

            final Label timeLabel = new Label(formatSeparatorTime(time));
            timeLabel.getStyleClass().add("separator-text");

            final Region rightLine = new Region();
            HBox.setHgrow(rightLine, Priority.ALWAYS);
            rightLine.getStyleClass().add("separator-line");

            separator.getChildren().addAll(leftLine, timeLabel, rightLine);
            return separator;
        }

        /**
         * 格式化分割线时间文字
         *
         * <p>
         * 今天显示 HH:mm，昨天显示"昨天 HH:mm"，同年显示"MM月dd日 HH:mm"，跨年显示完整日期。
         * </p>
         *
         * @param time 消息时间
         * @return 格式化后的时间文字
         */
        private String formatSeparatorTime(final java.time.LocalDateTime time) {
            if (time == null) {
                return "";
            }
            final java.time.LocalDate today = java.time.LocalDate.now();
            final java.time.LocalDate yesterday = today.minusDays(1);
            if (time.toLocalDate().equals(today)) {
                return time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            } else if (time.toLocalDate().equals(yesterday)) {
                return "昨天 " + time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            } else if (time.getYear() == today.getYear()) {
                return time.format(java.time.format.DateTimeFormatter.ofPattern("MM月dd日 HH:mm"));
            } else {
                return time.format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm"));
            }
        }

        /**
         * 用时间分割线包装消息内容（如需要）
         *
         * @param content 消息内容节点
         * @return 包装后的节点
         */
        private javafx.scene.Node wrapWithSeparator(final javafx.scene.Node content) {
            if (!showTimeSeparator || currentMessage == null) {
                return content;
            }
            final VBox outer = new VBox();
            outer.getChildren().add(createTimeSeparator(currentMessage.getCreateTime()));
            outer.getChildren().add(content);
            return outer;
        }
    }
}
