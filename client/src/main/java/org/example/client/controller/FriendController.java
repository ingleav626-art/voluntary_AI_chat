package org.example.client.controller;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.example.client.model.FriendApplyResponse;
import org.example.client.model.FriendResponse;
import org.example.client.view.FriendListViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 好友面板控制器
 *
 * <p>负责好友列表展示、好友申请管理、添加好友和删除好友。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class FriendController implements Initializable {

    private static final Logger LOG = LoggerFactory.getLogger(FriendController.class);

    @FXML
    private VBox rootPane;

    @FXML
    private Button backButton;

    @FXML
    private Button refreshFriendsButton;

    @FXML
    private ListView<FriendResponse> friendList;

    @FXML
    private Button refreshAppliesButton;

    @FXML
    private TextField targetPhoneField;

    @FXML
    private TextField applyMessageField;

    @FXML
    private Button sendApplyButton;

    @FXML
    private ListView<FriendApplyResponse> applyList;

    @FXML
    private Label errorLabel;

    @FXML
    private Label successLabel;

    @FXML
    private ProgressIndicator loadingIndicator;

    private FriendListViewModel viewModel;

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        viewModel = new FriendListViewModel();

        // 绑定好友列表
        friendList.itemsProperty().bind(viewModel.friendsProperty());
        friendList.setCellFactory(param -> new FriendCell());

        // 绑定申请列表
        applyList.itemsProperty().bind(viewModel.appliesProperty());
        applyList.setCellFactory(param -> new ApplyCell());

        // 绑定输入框
        targetPhoneField.textProperty().bindBidirectional(viewModel.targetPhoneProperty());
        applyMessageField.textProperty().bindBidirectional(viewModel.applyMessageProperty());

        // 绑定状态消息
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        successLabel.textProperty().bind(viewModel.successMessageProperty());

        // 绑定加载状态
        loadingIndicator.visibleProperty().bind(viewModel.loadingProperty());

        // 自动清除成功消息
        viewModel.successMessageProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    Platform.runLater(() -> viewModel.successMessageProperty().set(""));
                }).start();
            }
        });

        // 加载初始数据
        viewModel.loadFriends();
        viewModel.loadApplies();

        LOG.info("好友面板控制器初始化完成");
    }

    /**
     * 处理返回
     */
    @FXML
    private void handleBack() {
        LOG.info("返回主界面");
        org.example.client.App.switchToMainFromFriend();
    }

    /**
     * 刷新好友列表
     */
    @FXML
    private void handleRefreshFriends() {
        LOG.info("刷新好友列表");
        viewModel.loadFriends();
    }

    /**
     * 刷新申请列表
     */
    @FXML
    private void handleRefreshApplies() {
        LOG.info("刷新申请列表");
        viewModel.loadApplies();
    }

    /**
     * 发送好友申请
     */
    @FXML
    private void handleSendApply() {
        viewModel.sendApply();
    }

    /**
     * 好友列表 Cell
     */
    private final class FriendCell extends ListCell<FriendResponse> {

        @Override
        protected void updateItem(final FriendResponse item, final boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            final HBox cell = new HBox(10);
            cell.getStyleClass().add("friend-cell");
            cell.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            final VBox info = new VBox(2);
            info.getStyleClass().add("friend-info");

            final Label name = new Label(item.getUsername() != null ? item.getUsername() : "未知");
            name.getStyleClass().add("friend-name");

            final Label remark = new Label(item.getRemark() != null ? "备注: " + item.getRemark() : "");
            remark.getStyleClass().add("friend-remark");

            info.getChildren().addAll(name, remark);

            final Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            final Button deleteBtn = new Button("删除");
            deleteBtn.getStyleClass().add("btn-danger-sm");
            deleteBtn.setOnAction(e -> {
                viewModel.deleteFriend(item.getUserId());
            });

            cell.getChildren().addAll(info, spacer, deleteBtn);
            setGraphic(cell);
            setText(null);
        }
    }

    /**
     * 好友申请 Cell
     */
    private final class ApplyCell extends ListCell<FriendApplyResponse> {

        @Override
        protected void updateItem(final FriendApplyResponse item, final boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            final VBox cell = new VBox(4);
            cell.getStyleClass().add("apply-cell");

            final HBox topBox = new HBox(8);
            topBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            final Label name = new Label(item.getUsername() != null ? item.getUsername() : "未知用户");
            name.getStyleClass().add("apply-name");

            final Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            final Label status = new Label(statusToText(item.getStatus()));
            status.getStyleClass().add("apply-status");

            topBox.getChildren().addAll(name, spacer, status);

            final Label message = new Label(item.getMessage() != null ? "留言: " + item.getMessage() : "");
            message.getStyleClass().add("apply-message");
            message.setWrapText(true);

            // 待处理状态显示操作按钮
            if ("PENDING".equals(item.getStatus())) {
                final HBox actionBox = new HBox(8);
                actionBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

                final Button acceptBtn = new Button("同意");
                acceptBtn.getStyleClass().add("btn-success-sm");
                acceptBtn.setOnAction(e -> viewModel.acceptApply(item.getApplyId()));

                final Button rejectBtn = new Button("拒绝");
                rejectBtn.getStyleClass().add("btn-danger-sm");
                rejectBtn.setOnAction(e -> viewModel.rejectApply(item.getApplyId()));

                actionBox.getChildren().addAll(acceptBtn, rejectBtn);
                cell.getChildren().addAll(topBox, message, actionBox);
            } else {
                cell.getChildren().addAll(topBox, message);
            }

            setGraphic(cell);
            setText(null);
        }

        /**
         * 状态转文本
         *
         * @param status 状态
         * @return 文本
         */
        private String statusToText(final String status) {
            if (status == null) {
                return "";
            }
            return switch (status) {
                case "ACCEPTED" -> "已同意";
                case "REJECTED" -> "已拒绝";
                default -> "待处理";
            };
        }
    }
}
