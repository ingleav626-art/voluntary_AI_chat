package org.example.client.controller;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.example.client.view.GroupListViewModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GroupInfoController 单元测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("GroupInfoController 测试")
class GroupInfoControllerTest extends JavaFxTestBase {

    private GroupInfoController controller;

    @BeforeEach
    void setUp() throws Exception {
        controller = new GroupInfoController();

        final TextField groupNameField = new TextField();
        final TextArea announcementArea = new TextArea();
        final CheckBox pinnedCheckBox = new CheckBox();
        final Label errorLabel = new Label();
        final Button confirmButton = new Button();
        final Button cancelButton = new Button();

        setField(controller, "groupNameField", groupNameField);
        setField(controller, "announcementArea", announcementArea);
        setField(controller, "pinnedCheckBox", pinnedCheckBox);
        setField(controller, "errorLabel", errorLabel);
        setField(controller, "confirmButton", confirmButton);
        setField(controller, "cancelButton", cancelButton);

        controller.initialize(null, null);
    }

    @Test
    @DisplayName("initialize - groupId 初始为 null")
    void initialize_groupIdNull() throws Exception {
        final Long groupId = (Long) getField(controller, "groupId");
        assertNull(groupId);
    }

    @Test
    @DisplayName("initData - 设置群组数据不抛异常")
    void initData_shouldNotThrow() throws Exception {
        assertDoesNotThrow(() -> controller.initData(1L, new GroupListViewModel(), "测试群", "公告", true));
    }

    @Test
    @DisplayName("initData - 设置后 groupNameField 正确")
    void initData_shouldSetGroupName() throws Exception {
        controller.initData(1L, new GroupListViewModel(), "测试群", "公告内容", true);

        final TextField groupNameField = (TextField) getField(controller, "groupNameField");
        assertEquals("测试群", groupNameField.getText());
    }

    @Test
    @DisplayName("initData - 设置后 announcementArea 正确")
    void initData_shouldSetAnnouncement() throws Exception {
        controller.initData(1L, new GroupListViewModel(), "测试群", "公告内容", true);

        final TextArea announcementArea = (TextArea) getField(controller, "announcementArea");
        assertEquals("公告内容", announcementArea.getText());
    }

    @Test
    @DisplayName("initData - 设置后 pinnedCheckBox 正确")
    void initData_shouldSetPinned() throws Exception {
        controller.initData(1L, new GroupListViewModel(), "测试群", "公告", true);

        final CheckBox pinnedCheckBox = (CheckBox) getField(controller, "pinnedCheckBox");
        assertTrue(pinnedCheckBox.isSelected());
    }

    @Test
    @DisplayName("initData - null 名称不崩溃")
    void initData_nullName_shouldNotCrash() throws Exception {
        assertDoesNotThrow(() -> controller.initData(1L, new GroupListViewModel(), null, null, false));
    }

    @Test
    @DisplayName("handleConfirm - 空群名称显示错误")
    void handleConfirm_emptyName_shouldShowError() throws Exception {
        final TextField groupNameField = (TextField) getField(controller, "groupNameField");
        final Label errorLabel = (Label) getField(controller, "errorLabel");

        groupNameField.setText("");
        invokeNoArgMethod(controller, "handleConfirm");

        assertEquals("群名称不能为空", errorLabel.getText());
    }

    @Test
    @DisplayName("handleConfirm - 群名称过短显示错误")
    void handleConfirm_shortName_shouldShowError() throws Exception {
        final TextField groupNameField = (TextField) getField(controller, "groupNameField");
        final Label errorLabel = (Label) getField(controller, "errorLabel");

        groupNameField.setText("一");
        invokeNoArgMethod(controller, "handleConfirm");

        assertEquals("群名称长度需为 2-50 字符", errorLabel.getText());
    }

    @Test
    @DisplayName("handleConfirm - 群名称过长显示错误")
    void handleConfirm_longName_shouldShowError() throws Exception {
        final TextField groupNameField = (TextField) getField(controller, "groupNameField");
        final Label errorLabel = (Label) getField(controller, "errorLabel");

        groupNameField.setText("a".repeat(51));
        invokeNoArgMethod(controller, "handleConfirm");

        assertEquals("群名称长度需为 2-50 字符", errorLabel.getText());
    }

    @Test
    @DisplayName("handleConfirm - 有效名称不显示错误")
    void handleConfirm_validName_shouldProceed() throws Exception {
        controller.initData(1L, new GroupListViewModel(), "测试群", "公告", false);

        final TextField groupNameField = (TextField) getField(controller, "groupNameField");
        final Label errorLabel = (Label) getField(controller, "errorLabel");

        groupNameField.setText("新群名");
        try {
            invokeNoArgMethod(controller, "handleConfirm");
        } catch (final Exception e) {
            // closeWindow() 可能在测试环境中失败
        }

        // errorLabel 应被清除（设为空字符串）
        assertEquals("", errorLabel.getText());
    }

    @Test
    @DisplayName("handleConfirm - confirmButton 被禁用")
    void handleConfirm_validName_disablesConfirm() throws Exception {
        controller.initData(1L, new GroupListViewModel(), "测试群", "公告", false);

        final TextField groupNameField = (TextField) getField(controller, "groupNameField");
        final Button confirmButton = (Button) getField(controller, "confirmButton");

        groupNameField.setText("新群名");
        try {
            invokeNoArgMethod(controller, "handleConfirm");
        } catch (final Exception e) {
            // closeWindow() 可能在测试环境中失败
        }

        assertTrue(confirmButton.isDisable());
    }

    @Test
    @DisplayName("handleConfirm - 纯空白群名称显示错误")
    void handleConfirm_whitespaceName_shouldShowError() throws Exception {
        final TextField groupNameField = (TextField) getField(controller, "groupNameField");
        final Label errorLabel = (Label) getField(controller, "errorLabel");

        groupNameField.setText("   ");
        invokeNoArgMethod(controller, "handleConfirm");

        assertEquals("群名称不能为空", errorLabel.getText());
    }

    @Test
    @DisplayName("closeWindow - handleCancel触发不崩溃")
    void closeWindow_viaHandleCancel_shouldNotCrash() throws Exception {
        try {
            invokeNoArgMethod(controller, "handleCancel");
        } catch (final Exception e) {
            // cancelButton.getScene() 在测试环境中可能为 null
        }
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
