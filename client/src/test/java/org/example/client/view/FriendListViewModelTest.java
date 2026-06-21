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
}
