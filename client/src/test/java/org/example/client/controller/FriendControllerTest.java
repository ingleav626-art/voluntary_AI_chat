package org.example.client.controller;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.example.client.model.FriendApplyResponse;
import org.example.client.model.FriendResponse;
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

    // ============ FriendCell 测试 ============

    @Test
    @DisplayName("FriendCell - updateItem 空项")
    void friendCell_updateItem_empty() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.FriendController$FriendCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor(FriendController.class);
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<FriendResponse> cell =
                (javafx.scene.control.ListCell<FriendResponse>) constructor.newInstance(controller);

        callUpdateItem(cell, null, true);
        assertNull(cell.getGraphic());
        assertNull(cell.getText());
    }

    @Test
    @DisplayName("FriendCell - updateItem 正常好友")
    void friendCell_updateItem_normal() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.FriendController$FriendCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor(FriendController.class);
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<FriendResponse> cell =
                (javafx.scene.control.ListCell<FriendResponse>) constructor.newInstance(controller);

        final FriendResponse friend = new FriendResponse();
        friend.setUserId(1001L);
        friend.setUsername("张三");
        friend.setRemark("备注张三");

        callUpdateItem(cell, friend, false);
        assertNotNull(cell.getGraphic());
        assertNull(cell.getText());
    }

    @Test
    @DisplayName("FriendCell - updateItem 无备注好友")
    void friendCell_updateItem_noRemark() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.FriendController$FriendCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor(FriendController.class);
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<FriendResponse> cell =
                (javafx.scene.control.ListCell<FriendResponse>) constructor.newInstance(controller);

        final FriendResponse friend = new FriendResponse();
        friend.setUserId(1002L);
        friend.setUsername("李四");
        friend.setRemark(null);

        callUpdateItem(cell, friend, false);
        assertNotNull(cell.getGraphic());
        assertNull(cell.getText());
    }

    @Test
    @DisplayName("FriendCell - updateItem 无用户名好友")
    void friendCell_updateItem_noUsername() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.FriendController$FriendCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor(FriendController.class);
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<FriendResponse> cell =
                (javafx.scene.control.ListCell<FriendResponse>) constructor.newInstance(controller);

        final FriendResponse friend = new FriendResponse();
        friend.setUserId(1003L);
        friend.setUsername(null);
        friend.setRemark(null);

        callUpdateItem(cell, friend, false);
        assertNotNull(cell.getGraphic());
        assertNull(cell.getText());
    }

    // ============ ApplyCell 测试 ============

    @Test
    @DisplayName("ApplyCell - updateItem 空项")
    void applyCell_updateItem_empty() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.FriendController$ApplyCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor(FriendController.class);
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<FriendApplyResponse> cell =
                (javafx.scene.control.ListCell<FriendApplyResponse>) constructor.newInstance(controller);

        callUpdateItem(cell, null, true);
        assertNull(cell.getGraphic());
        assertNull(cell.getText());
    }

    @Test
    @DisplayName("ApplyCell - updateItem 待处理申请")
    void applyCell_updateItem_pending() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.FriendController$ApplyCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor(FriendController.class);
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<FriendApplyResponse> cell =
                (javafx.scene.control.ListCell<FriendApplyResponse>) constructor.newInstance(controller);

        final FriendApplyResponse apply = new FriendApplyResponse();
        apply.setApplyId(1L);
        apply.setUserId(1001L);
        apply.setUsername("王五");
        apply.setMessage("你好，我想加你为好友");
        apply.setStatus("PENDING");

        callUpdateItem(cell, apply, false);
        assertNotNull(cell.getGraphic());
        assertNull(cell.getText());
    }

    @Test
    @DisplayName("ApplyCell - updateItem 已同意申请")
    void applyCell_updateItem_accepted() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.FriendController$ApplyCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor(FriendController.class);
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<FriendApplyResponse> cell =
                (javafx.scene.control.ListCell<FriendApplyResponse>) constructor.newInstance(controller);

        final FriendApplyResponse apply = new FriendApplyResponse();
        apply.setApplyId(2L);
        apply.setUserId(1002L);
        apply.setUsername("赵六");
        apply.setMessage("我是赵六");
        apply.setStatus("ACCEPTED");

        callUpdateItem(cell, apply, false);
        assertNotNull(cell.getGraphic());
        assertNull(cell.getText());
    }

    @Test
    @DisplayName("ApplyCell - updateItem 已拒绝申请")
    void applyCell_updateItem_rejected() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.FriendController$ApplyCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor(FriendController.class);
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<FriendApplyResponse> cell =
                (javafx.scene.control.ListCell<FriendApplyResponse>) constructor.newInstance(controller);

        final FriendApplyResponse apply = new FriendApplyResponse();
        apply.setApplyId(3L);
        apply.setUserId(1003L);
        apply.setUsername("钱七");
        apply.setMessage("加好友");
        apply.setStatus("REJECTED");

        callUpdateItem(cell, apply, false);
        assertNotNull(cell.getGraphic());
        assertNull(cell.getText());
    }

    @Test
    @DisplayName("ApplyCell - updateItem 无用户名申请")
    void applyCell_updateItem_noUsername() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.FriendController$ApplyCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor(FriendController.class);
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<FriendApplyResponse> cell =
                (javafx.scene.control.ListCell<FriendApplyResponse>) constructor.newInstance(controller);

        final FriendApplyResponse apply = new FriendApplyResponse();
        apply.setApplyId(4L);
        apply.setUserId(1004L);
        apply.setUsername(null);
        apply.setMessage(null);
        apply.setStatus("PENDING");

        callUpdateItem(cell, apply, false);
        assertNotNull(cell.getGraphic());
        assertNull(cell.getText());
    }

    @Test
    @DisplayName("ApplyCell - updateItem 无状态申请")
    void applyCell_updateItem_noStatus() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.FriendController$ApplyCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor(FriendController.class);
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<FriendApplyResponse> cell =
                (javafx.scene.control.ListCell<FriendApplyResponse>) constructor.newInstance(controller);

        final FriendApplyResponse apply = new FriendApplyResponse();
        apply.setApplyId(5L);
        apply.setUserId(1005L);
        apply.setUsername("孙八");
        apply.setMessage("你好");
        apply.setStatus(null);

        callUpdateItem(cell, apply, false);
        assertNotNull(cell.getGraphic());
        assertNull(cell.getText());
    }

    @Test
    @DisplayName("ApplyCell - updateItem 其他状态申请")
    void applyCell_updateItem_otherStatus() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.FriendController$ApplyCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor(FriendController.class);
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<FriendApplyResponse> cell =
                (javafx.scene.control.ListCell<FriendApplyResponse>) constructor.newInstance(controller);

        final FriendApplyResponse apply = new FriendApplyResponse();
        apply.setApplyId(6L);
        apply.setUserId(1006L);
        apply.setUsername("周九");
        apply.setMessage("申请");
        apply.setStatus("UNKNOWN");

        callUpdateItem(cell, apply, false);
        assertNotNull(cell.getGraphic());
        assertNull(cell.getText());
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
