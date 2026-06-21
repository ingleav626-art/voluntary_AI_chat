package org.example.client.view;

import java.util.List;

import org.example.client.model.FriendApplyRequest;
import org.example.client.model.FriendApplyResponse;
import org.example.client.model.FriendResponse;
import org.example.client.service.FriendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * 好友列表视图模型（MVVM）
 *
 * <p>管理好友列表、好友申请列表、发送申请和处理申请。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class FriendListViewModel {

    private static final Logger LOG = LoggerFactory.getLogger(FriendListViewModel.class);

    /** 好友列表 */
    private final ListProperty<FriendResponse> friends =
            new SimpleListProperty<>(FXCollections.observableArrayList());

    /** 好友申请列表 */
    private final ListProperty<FriendApplyResponse> applies =
            new SimpleListProperty<>(FXCollections.observableArrayList());

    /** 加载状态 */
    private final BooleanProperty loading = new SimpleBooleanProperty(false);

    /** 错误消息 */
    private final StringProperty errorMessage = new SimpleStringProperty("");

    /** 成功消息 */
    private final StringProperty successMessage = new SimpleStringProperty("");

    /** 添加好友目标手机号输入 */
    private final StringProperty targetPhone = new SimpleStringProperty("");

    /** 添加好友留言输入 */
    private final StringProperty applyMessage = new SimpleStringProperty("");

    public FriendListViewModel() {
        // 初始化
    }

    /**
     * 加载好友列表
     */
    public void loadFriends() {
        loading.set(true);
        errorMessage.set("");

        FriendService.getInstance().getFriendList()
                .thenAccept(response -> Platform.runLater(() -> {
                    loading.set(false);

                    LOG.info("好友列表响应: code={}, message={}, data={}",
                            response == null ? "null" : response.getCode(),
                            response == null ? "null" : response.getMessage(),
                            response == null ? "null" : response.getData());

                    if (response != null && response.isSuccess() && response.getData() != null) {
                        final List<FriendResponse> list = response.getData();
                        friends.setAll(list);
                        LOG.info("好友列表加载成功: count={}", list.size());
                    } else {
                        final String msg = response != null ? response.getMessage() : "加载好友列表失败";
                        errorMessage.set(msg);
                        LOG.warn("好友列表加载失败: {}", msg);
                    }
                }));
    }

    /**
     * 加载好友申请列表
     */
    public void loadApplies() {
        loading.set(true);
        errorMessage.set("");

        FriendService.getInstance().getApplyList()
                .thenAccept(response -> Platform.runLater(() -> {
                    loading.set(false);

                    if (response != null && response.isSuccess() && response.getData() != null) {
                        final List<FriendApplyResponse> list = response.getData();
                        applies.setAll(list);
                        LOG.info("好友申请列表加载成功: count={}", list.size());
                    } else {
                        final String msg = response != null ? response.getMessage() : "加载申请列表失败";
                        errorMessage.set(msg);
                        LOG.warn("好友申请列表加载失败: {}", msg);
                    }
                }));
    }

    /**
     * 发送好友申请
     */
    public void sendApply() {
        final String phone = targetPhone.get();
        if (phone == null || phone.trim().isEmpty()) {
            errorMessage.set("请输入目标用户手机号");
            return;
        }

        // 简单校验手机号格式
        if (!phone.trim().matches("^1[3-9]\\d{9}$")) {
            errorMessage.set("手机号格式不正确");
            return;
        }

        final FriendApplyRequest request = new FriendApplyRequest();
        request.setTargetPhone(phone.trim());
        request.setMessage(applyMessage.get());

        loading.set(true);
        errorMessage.set("");

        FriendService.getInstance().applyFriend(request)
                .thenAccept(response -> Platform.runLater(() -> {
                    loading.set(false);

                    if (response != null && response.isSuccess()) {
                        successMessage.set("申请已发送");
                        targetPhone.set("");
                        applyMessage.set("");
                        LOG.info("好友申请已发送: targetPhone={}", phone);
                    } else {
                        final String msg = response != null ? response.getMessage() : "发送申请失败";
                        errorMessage.set(msg);
                        LOG.warn("好友申请发送失败: {}", msg);
                    }
                }));
    }

    /**
     * 同意好友申请
     *
     * @param applyId 申请ID
     */
    public void acceptApply(final Long applyId) {
        if (applyId == null) {
            return;
        }

        loading.set(true);
        errorMessage.set("");

        FriendService.getInstance().acceptApply(applyId)
                .thenAccept(response -> Platform.runLater(() -> {
                    loading.set(false);

                    if (response != null && response.isSuccess()) {
                        successMessage.set("已同意好友申请");
                        // 从申请列表中移除
                        applies.removeIf(apply -> applyId.equals(apply.getApplyId()));
                        // 刷新好友列表
                        loadFriends();
                        LOG.info("已同意好友申请: applyId={}", applyId);
                    } else {
                        final String msg = response != null ? response.getMessage() : "操作失败";
                        errorMessage.set(msg);
                        LOG.warn("同意好友申请失败: {}", msg);
                    }
                }));
    }

    /**
     * 拒绝好友申请
     *
     * @param applyId 申请ID
     */
    public void rejectApply(final Long applyId) {
        if (applyId == null) {
            return;
        }

        loading.set(true);
        errorMessage.set("");

        FriendService.getInstance().rejectApply(applyId)
                .thenAccept(response -> Platform.runLater(() -> {
                    loading.set(false);

                    if (response != null && response.isSuccess()) {
                        successMessage.set("已拒绝好友申请");
                        applies.removeIf(apply -> applyId.equals(apply.getApplyId()));
                        LOG.info("已拒绝好友申请: applyId={}", applyId);
                    } else {
                        final String msg = response != null ? response.getMessage() : "操作失败";
                        errorMessage.set(msg);
                        LOG.warn("拒绝好友申请失败: {}", msg);
                    }
                }));
    }

    /**
     * 删除好友
     *
     * @param friendId 好友ID
     */
    public void deleteFriend(final Long friendId) {
        if (friendId == null) {
            return;
        }

        loading.set(true);
        errorMessage.set("");

        FriendService.getInstance().deleteFriend(friendId)
                .thenAccept(response -> Platform.runLater(() -> {
                    loading.set(false);

                    if (response != null && response.isSuccess()) {
                        successMessage.set("已删除好友");
                        friends.removeIf(friend -> friendId.equals(friend.getUserId()));
                        LOG.info("已删除好友: friendId={}", friendId);
                    } else {
                        final String msg = response != null ? response.getMessage() : "删除好友失败";
                        errorMessage.set(msg);
                        LOG.warn("删除好友失败: {}", msg);
                    }
                }));
    }

    // Property getters
    public ListProperty<FriendResponse> friendsProperty() {
        return friends;
    }

    public ObservableList<FriendResponse> getFriends() {
        return friends.get();
    }

    public ListProperty<FriendApplyResponse> appliesProperty() {
        return applies;
    }

    public ObservableList<FriendApplyResponse> getApplies() {
        return applies.get();
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public StringProperty successMessageProperty() {
        return successMessage;
    }

    public StringProperty targetPhoneProperty() {
        return targetPhone;
    }

    public StringProperty applyMessageProperty() {
        return applyMessage;
    }
}
