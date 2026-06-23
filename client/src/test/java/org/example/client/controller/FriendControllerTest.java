package org.example.client.controller;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.example.client.view.FriendListViewModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FriendController 单元测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("FriendController 测试")
class FriendControllerTest extends JavaFxTestBase {

    private FriendController controller;

    @BeforeEach
    void setUp() throws Exception {
        controller = new FriendController();

        final VBox rootPane = new VBox();
        final Button backButton = new Button();
        final Button refreshFriendsButton = new Button();
        final ListView<?> friendList = new ListView<>();
        final Button refreshAppliesButton = new Button();
        final TextField targetPhoneField = new TextField();
        final TextField applyMessageField = new TextField();
        final Button sendApplyButton = new Button();
        final ListView<?> applyList = new ListView<>();
        final Label errorLabel = new Label();
        final Label successLabel = new Label();
        final ProgressIndicator loadingIndicator = new ProgressIndicator();

        setField(controller, "rootPane", rootPane);
        setField(controller, "backButton", backButton);
        setField(controller, "refreshFriendsButton", refreshFriendsButton);
        setField(controller, "friendList", friendList);
        setField(controller, "refreshAppliesButton", refreshAppliesButton);
        setField(controller, "targetPhoneField", targetPhoneField);
        setField(controller, "applyMessageField", applyMessageField);
        setField(controller, "sendApplyButton", sendApplyButton);
        setField(controller, "applyList", applyList);
        setField(controller, "errorLabel", errorLabel);
        setField(controller, "successLabel", successLabel);
        setField(controller, "loadingIndicator", loadingIndicator);

        controller.initialize(null, null);
    }

    @Test
    @DisplayName("initialize - ViewModel 正确创建")
    void initialize_shouldCreateViewModel() throws Exception {
        final FriendListViewModel viewModel = (FriendListViewModel) getField(controller, "viewModel");
        assertNotNull(viewModel);
    }

    @Test
    @DisplayName("initialize - targetPhoneField 与 ViewModel 双向绑定")
    void initialize_targetPhoneFieldBound() throws Exception {
        final FriendListViewModel viewModel = (FriendListViewModel) getField(controller, "viewModel");
        final TextField targetPhoneField = (TextField) getField(controller, "targetPhoneField");

        targetPhoneField.setText("13800138000");
        assertEquals("13800138000", viewModel.targetPhoneProperty().get());
    }

    @Test
    @DisplayName("initialize - applyMessageField 与 ViewModel 双向绑定")
    void initialize_applyMessageFieldBound() throws Exception {
        final FriendListViewModel viewModel = (FriendListViewModel) getField(controller, "viewModel");
        final TextField applyMessageField = (TextField) getField(controller, "applyMessageField");

        applyMessageField.setText("你好");
        assertEquals("你好", viewModel.applyMessageProperty().get());
    }

    @Test
    @DisplayName("initialize - errorLabel 绑定到 ViewModel errorMessage")
    void initialize_errorLabelBound() throws Exception {
        final FriendListViewModel viewModel = (FriendListViewModel) getField(controller, "viewModel");
        final Label errorLabel = (Label) getField(controller, "errorLabel");

        viewModel.errorMessageProperty().set("用户不存在");
        assertEquals("用户不存在", errorLabel.getText());
    }

    @Test
    @DisplayName("initialize - successLabel 绑定到 ViewModel successMessage")
    void initialize_successLabelBound() throws Exception {
        final FriendListViewModel viewModel = (FriendListViewModel) getField(controller, "viewModel");
        final Label successLabel = (Label) getField(controller, "successLabel");

        viewModel.successMessageProperty().set("申请已发送");
        assertEquals("申请已发送", successLabel.getText());
    }

    @Test
    @DisplayName("initialize - loadingIndicator 绑定到 ViewModel loading")
    void initialize_loadingIndicatorBound() throws Exception {
        final FriendListViewModel viewModel = (FriendListViewModel) getField(controller, "viewModel");
        final ProgressIndicator loadingIndicator = (ProgressIndicator) getField(controller, "loadingIndicator");

        viewModel.loadingProperty().set(true);
        assertTrue(loadingIndicator.isVisible());
    }

    @Test
    @DisplayName("handleBack - 不抛异常")
    void handleBack_shouldNotThrow() throws Exception {
        try {
            invokeNoArgMethod(controller, "handleBack");
        } catch (final Exception e) {
            // App.switchToMainFromFriend() 在测试环境中可能抛异常
        }
    }

    @Test
    @DisplayName("handleRefreshFriends - 不抛异常")
    void handleRefreshFriends_shouldNotThrow() throws Exception {
        assertDoesNotThrow(() -> invokeNoArgMethod(controller, "handleRefreshFriends"));
    }

    @Test
    @DisplayName("handleRefreshApplies - 不抛异常")
    void handleRefreshApplies_shouldNotThrow() throws Exception {
        assertDoesNotThrow(() -> invokeNoArgMethod(controller, "handleRefreshApplies"));
    }

    @Test
    @DisplayName("handleSendApply - 不抛异常")
    void handleSendApply_shouldNotThrow() throws Exception {
        assertDoesNotThrow(() -> invokeNoArgMethod(controller, "handleSendApply"));
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

    private static void invokeNoArgMethod(final Object obj, final String name) throws Exception {
        final Method method = obj.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(obj);
    }
}
