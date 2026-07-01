package org.example.client.controller;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import org.example.client.model.AiMemory;
import org.example.client.model.AiProfile;
import org.example.client.view.AiViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI 模块控制器
 *
 * <p>负责 AI 角色列表展示、角色详情、记忆查看、创建/编辑/删除操作。</p>
 */
public final class AiController implements Initializable {

    private static final Logger LOG = LoggerFactory.getLogger(AiController.class);

    @FXML
    private VBox rootPane;

    @FXML
    private Button backButton;

    @FXML
    private Button refreshButton;

    @FXML
    private Button createButton;

    @FXML
    private ListView<AiProfile> aiListView;

    @FXML
    private VBox detailPanel;

    @FXML
    private VBox detailContent;

    @FXML
    private VBox detailEmpty;

    @FXML
    private Label detailName;

    @FXML
    private Label detailModel;

    @FXML
    private Label detailProvider;

    @FXML
    private Label detailIsGroup;

    @FXML
    private Label detailPersona;

    @FXML
    private Button editButton;

    @FXML
    private Button deleteButton;

    @FXML
    private ListView<AiMemory> memoryList;

    @FXML
    private Label errorLabel;

    @FXML
    private Label successLabel;

    @FXML
    private ProgressIndicator loadingIndicator;

    private AiViewModel viewModel;

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        viewModel = new AiViewModel();

        // 绑定 AI 列表
        aiListView.itemsProperty().bind(viewModel.aiListProperty());
        aiListView.setCellFactory(param -> new AiProfileCell());

        // 绑定记忆列表
        memoryList.itemsProperty().bind(viewModel.memoriesProperty());
        memoryList.setCellFactory(param -> new AiMemoryCell());

        // 列表选中事件
        aiListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            viewModel.selectedAiProperty().set(newVal);
            updateDetailPanel(newVal);
        });

        // 绑定加载状态
        loadingIndicator.visibleProperty().bind(viewModel.loadingProperty());

        // 绑定错误/成功消息
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        successLabel.textProperty().bind(viewModel.successMessageProperty());

        // 成功消息3秒后自动消失
        viewModel.successMessageProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(
                        javafx.util.Duration.seconds(3));
                pause.setOnFinished(e -> viewModel.successMessageProperty().set(""));
                pause.play();
            }
        });

        // 加载 AI 列表
        viewModel.loadAiList();

        LOG.info("AI模块控制器初始化完成");
    }

    /**
     * 更新详情面板
     *
     * @param profile 选中的 AI 角色
     */
    private void updateDetailPanel(final AiProfile profile) {
        if (profile == null) {
            detailContent.setVisible(false);
            detailEmpty.setVisible(true);
            editButton.setVisible(false);
            deleteButton.setVisible(false);
            return;
        }

        detailContent.setVisible(true);
        detailEmpty.setVisible(false);
        editButton.setVisible(true);
        deleteButton.setVisible(true);

        detailName.setText(profile.getName() != null ? profile.getName() : "");
        detailModel.setText(profile.getModel() != null ? profile.getModel() : "");
        detailProvider.setText(profile.getModelProvider() != null ? profile.getModelProvider() : "");
        detailIsGroup.setText(profile.getIsGroup() != null && profile.getIsGroup() ? "是" : "否");
        detailPersona.setText(profile.getPersona() != null ? profile.getPersona() : "未设置");
    }

    /**
     * 处理返回按钮
     */
    @FXML
    private void handleBack() {
        LOG.info("返回主界面");
        org.example.client.App.switchToMainFromAi();
    }

    /**
     * 处理刷新
     */
    @FXML
    private void handleRefresh() {
        LOG.info("刷新AI角色列表");
        viewModel.loadAiList();
    }

    /**
     * 处理创建 AI 角色
     */
    @FXML
    private void handleCreate() {
        LOG.info("创建AI角色");
        AiEditDialog.showCreate(rootPane.getScene().getWindow(), viewModel);
    }

    /**
     * 处理编辑 AI 角色
     */
    @FXML
    private void handleEdit() {
        final AiProfile selected = viewModel.selectedAiProperty().get();
        if (selected == null) {
            return;
        }
        LOG.info("编辑AI角色: aiId={}", selected.getAiId());
        AiEditDialog.showEdit(rootPane.getScene().getWindow(), viewModel, selected);
    }

    /**
     * 处理删除 AI 角色
     */
    @FXML
    private void handleDelete() {
        final AiProfile selected = viewModel.selectedAiProperty().get();
        if (selected == null) {
            return;
        }

        final javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认删除");
        alert.setHeaderText("删除 AI 角色");
        alert.setContentText("确定要删除 \"" + selected.getName() + "\" 吗？此操作不可恢复。");

        alert.showAndWait().ifPresent(response -> {
            if (response == javafx.scene.control.ButtonType.OK) {
                LOG.info("确认删除AI角色: aiId={}", selected.getAiId());
                viewModel.deleteAiProfile(selected.getAiId());
            }
        });
    }

    /**
     * AI 角色列表 Cell
     */
    private final class AiProfileCell extends ListCell<AiProfile> {
        private final HBox cell;
        private final javafx.scene.shape.Circle avatarCircle;
        private final Label avatarText;
        private final javafx.scene.layout.StackPane avatarPane;
        private final VBox infoBox;
        private final Label name;
        private final Label modelInfo;
        private final Label groupBadge;
        private final Button jumpButton;

        AiProfileCell() {
            cell = new HBox(10);
            cell.getStyleClass().add("ai-profile-cell");
            cell.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            // 头像
            avatarCircle = new javafx.scene.shape.Circle(18);
            avatarCircle.setFill(javafx.scene.paint.Color.valueOf("#E76F51"));
            avatarCircle.getStyleClass().add("ai-avatar-circle");
            avatarText = new Label("AI");
            avatarText.setStyle("-fx-text-fill: white; -fx-font-size: 13; -fx-font-weight: bold;");
            avatarPane = new javafx.scene.layout.StackPane(avatarCircle, avatarText);
            avatarPane.setCursor(javafx.scene.Cursor.HAND);

            infoBox = new VBox(4);
            javafx.scene.layout.HBox.setHgrow(infoBox, javafx.scene.layout.Priority.ALWAYS);

            final javafx.scene.layout.HBox topBox = new javafx.scene.layout.HBox(8);
            topBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            name = new Label();
            name.getStyleClass().add("ai-profile-name");

            groupBadge = new Label();
            groupBadge.getStyleClass().add("ai-group-badge");

            topBox.getChildren().addAll(name, groupBadge);

            modelInfo = new Label();
            modelInfo.getStyleClass().add("ai-profile-model");

            infoBox.getChildren().addAll(topBox, modelInfo);

            // 跳转按钮
            jumpButton = new Button("跳转");
            jumpButton.getStyleClass().add("btn-ai-jump");
            jumpButton.setMinWidth(50);

            cell.getChildren().addAll(avatarPane, infoBox, jumpButton);
        }

        @Override
        protected void updateItem(final AiProfile item, final boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            // 头像：显示首字母或加载真实头像
            final String displayName = item.getName() != null ? item.getName() : "AI";
            avatarText.setText(!displayName.isEmpty() ? String.valueOf(displayName.charAt(0)) : "A");
            avatarCircle.setFill(javafx.scene.paint.Color.valueOf("#E76F51"));

            // 加载AI头像图片（如果有）
            if (item.getAvatar() != null && !item.getAvatar().isEmpty()) {
                org.example.client.util.ImageUtils.loadAvatarToPane(item.getAvatar(), avatarPane, 36);
            }

            // 头像点击 → 打开编辑对话框
            avatarPane.setOnMouseClicked(e -> handleEditAvatar(item));

            name.setText(displayName);
            modelInfo.setText((item.getModelProvider() != null ? item.getModelProvider() : "")
                    + " / " + (item.getModel() != null ? item.getModel() : ""));

            groupBadge.setVisible(item.getIsGroup() != null && item.getIsGroup());
            groupBadge.setManaged(item.getIsGroup() != null && item.getIsGroup());
            if (item.getIsGroup() != null && item.getIsGroup()) {
                groupBadge.setText("群聊");
            }

            jumpButton.setOnAction(e -> handleJumpToAi(item));

            setGraphic(cell);
            setText(null);
        }
    }

    /**
     * 跳转到与 AI 的聊天界面
     *
     * @param profile AI 角色
     */
    private void handleJumpToAi(final AiProfile profile) {
        if (profile == null || profile.getAiId() == null) {
            return;
        }
        LOG.info("跳转到AI聊天: aiId={}, name={}", profile.getAiId(), profile.getName());
        org.example.client.App.switchToMainWithAiConversation(
                profile.getAiId(),
                profile.getName(),
                profile.getAvatar());
    }

    /**
     * 修改 AI 头像（点击头像时触发）
     *
     * @param profile AI 角色
     */
    private void handleEditAvatar(final AiProfile profile) {
        if (profile == null) {
            return;
        }
        AiEditDialog.showEdit(rootPane.getScene().getWindow(), viewModel, profile);
    }

    /**
     * AI 记忆列表 Cell
     */
    private final class AiMemoryCell extends ListCell<AiMemory> {
        private final VBox cell;
        private final Label summary;
        private final Label keywords;
        private final Label time;
        private final Button deleteBtn;

        AiMemoryCell() {
            cell = new VBox(4);
            cell.getStyleClass().add("ai-memory-cell");

            summary = new Label();
            summary.getStyleClass().add("ai-memory-summary");
            summary.setWrapText(true);
            summary.setMaxWidth(380);

            final javafx.scene.layout.HBox bottomBox = new javafx.scene.layout.HBox(8);
            bottomBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            keywords = new Label();
            keywords.getStyleClass().add("ai-memory-keywords");

            final javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
            javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

            time = new Label();
            time.getStyleClass().add("ai-memory-time");

            deleteBtn = new Button("删除");
            deleteBtn.getStyleClass().add("btn-ai-memory-delete");
            deleteBtn.setStyle("-fx-text-fill: #e74c3c; -fx-background-color: transparent; -fx-cursor: hand;");

            bottomBox.getChildren().addAll(keywords, spacer, time, deleteBtn);

            cell.getChildren().addAll(summary, bottomBox);
        }

        @Override
        protected void updateItem(final AiMemory item, final boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            summary.setText(item.getSummary() != null ? item.getSummary() : "");
            keywords.setText(item.getKeywords() != null ? item.getKeywords() : "");
            time.setText(item.getCreateTime() != null ? item.getCreateTime() : "");

            deleteBtn.setOnAction(e -> handleDeleteMemory(item));

            setGraphic(cell);
            setText(null);
        }
    }

    /**
     * 处理删除记忆
     *
     * @param memory 要删除的记忆
     */
    private void handleDeleteMemory(final AiMemory memory) {
        if (memory == null || memory.getMemoryId() == null) {
            return;
        }

        final javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认删除");
        alert.setHeaderText("删除 AI 记忆");
        alert.setContentText("确定要删除这条记忆吗？");

        alert.showAndWait().ifPresent(response -> {
            if (response == javafx.scene.control.ButtonType.OK) {
                LOG.info("确认删除AI记忆: memoryId={}", memory.getMemoryId());
                viewModel.deleteMemory(memory.getMemoryId());
            }
        });
    }
}
