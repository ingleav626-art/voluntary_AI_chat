package org.example.client.controller;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import org.example.client.model.AiProfile;
import org.example.client.view.AiViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI 角色编辑对话框控制器
 *
 * <p>处理创建和编辑 AI 角色的表单逻辑。</p>
 */
public final class AiEditController implements Initializable {

    private static final Logger LOG = LoggerFactory.getLogger(AiEditController.class);

    @FXML
    private Label dialogTitle;

    @FXML
    private TextField nameField;

    @FXML
    private ComboBox<String> providerCombo;

    @FXML
    private TextField modelField;

    @FXML
    private PasswordField apiKeyField;

    @FXML
    private TextField baseUrlField;

    @FXML
    private TextField avatarField;

    @FXML
    private TextArea openingMessageField;

    @FXML
    private TextArea personaField;

    @FXML
    private TextArea systemPromptField;

    @FXML
    private TextField temperatureField;

    @FXML
    private TextField maxTokensField;

    @FXML
    private CheckBox isGroupCheck;

    @FXML
    private Label errorLabel;

    @FXML
    private Button cancelButton;

    @FXML
    private Button saveButton;

    private AiViewModel viewModel;

    private AiProfile editTarget;

    private boolean isEditMode = false;

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        providerCombo.getItems().setAll(AiViewModel.MODEL_PROVIDERS);
    }

    /**
     * 设置视图模型
     *
     * @param viewModel 视图模型
     */
    public void setViewModel(final AiViewModel viewModel) {
        this.viewModel = viewModel;
    }

    /**
     * 设置为创建模式
     */
    public void setCreateMode() {
        this.isEditMode = false;
        dialogTitle.setText("创建 AI 角色");
        clearForm();
    }

    /**
     * 设置为编辑模式，填充已有数据
     *
     * @param profile 要编辑的 AI 角色
     */
    public void setEditMode(final AiProfile profile) {
        this.isEditMode = true;
        this.editTarget = profile;
        dialogTitle.setText("编辑 AI 角色");

        nameField.setText(profile.getName() != null ? profile.getName() : "");
        providerCombo.setValue(profile.getModelProvider());
        modelField.setText(profile.getModel() != null ? profile.getModel() : "");
        // 编辑模式下不回填 API Key（安全考虑）
        apiKeyField.setPromptText("留空则不修改");
        baseUrlField.setText(profile.getBaseUrl() != null ? profile.getBaseUrl() : "");
        avatarField.setText(profile.getAvatar() != null ? profile.getAvatar() : "");
        openingMessageField.setText(profile.getOpeningMessage() != null ? profile.getOpeningMessage() : "");
        personaField.setText(profile.getPersona() != null ? profile.getPersona() : "");
        systemPromptField.setText(profile.getSystemPrompt() != null ? profile.getSystemPrompt() : "");
        temperatureField.setText(profile.getTemperature() != null ? String.valueOf(profile.getTemperature()) : "");
        maxTokensField.setText(profile.getMaxTokens() != null ? String.valueOf(profile.getMaxTokens()) : "");
        isGroupCheck.setSelected(profile.getIsGroup() != null && profile.getIsGroup());
    }

    /**
     * 清空表单
     */
    private void clearForm() {
        nameField.clear();
        providerCombo.setValue(null);
        modelField.clear();
        apiKeyField.clear();
        apiKeyField.setPromptText("输入 API Key");
        baseUrlField.clear();
        avatarField.clear();
        openingMessageField.clear();
        personaField.clear();
        systemPromptField.clear();
        temperatureField.clear();
        maxTokensField.clear();
        isGroupCheck.setSelected(false);
        errorLabel.setText("");
    }

    /**
     * 处理保存
     */
    @FXML
    private void handleSave() {
        final String name = nameField.getText().trim();
        final String provider = providerCombo.getValue();
        final String model = modelField.getText().trim();
        final String apiKey = apiKeyField.getText() != null ? apiKeyField.getText().trim() : "";

        // 验证必填项
        if (name.isEmpty()) {
            errorLabel.setText("AI 名称不能为空");
            return;
        }
        if (provider == null || provider.isEmpty()) {
            errorLabel.setText("请选择模型提供商");
            return;
        }
        if (model.isEmpty()) {
            errorLabel.setText("模型名称不能为空");
            return;
        }
        if (!isEditMode && apiKey.isEmpty()) {
            errorLabel.setText("API Key 不能为空");
            return;
        }

        // 解析可选数值
        Double temperature = null;
        Integer maxTokens = null;

        if (!temperatureField.getText().trim().isEmpty()) {
            try {
                temperature = Double.parseDouble(temperatureField.getText().trim());
                if (temperature < 0 || temperature > 2) {
                    errorLabel.setText("温度参数范围为 0.0-2.0");
                    return;
                }
            } catch (final NumberFormatException e) {
                errorLabel.setText("温度参数格式错误");
                return;
            }
        }

        if (!maxTokensField.getText().trim().isEmpty()) {
            try {
                maxTokens = Integer.parseInt(maxTokensField.getText().trim());
                if (maxTokens <= 0) {
                    errorLabel.setText("最大 Token 必须大于 0");
                    return;
                }
            } catch (final NumberFormatException e) {
                errorLabel.setText("最大 Token 格式错误");
                return;
            }
        }

        // 构建 AiProfile 对象
        final AiProfile profile = new AiProfile();
        profile.setName(name);
        profile.setModelProvider(provider);
        profile.setModel(model);
        if (!apiKey.isEmpty()) {
            profile.setApiKey(apiKey);
        }
        profile.setBaseUrl(baseUrlField.getText().trim().isEmpty() ? null : baseUrlField.getText().trim());
        profile.setAvatar(avatarField.getText().trim().isEmpty() ? null : avatarField.getText().trim());
        profile.setOpeningMessage(openingMessageField.getText().trim().isEmpty()
                ? null : openingMessageField.getText().trim());
        profile.setPersona(personaField.getText().trim().isEmpty() ? null : personaField.getText().trim());
        profile.setSystemPrompt(systemPromptField.getText().trim().isEmpty()
                ? null : systemPromptField.getText().trim());
        profile.setTemperature(temperature);
        profile.setMaxTokens(maxTokens);
        profile.setIsGroup(isGroupCheck.isSelected());

        if (isEditMode && editTarget != null) {
            viewModel.updateAiProfile(editTarget.getAiId(), profile);
        } else {
            final String openingMsg = profile.getOpeningMessage();
            viewModel.createAiProfile(profile, openingMsg);
        }

        // 关闭对话框
        closeDialog();
    }

    /**
     * 处理取消
     */
    @FXML
    private void handleCancel() {
        closeDialog();
    }

    /**
     * 关闭对话框
     */
    private void closeDialog() {
        final Stage stage = (Stage) saveButton.getScene().getWindow();
        stage.close();
    }
}
