package org.example.client.controller;

import java.util.ArrayList;
import java.util.List;

import org.example.client.model.AiGroupConfig;
import org.example.client.model.AiProfile;
import org.example.client.model.PageResult;
import org.example.client.service.AiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * 群 AI 配置管理控制器
 *
 * <p>
 * 管理群聊中的 AI 配置：查看、添加、修改、删除。
 * </p>
 */
public class GroupAiConfigController {

    private static final Logger LOG = LoggerFactory.getLogger(GroupAiConfigController.class);

    @FXML
    private VBox rootPane;

    @FXML
    private Label dialogTitle;

    @FXML
    private ListView<AiGroupConfig> configListView;

    @FXML
    private Label formTitle;

    @FXML
    private ComboBox<AiProfile> aiSelectCombo;

    @FXML
    private TextField triggerKeywordsField;

    @FXML
    private TextField triggerProbabilityField;

    @FXML
    private CheckBox isEnabledCheck;

    @FXML
    private Label errorLabel;

    @FXML
    private Button cancelButton;

    @FXML
    private Button addButton;

    /** 当前群组ID */
    private Long groupId;

    /** 编辑中的配置ID（null 表示新增模式） */
    private Long editingConfigId;

    /** AI 角色列表缓存 */
    private List<AiProfile> aiProfiles = new ArrayList<>();

    /**
     * 设置群组ID
     */
    public void setGroupId(final Long groupId) {
        this.groupId = groupId;
        loadConfigs();
        loadAiProfiles();
    }

    /**
     * 初始化
     */
    @FXML
    public void initialize() {
        configListView.setCellFactory(list -> new AiGroupConfigCell());
    }

    /**
     * 加载群 AI 配置列表
     */
    private void loadConfigs() {
        if (groupId == null) {
            return;
        }

        AiService.getInstance().getGroupConfigs(groupId)
                .thenAccept(response -> {
                    Platform.runLater(() -> {
                        if (response != null && response.isSuccess() && response.getData() != null) {
                            configListView.setItems(
                                    FXCollections.observableArrayList(response.getData()));
                            LOG.info("群AI配置加载成功: groupId={}, count={}",
                                    groupId, response.getData().size());
                        } else {
                            final String msg = response != null ? response.getMessage() : "加载配置失败";
                            errorLabel.setText(msg);
                            LOG.warn("群AI配置加载失败: {}", msg);
                        }
                    });
                })
                .exceptionally(ex -> {
                    LOG.error("加载群AI配置异常", ex);
                    Platform.runLater(() -> errorLabel.setText("网络异常，加载配置失败"));
                    return null;
                });
    }

    /**
     * 加载 AI 角色列表供选择
     */
    private void loadAiProfiles() {
        AiService.getInstance().getAiList(1, 100)
                .thenAccept(response -> {
                    Platform.runLater(() -> {
                        if (response != null && response.isSuccess() && response.getData() != null) {
                            final PageResult<AiProfile> result = response.getData();
                            aiProfiles = result.getList() != null ? result.getList() : new ArrayList<>();
                            aiSelectCombo.setItems(FXCollections.observableArrayList(aiProfiles));
                        }
                    });
                })
                .exceptionally(ex -> {
                    LOG.error("加载AI角色列表异常", ex);
                    return null;
                });
    }

    /**
     * 添加配置
     */
    @FXML
    private void handleAdd() {
        errorLabel.setText("");

        final AiProfile selectedAi = aiSelectCombo.getValue();
        if (selectedAi == null) {
            errorLabel.setText("请选择 AI 角色");
            return;
        }

        final AiGroupConfig config = new AiGroupConfig();
        config.setAiId(selectedAi.getAiId());
        config.setTriggerKeywords(triggerKeywordsField.getText().trim());
        config.setIsEnabled(isEnabledCheck.isSelected());

        final String probText = triggerProbabilityField.getText().trim();
        if (!probText.isEmpty()) {
            try {
                config.setTriggerProbability(Double.parseDouble(probText));
            } catch (final NumberFormatException e) {
                errorLabel.setText("触发概率必须是数字");
                return;
            }
        }

        if (editingConfigId != null) {
            // 修改模式
            AiService.getInstance().updateGroupConfig(groupId, editingConfigId, config)
                    .thenAccept(response -> {
                        Platform.runLater(() -> {
                            if (response != null && response.isSuccess()) {
                                LOG.info("群AI配置修改成功: configId={}", editingConfigId);
                                resetForm();
                                loadConfigs();
                            } else {
                                final String msg = response != null ? response.getMessage() : "修改失败";
                                errorLabel.setText(msg);
                            }
                        });
                    })
                    .exceptionally(ex -> {
                        LOG.error("修改群AI配置异常", ex);
                        Platform.runLater(() -> errorLabel.setText("网络异常，修改失败"));
                        return null;
                    });
        } else {
            // 新增模式
            AiService.getInstance().createGroupConfig(groupId, config)
                    .thenAccept(response -> {
                        Platform.runLater(() -> {
                            if (response != null && response.isSuccess()) {
                                LOG.info("群AI配置添加成功");
                                resetForm();
                                loadConfigs();
                            } else {
                                final String msg = response != null ? response.getMessage() : "添加失败";
                                errorLabel.setText(msg);
                            }
                        });
                    })
                    .exceptionally(ex -> {
                        LOG.error("添加群AI配置异常", ex);
                        Platform.runLater(() -> errorLabel.setText("网络异常，添加失败"));
                        return null;
                    });
        }
    }

    /**
     * 删除配置
     *
     * @param config 要删除的配置
     */
    private void handleDeleteConfig(final AiGroupConfig config) {
        if (config == null || config.getConfigId() == null || groupId == null) {
            return;
        }

        final javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认删除");
        alert.setHeaderText("删除 AI 配置");
        alert.setContentText("确定要删除 " + (config.getAiName() != null ? config.getAiName() : "此AI") + " 的配置吗？");

        alert.showAndWait().ifPresent(response -> {
            if (response == javafx.scene.control.ButtonType.OK) {
                AiService.getInstance().deleteGroupConfig(groupId, config.getConfigId())
                        .thenAccept(resp -> {
                            Platform.runLater(() -> {
                                if (resp != null && resp.isSuccess()) {
                                    LOG.info("群AI配置删除成功: configId={}", config.getConfigId());
                                    loadConfigs();
                                } else {
                                    final String msg = resp != null ? resp.getMessage() : "删除失败";
                                    errorLabel.setText(msg);
                                }
                            });
                        })
                        .exceptionally(ex -> {
                            LOG.error("删除群AI配置异常", ex);
                            Platform.runLater(() -> errorLabel.setText("网络异常，删除失败"));
                            return null;
                        });
            }
        });
    }

    /**
     * 编辑配置（填充表单）
     *
     * @param config 要编辑的配置
     */
    private void handleEditConfig(final AiGroupConfig config) {
        if (config == null) {
            return;
        }

        editingConfigId = config.getConfigId();
        formTitle.setText("编辑 AI 配置");
        addButton.setText("保存修改");

        // 选中对应的 AI
        for (final AiProfile profile : aiProfiles) {
            if (profile.getAiId().equals(config.getAiId())) {
                aiSelectCombo.setValue(profile);
                break;
            }
        }

        triggerKeywordsField.setText(config.getTriggerKeywords() != null ? config.getTriggerKeywords() : "");
        triggerProbabilityField.setText(config.getTriggerProbability() != null
                ? String.valueOf(config.getTriggerProbability())
                : "");
        isEnabledCheck.setSelected(config.getIsEnabled() != null ? config.getIsEnabled() : true);
    }

    /**
     * 重置表单
     */
    private void resetForm() {
        editingConfigId = null;
        formTitle.setText("添加 AI 配置");
        addButton.setText("添加配置");
        aiSelectCombo.setValue(null);
        triggerKeywordsField.clear();
        triggerProbabilityField.clear();
        isEnabledCheck.setSelected(true);
        errorLabel.setText("");
    }

    /**
     * 关闭弹窗
     */
    @FXML
    private void handleCancel() {
        final Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }

    /**
     * 群 AI 配置列表 Cell
     */
    private final class AiGroupConfigCell extends ListCell<AiGroupConfig> {
        private final HBox cellBox;
        private final VBox infoBox;
        private final Label nameLabel;
        private final Label keywordsLabel;
        private final Label statusLabel;
        private final Button editBtn;
        private final Button deleteBtn;

        AiGroupConfigCell() {
            nameLabel = new Label();
            nameLabel.setStyle("-fx-font-weight: bold;");

            keywordsLabel = new Label();
            keywordsLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 11px;");

            statusLabel = new Label();
            statusLabel.setStyle("-fx-font-size: 11px;");

            infoBox = new VBox(2, nameLabel, keywordsLabel, statusLabel);

            editBtn = new Button("编辑");
            editBtn.setStyle("-fx-text-fill: #3498db; -fx-background-color: transparent; -fx-cursor: hand;");

            deleteBtn = new Button("删除");
            deleteBtn.setStyle("-fx-text-fill: #e74c3c; -fx-background-color: transparent; -fx-cursor: hand;");

            final VBox btnBox = new VBox(4, editBtn, deleteBtn);
            btnBox.setPrefWidth(50);

            cellBox = new HBox(10, infoBox, btnBox);
            cellBox.setStyle("-fx-padding: 6; -fx-alignment: CENTER_LEFT;");
            HBox.setHgrow(infoBox, javafx.scene.layout.Priority.ALWAYS);
        }

        @Override
        protected void updateItem(final AiGroupConfig item, final boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            nameLabel.setText(item.getAiName() != null ? item.getAiName() : "AI #" + item.getAiId());
            keywordsLabel.setText("关键词: " + (item.getTriggerKeywords() != null ? item.getTriggerKeywords() : "无"));
            statusLabel.setText((item.getIsEnabled() != null && item.getIsEnabled() ? "已启用" : "已禁用")
                    + (item.getTriggerProbability() != null ? " | 概率: " + item.getTriggerProbability() : ""));

            editBtn.setOnAction(e -> handleEditConfig(item));
            deleteBtn.setOnAction(e -> handleDeleteConfig(item));

            setGraphic(cellBox);
            setText(null);
        }
    }
}
