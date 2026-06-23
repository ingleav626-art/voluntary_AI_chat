package org.example.client.controller;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.example.client.model.FriendResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GroupCreateController 单元测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("GroupCreateController 测试")
class GroupCreateControllerTest extends JavaFxTestBase {

    private GroupCreateController controller;

    @BeforeEach
    void setUp() throws Exception {
        controller = new GroupCreateController();

        final TextField groupNameField = new TextField();
        final TextField searchFriendField = new TextField();
        final ListView<FriendResponse> friendCheckList = new ListView<>();
        final Label selectedCountLabel = new Label();
        final Label errorLabel = new Label();
        final Button confirmCreateButton = new Button();
        final Button cancelButton = new Button();

        setField(controller, "groupNameField", groupNameField);
        setField(controller, "searchFriendField", searchFriendField);
        setField(controller, "friendCheckList", friendCheckList);
        setField(controller, "selectedCountLabel", selectedCountLabel);
        setField(controller, "errorLabel", errorLabel);
        setField(controller, "confirmCreateButton", confirmCreateButton);
        setField(controller, "cancelButton", cancelButton);

        controller.initialize(null, null);
    }

    @Test
    @DisplayName("initialize - selectedMap 正确初始化")
    void initialize_selectedMapInitialized() throws Exception {
        final Map<Long, Boolean> selectedMap = (Map<Long, Boolean>) getField(controller, "selectedMap");
        assertNotNull(selectedMap);
    }

    @Test
    @DisplayName("initialize - allFriends 正确初始化")
    void initialize_allFriendsInitialized() throws Exception {
        final List<FriendResponse> allFriends = (List<FriendResponse>) getField(controller, "allFriends");
        assertNotNull(allFriends);
    }

    @Test
    @DisplayName("setOnSuccess - 设置回调不抛异常")
    void setOnSuccess_shouldNotThrow() throws Exception {
        assertDoesNotThrow(() -> controller.setOnSuccess(() -> { }));
    }

    @Test
    @DisplayName("handleCreate - 空群名称显示错误")
    void handleCreate_emptyName_shouldShowError() throws Exception {
        final TextField groupNameField = (TextField) getField(controller, "groupNameField");
        final Label errorLabel = (Label) getField(controller, "errorLabel");

        groupNameField.setText("");
        invokeNoArgMethod(controller, "handleCreate");

        assertEquals("请输入群名称", errorLabel.getText());
    }

    @Test
    @DisplayName("handleCreate - 群名称过短显示错误")
    void handleCreate_shortName_shouldShowError() throws Exception {
        final TextField groupNameField = (TextField) getField(controller, "groupNameField");
        final Label errorLabel = (Label) getField(controller, "errorLabel");

        groupNameField.setText("一");
        invokeNoArgMethod(controller, "handleCreate");

        assertEquals("群名称长度需为 2-50 字符", errorLabel.getText());
    }

    @Test
    @DisplayName("handleCreate - 群名称过长显示错误")
    void handleCreate_longName_shouldShowError() throws Exception {
        final TextField groupNameField = (TextField) getField(controller, "groupNameField");
        final Label errorLabel = (Label) getField(controller, "errorLabel");

        groupNameField.setText("a".repeat(51));
        invokeNoArgMethod(controller, "handleCreate");

        assertEquals("群名称长度需为 2-50 字符", errorLabel.getText());
    }

    @Test
    @DisplayName("handleCreate - 有效群名称不显示长度错误")
    void handleCreate_validName_shouldNotShowLengthError() throws Exception {
        final TextField groupNameField = (TextField) getField(controller, "groupNameField");
        final Label errorLabel = (Label) getField(controller, "errorLabel");

        groupNameField.setText("测试群组");
        invokeNoArgMethod(controller, "handleCreate");

        // 不应有长度错误（可能有网络错误，但不是长度错误）
        assertNotEquals("群名称长度需为 2-50 字符", errorLabel.getText());
        assertNotEquals("请输入群名称", errorLabel.getText());
    }

    @Test
    @DisplayName("filterFriends - 空关键词显示全部")
    void filterFriends_emptyKeyword_shouldShowAll() throws Exception {
        final List<FriendResponse> allFriends = new ArrayList<>();
        final FriendResponse friend = new FriendResponse();
        friend.setUsername("张三");
        friend.setUserId(1L);
        allFriends.add(friend);

        setField(controller, "allFriends", allFriends);
        invokeMethod(controller, "filterFriends", String.class, "");

        final ListView<FriendResponse> friendCheckList =
                (ListView<FriendResponse>) getField(controller, "friendCheckList");
        assertEquals(1, friendCheckList.getItems().size());
    }

    @Test
    @DisplayName("filterFriends - null 关键词显示全部")
    void filterFriends_nullKeyword_shouldShowAll() throws Exception {
        invokeMethod(controller, "filterFriends", String.class, null);

        final ListView<FriendResponse> friendCheckList =
                (ListView<FriendResponse>) getField(controller, "friendCheckList");
        assertNotNull(friendCheckList.getItems());
    }

    @Test
    @DisplayName("updateSelectedCount - 无选中显示0人")
    void updateSelectedCount_noSelection_shouldShowZero() throws Exception {
        invokeNoArgMethod(controller, "updateSelectedCount");

        final Label selectedCountLabel = (Label) getField(controller, "selectedCountLabel");
        assertEquals("已选 0 人", selectedCountLabel.getText());
    }

    @Test
    @DisplayName("updateSelectedCount - 有选中显示正确人数")
    void updateSelectedCount_withSelection_shouldShowCorrectCount() throws Exception {
        final Map<Long, Boolean> selectedMap = new HashMap<>();
        selectedMap.put(1L, true);
        selectedMap.put(2L, false);
        selectedMap.put(3L, true);
        setField(controller, "selectedMap", selectedMap);

        invokeNoArgMethod(controller, "updateSelectedCount");

        final Label selectedCountLabel = (Label) getField(controller, "selectedCountLabel");
        assertEquals("已选 2 人", selectedCountLabel.getText());
    }

    @Test
    @DisplayName("searchFriendField 文本变化触发过滤")
    void searchFriendField_textChange_shouldTriggerFilter() throws Exception {
        final TextField searchFriendField = (TextField) getField(controller, "searchFriendField");
        searchFriendField.setText("张");
        // 不应抛异常
    }

    // ============ FriendCheckCell 测试 ============

    @Test
    @DisplayName("FriendCheckCell - updateItem 空项")
    void friendCheckCell_updateItem_empty() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.GroupCreateController$FriendCheckCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor(GroupCreateController.class);
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<FriendResponse> cell =
                (javafx.scene.control.ListCell<FriendResponse>) constructor.newInstance(controller);

        callUpdateItem(cell, null, true);
        assertNull(cell.getGraphic());
        assertNull(cell.getText());
    }

    @Test
    @DisplayName("FriendCheckCell - updateItem 正常好友")
    void friendCheckCell_updateItem_normal() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.GroupCreateController$FriendCheckCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor(GroupCreateController.class);
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<FriendResponse> cell =
                (javafx.scene.control.ListCell<FriendResponse>) constructor.newInstance(controller);

        final FriendResponse friend = new FriendResponse();
        friend.setUserId(1001L);
        friend.setUsername("张三");
        friend.setRemark("备注张三");

        // 初始化选中状态
        final Map<Long, Boolean> selectedMap = (Map<Long, Boolean>) getField(controller, "selectedMap");
        selectedMap.put(1001L, false);

        callUpdateItem(cell, friend, false);
        assertNotNull(cell.getGraphic());
        assertNull(cell.getText());
    }

    @Test
    @DisplayName("FriendCheckCell - updateItem 已选中好友")
    void friendCheckCell_updateItem_selected() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.GroupCreateController$FriendCheckCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor(GroupCreateController.class);
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<FriendResponse> cell =
                (javafx.scene.control.ListCell<FriendResponse>) constructor.newInstance(controller);

        final FriendResponse friend = new FriendResponse();
        friend.setUserId(1002L);
        friend.setUsername("李四");
        friend.setRemark(null);

        // 初始化选中状态为已选中
        final Map<Long, Boolean> selectedMap = (Map<Long, Boolean>) getField(controller, "selectedMap");
        selectedMap.put(1002L, true);

        callUpdateItem(cell, friend, false);
        assertNotNull(cell.getGraphic());
        assertNull(cell.getText());
    }

    @Test
    @DisplayName("FriendCheckCell - updateItem 无用户名好友")
    void friendCheckCell_updateItem_noUsername() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.GroupCreateController$FriendCheckCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor(GroupCreateController.class);
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<FriendResponse> cell =
                (javafx.scene.control.ListCell<FriendResponse>) constructor.newInstance(controller);

        final FriendResponse friend = new FriendResponse();
        friend.setUserId(1003L);
        friend.setUsername(null);
        friend.setRemark(null);

        final Map<Long, Boolean> selectedMap = (Map<Long, Boolean>) getField(controller, "selectedMap");
        selectedMap.put(1003L, false);

        callUpdateItem(cell, friend, false);
        assertNotNull(cell.getGraphic());
        assertNull(cell.getText());
    }

    @Test
    @DisplayName("FriendCheckCell - updateItem 无选中状态好友")
    void friendCheckCell_updateItem_noSelectedState() throws Exception {
        final Class<?> cellClass = Class.forName("org.example.client.controller.GroupCreateController$FriendCheckCell");
        final java.lang.reflect.Constructor<?> constructor = cellClass.getDeclaredConstructor(GroupCreateController.class);
        constructor.setAccessible(true);
        final javafx.scene.control.ListCell<FriendResponse> cell =
                (javafx.scene.control.ListCell<FriendResponse>) constructor.newInstance(controller);

        final FriendResponse friend = new FriendResponse();
        friend.setUserId(1004L);
        friend.setUsername("王五");
        friend.setRemark("备注");

        // 不设置选中状态（selectedMap 中没有该用户）
        callUpdateItem(cell, friend, false);
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
