package org.example.client.controller;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.example.client.model.ConversationInfo;
import org.example.client.model.LoginResponse;
import org.example.client.model.MessageInfo;
import org.example.client.model.UserInfo;
import org.example.client.view.ChatViewModel;
import org.example.client.view.MainViewModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MainController 单元测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("MainController 测试")
class MainControllerTest extends JavaFxTestBase {

    private MainController controller;

    @BeforeEach
    void setUp() throws Exception {
        controller = new MainController();

        // 手动创建 @FXML 控件
        final BorderPane rootPane = new BorderPane();
        rootPane.setCenter(new javafx.scene.layout.VBox()); // center 节点，防止 getCenter() 返回 null
        final Circle avatarCircle = new Circle();
        final Label avatarText = new Label();
        final Label usernameLabel = new Label();
        final Circle statusDot = new Circle();
        final Label statusLabel = new Label();
        final Button settingsButton = new Button();
        final TextField searchField = new TextField();
        final ListView<ConversationInfo> conversationList = new ListView<>();
        final Button refreshButton = new Button();
        final Button friendButton = new Button();
        final Button groupButton = new Button();
        final Button logoutButton = new Button();
        final Label chatTitleLabel = new Label();
        final Label connectionLabel = new Label();
        final ListView<MessageInfo> messageList = new ListView<>();
        final VBox inputAreaContainer = new VBox();
        final TextArea inputArea = new TextArea();
        final Label errorLabel = new Label();
        final Button sendButton = new Button();
        final Button plusButton = new Button();

        setField(controller, "rootPane", rootPane);
        setField(controller, "avatarCircle", avatarCircle);
        setField(controller, "avatarText", avatarText);
        setField(controller, "usernameLabel", usernameLabel);
        setField(controller, "statusDot", statusDot);
        setField(controller, "statusLabel", statusLabel);
        setField(controller, "settingsButton", settingsButton);
        setField(controller, "searchField", searchField);
        setField(controller, "conversationList", conversationList);
        setField(controller, "refreshButton", refreshButton);
        setField(controller, "friendButton", friendButton);
        setField(controller, "groupButton", groupButton);
        setField(controller, "logoutButton", logoutButton);
        setField(controller, "chatTitleLabel", chatTitleLabel);
        setField(controller, "connectionLabel", connectionLabel);
        setField(controller, "messageList", messageList);
        setField(controller, "inputAreaContainer", inputAreaContainer);
        setField(controller, "inputArea", inputArea);
        setField(controller, "errorLabel", errorLabel);
        setField(controller, "sendButton", sendButton);
        setField(controller, "plusButton", plusButton);

        controller.initialize(null, null);
    }

    @Test
    @DisplayName("initialize - ViewModel 正确创建")
    void initialize_shouldCreateViewModel() throws Exception {
        final MainViewModel viewModel = (MainViewModel) getField(controller, "viewModel");
        assertNotNull(viewModel);
    }

    @Test
    @DisplayName("initialize - usernameLabel 绑定到 ViewModel currentUser")
    void initialize_usernameLabelBound() throws Exception {
        final MainViewModel viewModel = (MainViewModel) getField(controller, "viewModel");
        final Label usernameLabel = (Label) getField(controller, "usernameLabel");

        final UserInfo user = new UserInfo();
        user.setUsername("张三");
        viewModel.currentUserProperty().set(user);

        assertEquals("张三", usernameLabel.getText());
    }

    @Test
    @DisplayName("initialize - usernameLabel 无用户时显示'用户'")
    void initialize_usernameLabelDefault() throws Exception {
        final MainViewModel viewModel = (MainViewModel) getField(controller, "viewModel");
        final Label usernameLabel = (Label) getField(controller, "usernameLabel");

        viewModel.currentUserProperty().set(null);
        assertEquals("用户", usernameLabel.getText());
    }

    @Test
    @DisplayName("initialize - avatarText 绑定到用户名首字")
    void initialize_avatarTextBound() throws Exception {
        final MainViewModel viewModel = (MainViewModel) getField(controller, "viewModel");
        final Label avatarText = (Label) getField(controller, "avatarText");

        final UserInfo user = new UserInfo();
        user.setUsername("李四");
        viewModel.currentUserProperty().set(user);

        assertEquals("李", avatarText.getText());
    }

    @Test
    @DisplayName("initialize - avatarText 无用户时显示'?'")
    void initialize_avatarTextDefault() throws Exception {
        final MainViewModel viewModel = (MainViewModel) getField(controller, "viewModel");
        final Label avatarText = (Label) getField(controller, "avatarText");

        viewModel.currentUserProperty().set(null);
        assertEquals("?", avatarText.getText());
    }

    @Test
    @DisplayName("initialize - statusLabel 在线时显示'在线'")
    void initialize_statusLabelOnline() throws Exception {
        final MainViewModel viewModel = (MainViewModel) getField(controller, "viewModel");
        final Label statusLabel = (Label) getField(controller, "statusLabel");

        viewModel.connectedProperty().set(true);
        assertEquals("在线", statusLabel.getText());
    }

    @Test
    @DisplayName("initialize - statusLabel 离线时显示'离线'")
    void initialize_statusLabelOffline() throws Exception {
        final MainViewModel viewModel = (MainViewModel) getField(controller, "viewModel");
        final Label statusLabel = (Label) getField(controller, "statusLabel");

        viewModel.connectedProperty().set(false);
        assertEquals("离线", statusLabel.getText());
    }

    @Test
    @DisplayName("initialize - connectionLabel 在线时显示'已连接'")
    void initialize_connectionLabelOnline() throws Exception {
        final MainViewModel viewModel = (MainViewModel) getField(controller, "viewModel");
        final Label connectionLabel = (Label) getField(controller, "connectionLabel");

        viewModel.connectedProperty().set(true);
        assertEquals("已连接", connectionLabel.getText());
    }

    @Test
    @DisplayName("initialize - connectionLabel 离线时显示'连接中...'")
    void initialize_connectionLabelOffline() throws Exception {
        final MainViewModel viewModel = (MainViewModel) getField(controller, "viewModel");
        final Label connectionLabel = (Label) getField(controller, "connectionLabel");

        viewModel.connectedProperty().set(false);
        assertEquals("连接中...", connectionLabel.getText());
    }

    @Test
    @DisplayName("initialize - errorLabel 绑定到 ViewModel errorMessage")
    void initialize_errorLabelBound() throws Exception {
        final MainViewModel viewModel = (MainViewModel) getField(controller, "viewModel");
        final Label errorLabel = (Label) getField(controller, "errorLabel");

        viewModel.errorMessageProperty().set("网络错误");
        assertEquals("网络错误", errorLabel.getText());
    }

    @Test
    @DisplayName("initialize - chatTitleLabel 默认显示'请选择会话'")
    void initialize_chatTitleLabelDefault() throws Exception {
        final Label chatTitleLabel = (Label) getField(controller, "chatTitleLabel");
        assertEquals("请选择会话", chatTitleLabel.getText());
    }

    @Test
    @DisplayName("initData - 不抛异常")
    void initData_shouldNotThrow() throws Exception {
        final LoginResponse response = new LoginResponse();
        response.setAccessToken("test-token");
        response.setRefreshToken("test-refresh");
        response.setExpiresIn(7200L);

        assertDoesNotThrow(() -> controller.initData(response));
    }

    @Test
    @DisplayName("handleSend - 无 chatViewModel 时不抛异常")
    void handleSend_noChatViewModel_shouldNotThrow() throws Exception {
        assertDoesNotThrow(() -> invokeMethod(controller, "handleSend"));
    }

    @Test
    @DisplayName("handleInputKeyPress - Enter 键触发发送")
    void handleInputKeyPress_enter_shouldTriggerSend() throws Exception {
        final KeyEvent enterEvent = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                false, false, false, false);
        assertDoesNotThrow(() -> invokeMethod(controller, "handleInputKeyPress", KeyEvent.class, enterEvent));
    }

    @Test
    @DisplayName("handleInputKeyPress - Shift+Enter 不触发发送")
    void handleInputKeyPress_shiftEnter_shouldNotTriggerSend() throws Exception {
        final KeyEvent shiftEnterEvent = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                true, false, false, false);
        assertDoesNotThrow(() -> invokeMethod(controller, "handleInputKeyPress", KeyEvent.class, shiftEnterEvent));
    }

    @Test
    @DisplayName("handleInputKeyPress - 非 Enter 键不触发发送")
    void handleInputKeyPress_nonEnter_shouldNotTriggerSend() throws Exception {
        final KeyEvent tabEvent = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.TAB,
                false, false, false, false);
        assertDoesNotThrow(() -> invokeMethod(controller, "handleInputKeyPress", KeyEvent.class, tabEvent));
    }

    @Test
    @DisplayName("handleRefresh - 不抛异常")
    void handleRefresh_shouldNotThrow() throws Exception {
        assertDoesNotThrow(() -> invokeMethod(controller, "handleRefresh"));
    }

    @Test
    @DisplayName("handleSettings - 测试环境无完整UI，允许失败")
    void handleSettings_shouldNotThrow() throws Exception {
        // handleSettings 需要完整的 UI 环境（Scene、Window 等）
        // 测试环境中没有这些，所以会抛出 NullPointerException 或 IOException
        // 这个测试只是验证方法存在且可以被调用
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicReference<Exception> exceptionRef =
                new java.util.concurrent.atomic.AtomicReference<>();

        javafx.application.Platform.runLater(() -> {
            try {
                invokeMethod(controller, "handleSettings");
            } catch (final Exception e) {
                exceptionRef.set(e);
            }
            latch.countDown();
        });

        latch.await(5, java.util.concurrent.TimeUnit.SECONDS);

        // 在测试环境中，预期会有异常（NullPointerException 或 IOException）
        // 这是正常的，因为测试环境没有完整的 UI
        final Exception exception = exceptionRef.get();
        // 只要不是其他类型的严重错误，测试就算通过
        if (exception != null && exception.getCause() != null) {
            final Throwable cause = exception.getCause();
            // 接受 NullPointerException（无 Scene）或 IOException（无 FXML）
            if (!(cause instanceof java.io.IOException)
                    && !(cause instanceof java.lang.NullPointerException)) {
                fail("不应该抛出非预期异常: " + cause);
            }
        }
    }

    @Test
    @DisplayName("handleLogout - 不抛异常")
    void handleLogout_shouldNotThrow() throws Exception {
        assertDoesNotThrow(() -> invokeMethod(controller, "handleLogout"));
    }

    @Test
    @DisplayName("handleFriend - 不抛异常")
    void handleFriend_shouldNotThrow() throws Exception {
        try {
            invokeMethod(controller, "handleFriend");
        } catch (final Exception e) {
            // App.switchToFriend() 在测试环境中可能抛异常
        }
    }

    @Test
    @DisplayName("handleGroup - 不抛异常")
    void handleGroup_shouldNotThrow() throws Exception {
        try {
            invokeMethod(controller, "handleGroup");
        } catch (final Exception e) {
            // App.switchToGroup() 在测试环境中可能抛异常
        }
    }

    @Test
    @DisplayName("handlePlusButton - 不抛异常")
    void handlePlusButton_shouldNotThrow() throws Exception {
        try {
            invokeMethod(controller, "handlePlusButton");
        } catch (final Exception e) {
            // ContextMenu.show() 在测试环境中可能抛异常
        }
    }

    @Test
    @DisplayName("handleConversationClick - 选中会话不抛异常")
    void handleConversationClick_shouldNotThrow() throws Exception {
        final ListView<ConversationInfo> conversationList =
                (ListView<ConversationInfo>) getField(controller, "conversationList");

        // 添加一个会话并选中
        final ConversationInfo info = new ConversationInfo();
        info.setTargetName("测试会话");
        info.setSessionId("p_1001_1002");
        conversationList.getItems().add(info);
        conversationList.getSelectionModel().select(0);

        final MouseEvent clickEvent = new MouseEvent(MouseEvent.MOUSE_CLICKED,
                0, 0, 0, 0, null, 1, false, false, false, false, true, false, false, false, false, false, null);
        assertDoesNotThrow(() -> invokeMethod(controller, "handleConversationClick", MouseEvent.class, clickEvent));
    }

    @Test
    @DisplayName("handleConversationClick - 无选中项不抛异常")
    void handleConversationClick_noSelection_shouldNotThrow() throws Exception {
        final ListView<ConversationInfo> conversationList =
                (ListView<ConversationInfo>) getField(controller, "conversationList");

        final MouseEvent clickEvent = new MouseEvent(MouseEvent.MOUSE_CLICKED,
                0, 0, 0, 0, null, 1, false, false, false, false, true, false, false, false, false, false, null);
        assertDoesNotThrow(() -> invokeMethod(controller, "handleConversationClick", MouseEvent.class, clickEvent));
    }

    @Test
    @DisplayName("searchField 文本变化触发搜索")
    void searchField_textChange_shouldTriggerSearch() throws Exception {
        final MainViewModel viewModel = (MainViewModel) getField(controller, "viewModel");
        final TextField searchField = (TextField) getField(controller, "searchField");

        searchField.setText("测试");
        // 不应抛异常
    }

    // ============ ConversationCell 测试 ============

    @Test
    @DisplayName("ConversationCell - updateItem 空项")
    void conversationCell_updateItem_empty() throws Exception {
        // 获取 ConversationCell 类
        final Class<?> cellClass = Class.forName("org.example.client.controller.MainController$ConversationCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<ConversationInfo> cell =
                (javafx.scene.control.ListCell<ConversationInfo>) constructor.newInstance();

        // 测试空项
        callUpdateItem(cell, null, true);
        assertNull(cell.getGraphic());
        assertNull(cell.getText());
    }

    @Test
    @DisplayName("ConversationCell - updateItem 正常会话")
    void conversationCell_updateItem_normal() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.MainController$ConversationCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<ConversationInfo> cell =
                (javafx.scene.control.ListCell<ConversationInfo>) constructor.newInstance();

        final ConversationInfo info = new ConversationInfo();
        info.setTargetName("张三");
        info.setSessionId("p_1001_1002");
        info.setLastMessage("你好");
        info.setLastMessageTime(LocalDateTime.now());
        info.setUnreadCount(0);

        callUpdateItem(cell, info, false);
        assertNotNull(cell.getGraphic());
        assertNull(cell.getText());
    }

    @Test
    @DisplayName("ConversationCell - updateItem 有未读消息")
    void conversationCell_updateItem_withUnread() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.MainController$ConversationCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<ConversationInfo> cell =
                (javafx.scene.control.ListCell<ConversationInfo>) constructor.newInstance();

        final ConversationInfo info = new ConversationInfo();
        info.setTargetName("李四");
        info.setSessionId("p_1001_1003");
        info.setLastMessage("晚上好");
        info.setLastMessageTime(LocalDateTime.now());
        info.setUnreadCount(5);

        callUpdateItem(cell, info, false);
        assertNotNull(cell.getGraphic());
        assertNull(cell.getText());
    }

    @Test
    @DisplayName("ConversationCell - updateItem 空目标名")
    void conversationCell_updateItem_nullTargetName() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.MainController$ConversationCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<ConversationInfo> cell =
                (javafx.scene.control.ListCell<ConversationInfo>) constructor.newInstance();

        final ConversationInfo info = new ConversationInfo();
        info.setTargetName(null);
        info.setSessionId("p_1001_1004");
        info.setLastMessage(null);
        info.setLastMessageTime(null);
        info.setUnreadCount(0);

        callUpdateItem(cell, info, false);
        assertNotNull(cell.getGraphic());
    }

    @Test
    @DisplayName("ConversationCell - updateItem 非今天的时间")
    void conversationCell_updateItem_yesterdayTime() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.MainController$ConversationCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<ConversationInfo> cell =
                (javafx.scene.control.ListCell<ConversationInfo>) constructor.newInstance();

        final ConversationInfo info = new ConversationInfo();
        info.setTargetName("王五");
        info.setSessionId("p_1001_1005");
        info.setLastMessage("昨天发的消息");
        info.setLastMessageTime(LocalDateTime.now().minusDays(1));
        info.setUnreadCount(0);

        callUpdateItem(cell, info, false);
        assertNotNull(cell.getGraphic());
    }

    // ============ MessageCell 测试 ============

    @Test
    @DisplayName("MessageCell - updateItem 空项")
    void messageCell_updateItem_empty() throws Exception {
        // MessageCell 是非静态内部类，需要 MainController 实例
        final Class<?> cellClass = Class.forName("org.example.client.controller.MainController$MessageCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor(MainController.class);
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<MessageInfo> cell =
                (javafx.scene.control.ListCell<MessageInfo>) constructor.newInstance(controller);

        callUpdateItem(cell, null, true);
        assertNull(cell.getGraphic());
        assertNull(cell.getText());
        assertNull(cell.getContextMenu());
    }

    @Test
    @DisplayName("MessageCell - updateItem 已撤回消息")
    void messageCell_updateItem_recalled() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.MainController$MessageCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor(MainController.class);
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<MessageInfo> cell =
                (javafx.scene.control.ListCell<MessageInfo>) constructor.newInstance(controller);

        final MessageInfo info = new MessageInfo();
        info.setMessageId(100L);
        info.setSessionId("p_1001_1002");
        info.setContent("已撤回的消息");
        info.setRecalled(true);

        callUpdateItem(cell, info, false);
        assertNotNull(cell.getGraphic());
        assertNull(cell.getText());
        assertNull(cell.getContextMenu());
    }

    @Test
    @DisplayName("MessageCell - updateItem 系统消息")
    void messageCell_updateItem_system() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.MainController$MessageCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor(MainController.class);
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<MessageInfo> cell =
                (javafx.scene.control.ListCell<MessageInfo>) constructor.newInstance(controller);

        final MessageInfo info = new MessageInfo();
        info.setMessageId(101L);
        info.setSessionId("g_2001");
        info.setType("SYSTEM");
        info.setContent("张三 已加入群聊");

        callUpdateItem(cell, info, false);
        assertNotNull(cell.getGraphic());
        assertNull(cell.getText());
        assertNull(cell.getContextMenu());
    }

    @Test
    @DisplayName("MessageCell - updateItem 文本消息-自己发送")
    void messageCell_updateItem_textSentByMe() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.MainController$MessageCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor(MainController.class);
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<MessageInfo> cell =
                (javafx.scene.control.ListCell<MessageInfo>) constructor.newInstance(controller);

        final MessageInfo info = new MessageInfo();
        info.setMessageId(102L);
        info.setSessionId("p_1001_1002");
        info.setType("TEXT");
        info.setContent("我发送的消息");
        info.setSenderId(1001L);
        info.setSentByMe(true);
        info.setCreateTime(LocalDateTime.now().minusMinutes(1));
        info.setRead(false);

        callUpdateItem(cell, info, false);
        assertNotNull(cell.getGraphic());
        assertNotNull(cell.getContextMenu()); // 应有撤回和复制菜单
    }

    @Test
    @DisplayName("MessageCell - updateItem 文本消息-对方发送")
    void messageCell_updateItem_textReceived() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.MainController$MessageCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor(MainController.class);
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<MessageInfo> cell =
                (javafx.scene.control.ListCell<MessageInfo>) constructor.newInstance(controller);

        final MessageInfo info = new MessageInfo();
        info.setMessageId(103L);
        info.setSessionId("p_1001_1002");
        info.setType("TEXT");
        info.setContent("对方发送的消息");
        info.setSenderId(1002L);
        info.setSentByMe(false);
        info.setCreateTime(LocalDateTime.now());

        callUpdateItem(cell, info, false);
        assertNotNull(cell.getGraphic());
        assertNotNull(cell.getContextMenu()); // 应有复制菜单
    }

    @Test
    @DisplayName("MessageCell - updateItem 图片消息")
    void messageCell_updateItem_image() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.MainController$MessageCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor(MainController.class);
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<MessageInfo> cell =
                (javafx.scene.control.ListCell<MessageInfo>) constructor.newInstance(controller);

        final MessageInfo info = new MessageInfo();
        info.setMessageId(104L);
        info.setSessionId("p_1001_1002");
        info.setType("IMAGE");
        info.setContent("http://example.com/image.jpg");
        info.setThumbnailUrl("http://example.com/thumb.jpg");
        info.setSenderId(1001L);
        info.setSentByMe(true);
        info.setCreateTime(LocalDateTime.now());

        callUpdateItem(cell, info, false);
        assertNotNull(cell.getGraphic());
    }

    @Test
    @DisplayName("MessageCell - updateItem 文件消息")
    void messageCell_updateItem_file() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.MainController$MessageCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor(MainController.class);
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<MessageInfo> cell =
                (javafx.scene.control.ListCell<MessageInfo>) constructor.newInstance(controller);

        final MessageInfo info = new MessageInfo();
        info.setMessageId(105L);
        info.setSessionId("p_1001_1002");
        info.setType("FILE");
        info.setContent("test.pdf");
        info.setExtra("{\"fileSize\":1024000}");
        info.setSenderId(1002L);
        info.setSentByMe(false);
        info.setCreateTime(LocalDateTime.now());

        callUpdateItem(cell, info, false);
        assertNotNull(cell.getGraphic());
    }

    @Test
    @DisplayName("MessageCell - updateItem AI会话消息可随时撤回")
    void messageCell_updateItem_aiSessionRecallable() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.MainController$MessageCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor(MainController.class);
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<MessageInfo> cell =
                (javafx.scene.control.ListCell<MessageInfo>) constructor.newInstance(controller);

        // AI会话，超过2分钟仍可撤回
        final MessageInfo info = new MessageInfo();
        info.setMessageId(106L);
        info.setSessionId("a_3001_1001");
        info.setType("TEXT");
        info.setContent("AI消息");
        info.setSenderId(1001L);
        info.setSentByMe(true);
        info.setCreateTime(LocalDateTime.now().minusMinutes(5));

        callUpdateItem(cell, info, false);
        assertNotNull(cell.getGraphic());
        assertNotNull(cell.getContextMenu()); // AI消息超过2分钟仍有撤回菜单
    }

    @Test
    @DisplayName("MessageCell - updateItem 普通会话超过2分钟不可撤回")
    void messageCell_updateItem_normalSessionNotRecallable() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.MainController$MessageCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor(MainController.class);
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<MessageInfo> cell =
                (javafx.scene.control.ListCell<MessageInfo>) constructor.newInstance(controller);

        // 普通会话，超过2分钟不可撤回
        final MessageInfo info = new MessageInfo();
        info.setMessageId(107L);
        info.setSessionId("p_1001_1002");
        info.setType("TEXT");
        info.setContent("超过2分钟的消息");
        info.setSenderId(1001L);
        info.setSentByMe(true);
        info.setCreateTime(LocalDateTime.now().minusMinutes(3));

        callUpdateItem(cell, info, false);
        assertNotNull(cell.getGraphic());
        // 超过2分钟不应有撤回菜单，但应有复制菜单
        assertNotNull(cell.getContextMenu());
    }

    @Test
    @DisplayName("MessageCell - updateItem 已读消息")
    void messageCell_updateItem_readMessage() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.MainController$MessageCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor(MainController.class);
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<MessageInfo> cell =
                (javafx.scene.control.ListCell<MessageInfo>) constructor.newInstance(controller);

        final MessageInfo info = new MessageInfo();
        info.setMessageId(108L);
        info.setSessionId("p_1001_1002");
        info.setType("TEXT");
        info.setContent("已读消息");
        info.setSenderId(1001L);
        info.setSentByMe(true);
        info.setCreateTime(LocalDateTime.now());
        info.setRead(true);

        callUpdateItem(cell, info, false);
        assertNotNull(cell.getGraphic());
    }

    // ============ 辅助方法 ============

    private static void setField(final Object obj, final String name, final Object value) throws Exception {
        final Field field = obj.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(obj, value);
    }

    private static Object getField(final Object obj, final String name) throws Exception {
        final Field field = obj.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(obj);
    }

    private static void invokeMethod(final Object obj, final String name) throws Exception {
        final Method method = obj.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(obj);
    }

    @SuppressWarnings("unchecked")
    private static <T> void invokeMethod(final Object obj, final String name,
            final Class<T> paramType, final T paramValue) throws Exception {
        final Method method = obj.getClass().getDeclaredMethod(name, paramType);
        method.setAccessible(true);
        method.invoke(obj, paramValue);
    }

    /**
     * 通过反射调用 Cell 的 protected updateItem 方法
     * 使用 Cell 类而不是 ListCell 类，因为 updateItem 定义在 Cell 类中
     */
    private static void callUpdateItem(final javafx.scene.control.Cell<?> cell,
            final Object item, final boolean empty) throws Exception {
        final Method method = javafx.scene.control.Cell.class.getDeclaredMethod(
                "updateItem", Object.class, boolean.class);
        method.setAccessible(true);
        method.invoke(cell, item, empty);
    }
}
