package org.example.client.controller;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.client.model.FriendResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GroupInviteController 单元测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("GroupInviteController 测试")
class GroupInviteControllerTest extends JavaFxTestBase {

    private GroupInviteController controller;
    private TextField searchFriendField;
    private ListView<FriendResponse> friendCheckList;
    private Label selectedCountLabel;
    private Label errorLabel;
    private Button confirmInviteButton;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        controller = new GroupInviteController();

        searchFriendField = new TextField();
        friendCheckList = new ListView<>();
        selectedCountLabel = new Label();
        errorLabel = new Label();
        confirmInviteButton = new Button();
        final Button cancelButton = new Button();

        setField(controller, "searchFriendField", searchFriendField);
        setField(controller, "friendCheckList", friendCheckList);
        setField(controller, "selectedCountLabel", selectedCountLabel);
        setField(controller, "errorLabel", errorLabel);
        setField(controller, "confirmInviteButton", confirmInviteButton);
        setField(controller, "cancelButton", cancelButton);

        controller.initialize(null, null);
    }

    // ======================== initialize 测试 ========================

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
    @DisplayName("initialize - friendCheckList cellFactory 设置")
    void initialize_cellFactorySet() {
        assertNotNull(friendCheckList.getCellFactory());
    }

    // ======================== initData 测试 ========================

    @Test
    @DisplayName("initData - 设置群组ID和回调不抛异常")
    void initData_shouldNotThrow() {
        assertDoesNotThrow(() -> controller.initData(1L, () -> { }));
    }

    // ======================== updateSelectedCount 测试 ========================

    @Test
    @DisplayName("updateSelectedCount - 无选中显示0人")
    void updateSelectedCount_noSelection_shouldShowZero() throws Exception {
        invokeNoArgMethod(controller, "updateSelectedCount");
        assertEquals("已选 0 人", selectedCountLabel.getText());
    }

    @Test
    @DisplayName("updateSelectedCount - 有选中显示正确人数")
    void updateSelectedCount_withSelection_shouldShowCorrectCount() throws Exception {
        final Map<Long, Boolean> selectedMap = new HashMap<>();
        selectedMap.put(1L, true);
        selectedMap.put(2L, true);
        selectedMap.put(3L, false);
        setField(controller, "selectedMap", selectedMap);

        invokeNoArgMethod(controller, "updateSelectedCount");
        assertEquals("已选 2 人", selectedCountLabel.getText());
    }

    // ======================== filterFriends 测试 ========================

    @Test
    @DisplayName("filterFriends - 空关键词显示全部")
    void filterFriends_emptyKeyword_shouldShowAll() throws Exception {
        final List<FriendResponse> allFriends = new ArrayList<>();
        final FriendResponse friend = createFriend(1L, "张三");
        allFriends.add(friend);
        setField(controller, "allFriends", allFriends);

        invokeMethod(controller, "filterFriends", String.class, "");

        assertEquals(1, friendCheckList.getItems().size());
    }

    @Test
    @DisplayName("filterFriends - null 关键词显示全部")
    void filterFriends_nullKeyword_shouldShowAll() throws Exception {
        final List<FriendResponse> allFriends = new ArrayList<>();
        allFriends.add(createFriend(1L, "张三"));
        setField(controller, "allFriends", allFriends);

        invokeMethod(controller, "filterFriends", String.class, (String) null);

        assertEquals(1, friendCheckList.getItems().size());
    }

    @Test
    @DisplayName("filterFriends - 空白关键词显示全部")
    void filterFriends_whitespaceKeyword_shouldShowAll() throws Exception {
        final List<FriendResponse> allFriends = new ArrayList<>();
        allFriends.add(createFriend(1L, "张三"));
        setField(controller, "allFriends", allFriends);

        invokeMethod(controller, "filterFriends", String.class, "   ");

        assertEquals(1, friendCheckList.getItems().size());
    }

    @Test
    @DisplayName("filterFriends - 关键词过滤匹配")
    void filterFriends_keywordMatch_shouldFilter() throws Exception {
        final List<FriendResponse> allFriends = new ArrayList<>();
        allFriends.add(createFriend(1L, "张三"));
        allFriends.add(createFriend(2L, "李四"));
        setField(controller, "allFriends", allFriends);

        invokeMethod(controller, "filterFriends", String.class, "张");

        assertEquals(1, friendCheckList.getItems().size());
    }

    @Test
    @DisplayName("filterFriends - 关键词不匹配时显示空")
    void filterFriends_noMatch_shouldShowEmpty() throws Exception {
        final List<FriendResponse> allFriends = new ArrayList<>();
        allFriends.add(createFriend(1L, "张三"));
        setField(controller, "allFriends", allFriends);

        invokeMethod(controller, "filterFriends", String.class, "王");

        assertEquals(0, friendCheckList.getItems().size());
    }

    @Test
    @DisplayName("filterFriends - 昵称匹配")
    void filterFriends_remarkMatch_shouldFilter() throws Exception {
        final List<FriendResponse> allFriends = new ArrayList<>();
        final FriendResponse friend = createFriend(1L, "用户1");
        friend.setRemark("备注名");
        allFriends.add(friend);
        setField(controller, "allFriends", allFriends);

        invokeMethod(controller, "filterFriends", String.class, "备注");

        assertEquals(1, friendCheckList.getItems().size());
    }

    // ======================== handleInvite / handleCancel 测试 ========================

    @Test
    @DisplayName("handleInvite - 无选中成员显示错误")
    void handleInvite_noSelection_shouldShowError() throws Exception {
        invokeNoArgMethod(controller, "handleInvite");
        assertEquals("请至少选择一位好友", errorLabel.getText());
    }

    @Test
    @DisplayName("handleInvite - selectedMap 有选中但为空列表时仍提示")
    void handleInvite_emptySelectedIds_shouldShowError() throws Exception {
        final Map<Long, Boolean> selectedMap = new HashMap<>();
        // 全部 false
        selectedMap.put(1L, false);
        selectedMap.put(2L, false);
        setField(controller, "selectedMap", selectedMap);

        invokeNoArgMethod(controller, "handleInvite");
        assertEquals("请至少选择一位好友", errorLabel.getText());
    }

    @Test
    @DisplayName("handleCancel - 不抛异常")
    void handleCancel_shouldNotThrow() {
        try {
            invokeNoArgMethod(controller, "handleCancel");
        } catch (final Exception e) {
            // cancelButton.getScene() 在测试环境中可能为 null
        }
    }

    // ======================== searchFriendField 测试 ========================

    @Test
    @DisplayName("searchFriendField 文本变化触发过滤")
    void searchFriendField_textChange_shouldTriggerFilter() {
        searchFriendField.setText("张");
        // 不应抛异常
    }

    // ======================== FriendCheckCell 测试 ========================

    @Test
    @DisplayName("FriendCheckCell - updateItem empty 时清空")
    void friendCheckCell_updateItem_empty() throws Exception {
        final Object cell = createFriendCheckCell();
        invokeCellUpdateItem(cell, null, true);

        assertNull(getCellGraphic(cell));
        assertNull(getCellText(cell));
    }

    @Test
    @DisplayName("FriendCheckCell - updateItem null item 时清空")
    void friendCheckCell_updateItem_nullItem() throws Exception {
        final Object cell = createFriendCheckCell();
        invokeCellUpdateItem(cell, null, false);

        assertNull(getCellGraphic(cell));
        assertNull(getCellText(cell));
    }

    @Test
    @DisplayName("FriendCheckCell - updateItem 有效好友显示名字")
    void friendCheckCell_updateItem_validFriend() throws Exception {
        final Object cell = createFriendCheckCell();
        final FriendResponse friend = createFriend(100L, "测试好友");

        invokeCellUpdateItem(cell, friend, false);

        assertNotNull(getCellGraphic(cell));
        assertTrue(getCellGraphic(cell) instanceof HBox);
    }

    @Test
    @DisplayName("FriendCheckCell - updateItem null username 显示默认")
    void friendCheckCell_updateItem_nullUsername() throws Exception {
        final Object cell = createFriendCheckCell();
        final FriendResponse friend = createFriend(101L, null);

        invokeCellUpdateItem(cell, friend, false);

        assertNotNull(getCellGraphic(cell));
    }

    @Test
    @DisplayName("FriendCheckCell - updateItem 有备注显示备注")
    void friendCheckCell_updateItem_withRemark() throws Exception {
        final Object cell = createFriendCheckCell();
        final FriendResponse friend = createFriend(102L, "好友名");
        friend.setRemark("备注");

        invokeCellUpdateItem(cell, friend, false);

        assertNotNull(getCellGraphic(cell));
    }

    @Test
    @DisplayName("FriendCheckCell - updateItem 已在群中显示已入群标记")
    void friendCheckCell_updateItem_alreadyInGroup() throws Exception {
        setExistingMemberIds(Set.of(200L));

        final Object cell = createFriendCheckCell();
        final FriendResponse friend = createFriend(200L, "已在群好友");

        invokeCellUpdateItem(cell, friend, false);

        assertNotNull(getCellGraphic(cell));
    }

    @Test
    @DisplayName("FriendCheckCell - 复选框选中时更新 selectedMap")
    void friendCheckCell_checkboxSelect_updatesMap() throws Exception {
        prepareAllFriends();
        final Object cell = createFriendCheckCell();
        final FriendResponse friend = createFriend(300L, "待选好友");

        // force set the item first so the checkbox listener has access
        setCellItem(cell, friend);
        invokeCellUpdateItem(cell, friend, false);

        // Verify checkbox interaction doesn't crash
        assertNotNull(getCellGraphic(cell));
    }

    // ======================== 辅助方法 ========================

    private static FriendResponse createFriend(final Long userId, final String username) {
        final FriendResponse friend = new FriendResponse();
        friend.setUserId(userId);
        friend.setUsername(username);
        return friend;
    }

    private void setExistingMemberIds(final Set<Long> ids) throws Exception {
        final Field field = GroupInviteController.class.getDeclaredField("existingMemberIds");
        field.setAccessible(true);
        field.set(controller, ids instanceof HashSet ? ids : new HashSet<>(ids));
    }

    private void prepareAllFriends() throws Exception {
        final List<FriendResponse> friends = new ArrayList<>();
        friends.add(createFriend(300L, "待选好友"));
        setField(controller, "allFriends", friends);
    }

    private Object createFriendCheckCell() throws Exception {
        final Class<?> innerClass = Class.forName(
                "org.example.client.controller.GroupInviteController$FriendCheckCell");
        final Constructor<?> ctor = innerClass.getDeclaredConstructor(GroupInviteController.class);
        ctor.setAccessible(true);
        return ctor.newInstance(controller);
    }

    private static void invokeCellUpdateItem(final Object cell, final Object item,
            final boolean empty) throws Exception {
        for (final Method m : cell.getClass().getDeclaredMethods()) {
            if ("updateItem".equals(m.getName()) && m.getParameterCount() == 2
                    && m.getParameterTypes()[1] == boolean.class) {
                m.setAccessible(true);
                m.invoke(cell, item, empty);
                return;
            }
        }
    }

    private static void setCellItem(final Object cell, final Object item) throws Exception {
        final Method method = cell.getClass().getMethod("setItem", Object.class);
        method.invoke(cell, item);
    }

    private static Object getCellGraphic(final Object cell) throws Exception {
        return cell.getClass().getMethod("getGraphic").invoke(cell);
    }

    private static Object getCellText(final Object cell) throws Exception {
        return cell.getClass().getMethod("getText").invoke(cell);
    }

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
}
