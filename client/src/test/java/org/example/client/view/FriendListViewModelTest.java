package org.example.client.view;

import org.example.client.model.FriendApplyResponse;
import org.example.client.model.FriendResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FriendListViewModel 单元测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("FriendListViewModel 测试")
class FriendListViewModelTest {

    private FriendListViewModel viewModel;

    @BeforeEach
    void setUp() {
        viewModel = new FriendListViewModel();
    }

    @Test
    @DisplayName("属性初始化")
    void testPropertiesInitialized() {
        assertTrue(viewModel.getFriends().isEmpty());
        assertTrue(viewModel.getApplies().isEmpty());
        assertFalse(viewModel.loadingProperty().get());
        assertEquals("", viewModel.errorMessageProperty().get());
        assertEquals("", viewModel.successMessageProperty().get());
        assertEquals("", viewModel.targetPhoneProperty().get());
        assertEquals("", viewModel.applyMessageProperty().get());
    }

    @Test
    @DisplayName("发送申请-目标手机号为空")
    void testSendApplyEmptyTargetPhone() {
        viewModel.targetPhoneProperty().set("");
        viewModel.sendApply();

        assertEquals("请输入目标用户手机号", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("发送申请-目标手机号格式错误")
    void testSendApplyInvalidTargetPhone() {
        viewModel.targetPhoneProperty().set("abc");
        viewModel.sendApply();

        assertEquals("手机号格式不正确", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("同意申请-空申请ID不操作")
    void testAcceptApplyNullId() {
        viewModel.acceptApply(null);
        // 不应崩溃，列表保持空
        assertTrue(viewModel.getApplies().isEmpty());
    }

    @Test
    @DisplayName("拒绝申请-空申请ID不操作")
    void testRejectApplyNullId() {
        viewModel.rejectApply(null);
        assertTrue(viewModel.getApplies().isEmpty());
    }

    @Test
    @DisplayName("删除好友-空好友ID不操作")
    void testDeleteFriendNullId() {
        viewModel.deleteFriend(null);
        assertTrue(viewModel.getFriends().isEmpty());
    }

    @Test
    @DisplayName("loadFriends 调用不崩溃")
    void testLoadFriends_shouldNotCrash() {
        assertDoesNotThrow(() -> viewModel.loadFriends());
    }

    @Test
    @DisplayName("loadApplies 调用不崩溃")
    void testLoadApplies_shouldNotCrash() {
        assertDoesNotThrow(() -> viewModel.loadApplies());
    }

    @Test
    @DisplayName("acceptApply 非 null ID 调用不崩溃")
    void testAcceptApply_shouldNotCrash() {
        assertDoesNotThrow(() -> viewModel.acceptApply(1L));
    }

    @Test
    @DisplayName("rejectApply 非 null ID 调用不崩溃")
    void testRejectApply_shouldNotCrash() {
        assertDoesNotThrow(() -> viewModel.rejectApply(1L));
    }

    @Test
    @DisplayName("deleteFriend 非 null ID 调用不崩溃")
    void testDeleteFriend_shouldNotCrash() {
        assertDoesNotThrow(() -> viewModel.deleteFriend(1L));
    }

    @Test
    @DisplayName("errorMessage 初始为空")
    void testErrorMessageInitialEmpty() {
        assertEquals("", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("successMessage 初始为空")
    void testSuccessMessageInitialEmpty() {
        assertEquals("", viewModel.successMessageProperty().get());
    }

    @Test
    @DisplayName("手机号格式 - 正确格式通过校验")
    void testSendApplyValidPhone() {
        viewModel.targetPhoneProperty().set("13800138000");
        viewModel.applyMessageProperty().set("你好，交个朋友");

        // 调用 sendApply 不应立即设置 errorMessage（异步执行）
        viewModel.sendApply();

        // 格式正确不应立即报错（异步请求未完成）
        assertNotEquals("手机号格式不正确", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("手机号格式 - 非数字字符格式错误")
    void testSendApplyNonDigitsPhone() {
        viewModel.targetPhoneProperty().set("1234567890a");
        viewModel.sendApply();
        assertEquals("手机号格式不正确", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("手机号格式 - 短手机号格式错误")
    void testSendApplyShortPhone() {
        viewModel.targetPhoneProperty().set("123456789");
        viewModel.sendApply();
        assertEquals("手机号格式不正确", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("手机号格式 - 非1开头格式错误")
    void testSendApplyPhoneNotStartingWith1() {
        viewModel.targetPhoneProperty().set("20000000000");
        viewModel.sendApply();
        assertEquals("手机号格式不正确", viewModel.errorMessageProperty().get());
    }

    @Test
    @DisplayName("好友列表属性可观察")
    void testFriendsObservable() {
        final FriendResponse friend1 = new FriendResponse();
        friend1.setUserId(1002L);
        friend1.setUsername("李四");

        final FriendResponse friend2 = new FriendResponse();
        friend2.setUserId(1003L);
        friend2.setUsername("王五");

        viewModel.getFriends().addAll(friend1, friend2);

        assertEquals(2, viewModel.getFriends().size());
        assertTrue(viewModel.getFriends().contains(friend1));
        assertTrue(viewModel.getFriends().contains(friend2));
    }

    @Test
    @DisplayName("好友申请列表属性可观察")
    void testAppliesObservable() {
        final FriendApplyResponse apply1 = new FriendApplyResponse();
        apply1.setApplyId(1L);
        apply1.setUserId(1002L);
        apply1.setUsername("李四");

        final FriendApplyResponse apply2 = new FriendApplyResponse();
        apply2.setApplyId(2L);
        apply2.setUserId(1003L);
        apply2.setUsername("王五");

        viewModel.getApplies().addAll(apply1, apply2);

        assertEquals(2, viewModel.getApplies().size());
        assertTrue(viewModel.getApplies().contains(apply1));
        assertTrue(viewModel.getApplies().contains(apply2));
    }
}
