package org.example.client.controller;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import org.example.client.model.FriendResponse;
import org.example.client.model.GroupMemberInfo;
import org.example.client.service.FriendService;
import org.example.client.service.GroupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 邀请成员对话框控制器
 *
 * <p>提供从好友列表勾选成员并邀请加入群组的功能。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class GroupInviteController implements Initializable {

    private static final Logger LOG = LoggerFactory.getLogger(GroupInviteController.class);

    /** 邀请成功回调 */
    private Runnable onSuccess;

    /** 群组ID */
    private Long groupId;

    @FXML
    private TextField searchFriendField;

    @FXML
    private ListView<FriendResponse> friendCheckList;

    @FXML
    private Label selectedCountLabel;

    @FXML
    private Label errorLabel;

    @FXML
    private Button confirmInviteButton;

    @FXML
    private Button cancelButton;

    /** 存储好友的选中状态 */
    private final java.util.Map<Long, Boolean> selectedMap = new java.util.HashMap<>();

    /** 好友列表完整数据 */
    private List<FriendResponse> allFriends = new ArrayList<>();

    /** 已在群中的用户ID集合 */
    private Set<Long> existingMemberIds = Set.of();

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        // 好友列表 Cell（带复选框）
        friendCheckList.setCellFactory(param -> new FriendCheckCell());

        // 搜索好友过滤
        searchFriendField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterFriends(newVal);
        });

        // 加载好友列表
        loadFriends();

        LOG.info("邀请成员对话框控制器初始化完成");
    }

    /**
     * 设置群组ID和成功回调
     *
     * @param groupId   群组ID
     * @param onSuccess 成功回调
     */
    public void initData(final Long groupId, final Runnable onSuccess) {
        this.groupId = groupId;
        this.onSuccess = onSuccess;
        // 加载当前群成员，用于标记已在群中的好友
        loadExistingMembers();
    }

    /**
     * 加载当前群成员列表，记录已在群中的用户ID
     */
    private void loadExistingMembers() {
        if (groupId == null) {
            return;
        }

        GroupService.getInstance().getGroupMembers(groupId, 1, 100)
                .thenAccept(response -> Platform.runLater(() -> {
                    if (response != null && response.isSuccess() && response.getData() != null) {
                        final List<GroupMemberInfo> members = response.getData().getList();
                        existingMemberIds = members.stream()
                                .map(GroupMemberInfo::getUserId)
                                .collect(Collectors.toSet());
                        // 刷新好友列表以更新"已在群"标记
                        refreshFriendListDisplay();
                        LOG.info("已加载群成员: groupId={}, count={}", groupId, existingMemberIds.size());
                    }
                }));
    }

    /**
     * 刷新好友列表显示（更新"已在群"标记）
     */
    private void refreshFriendListDisplay() {
        friendCheckList.getItems().setAll(allFriends);
    }

    /**
     * 加载好友列表
     */
    private void loadFriends() {
        LOG.info("加载好友列表用于邀请成员");

        FriendService.getInstance().getFriendList()
                .thenAccept(response -> Platform.runLater(() -> {
                    if (response != null && response.isSuccess() && response.getData() != null) {
                        allFriends = response.getData();
                        for (final FriendResponse friend : allFriends) {
                            selectedMap.put(friend.getUserId(), false);
                        }
                        friendCheckList.getItems().setAll(allFriends);
                        updateSelectedCount();
                        LOG.info("加载好友列表成功: count={}", allFriends.size());
                    } else {
                        final String msg = response != null ? response.getMessage() : "加载好友列表失败";
                        errorLabel.setText(msg);
                        LOG.warn("加载好友列表失败: {}", msg);
                    }
                }));
    }

    /**
     * 搜索过滤好友
     */
    private void filterFriends(final String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            friendCheckList.getItems().setAll(allFriends);
            return;
        }
        final String lower = keyword.trim().toLowerCase();
        final List<FriendResponse> filtered = allFriends.stream()
                .filter(f -> f.getUsername() != null && f.getUsername().toLowerCase().contains(lower)
                        || (f.getRemark() != null && f.getRemark().toLowerCase().contains(lower)))
                .collect(Collectors.toList());
        friendCheckList.getItems().setAll(filtered);
    }

    /**
     * 更新已选人数
     */
    private void updateSelectedCount() {
        final long count = selectedMap.values().stream().filter(Boolean::booleanValue).count();
        selectedCountLabel.setText("已选 " + count + " 人");
    }

    /**
     * 处理邀请
     */
    @FXML
    private void handleInvite() {
        // 分离已入群和未入群的好友
        final List<Long> alreadyInGroup = selectedMap.entrySet().stream()
                .filter(java.util.Map.Entry::getValue)
                .map(java.util.Map.Entry::getKey)
                .filter(userId -> existingMemberIds.contains(userId))
                .collect(Collectors.toList());

        final List<Long> memberIds = selectedMap.entrySet().stream()
                .filter(java.util.Map.Entry::getValue)
                .map(java.util.Map.Entry::getKey)
                .filter(userId -> !existingMemberIds.contains(userId))
                .collect(Collectors.toList());

        // 提示已入群的用户
        if (!alreadyInGroup.isEmpty()) {
            final String names = alreadyInGroup.stream()
                    .map(id -> allFriends.stream()
                            .filter(f -> f.getUserId().equals(id))
                            .map(f -> f.getUsername() != null ? f.getUsername() : String.valueOf(id))
                            .findFirst()
                            .orElse(String.valueOf(id)))
                    .collect(Collectors.joining("、"));
            errorLabel.setText(names + " 已在群聊中，已自动过滤");
        }

        if (memberIds.isEmpty()) {
            if (alreadyInGroup.isEmpty()) {
                errorLabel.setText("请至少选择一位好友");
            }
            return;
        }

        confirmInviteButton.setDisable(true);
        errorLabel.setText("");

        LOG.info("邀请群成员: groupId={}, inviteCount={}", groupId, memberIds.size());

        GroupService.getInstance().inviteMembers(groupId, memberIds)
                .thenAccept(response -> Platform.runLater(() -> {
                    confirmInviteButton.setDisable(false);

                    if (response != null && response.isSuccess()) {
                        LOG.info("邀请成员成功: groupId={}", groupId);
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                        closeWindow();
                    } else {
                        final String msg = response != null ? response.getMessage() : "邀请成员失败";
                        errorLabel.setText(msg);
                        LOG.warn("邀请成员失败: {}", msg);
                    }
                }));
    }

    /**
     * 处理取消
     */
    @FXML
    private void handleCancel() {
        closeWindow();
    }

    /**
     * 关闭窗口
     */
    private void closeWindow() {
        final Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }

    /**
     * 好友列表复选框 Cell
     */
    private final class FriendCheckCell extends ListCell<FriendResponse> {

        private final CheckBox checkBox = new CheckBox();
        private final Label nameLabel = new Label();
        private final Label remarkLabel = new Label();
        private final HBox cell;

        FriendCheckCell() {
            cell = new HBox(10);
            cell.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            cell.getStyleClass().add("friend-check-cell");

            final VBox info = new VBox(2);
            nameLabel.getStyleClass().add("friend-check-name");
            remarkLabel.getStyleClass().add("friend-check-remark");
            info.getChildren().addAll(nameLabel, remarkLabel);

            final Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            cell.getChildren().addAll(checkBox, info, spacer);

            checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
                final FriendResponse item = getItem();
                if (item != null) {
                    selectedMap.put(item.getUserId(), newVal);
                    updateSelectedCount();
                }
            });
        }

        @Override
        protected void updateItem(final FriendResponse item, final boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            nameLabel.setText(item.getUsername() != null ? item.getUsername() : "未知");
            remarkLabel.setText(item.getRemark() != null ? "备注: " + item.getRemark() : "");

            // 判断是否已在群中
            final boolean inGroup = existingMemberIds.contains(item.getUserId());
            if (inGroup) {
                remarkLabel.setText("已在群聊中");
                remarkLabel.setTextFill(Color.GRAY);
                checkBox.setDisable(true);
                checkBox.setSelected(false);
                selectedMap.put(item.getUserId(), false);
            } else {
                // 恢复选中状态
                final Boolean selected = selectedMap.get(item.getUserId());
                checkBox.setSelected(selected != null && selected);
                checkBox.setDisable(false);
                remarkLabel.setTextFill(Color.BLACK);
            }

            setGraphic(cell);
            setText(null);
        }
    }
}
