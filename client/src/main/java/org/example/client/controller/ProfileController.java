package org.example.client.controller;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import org.example.client.model.UserInfo;
import org.example.client.view.ProfileViewModel;
import org.example.client.view.MainViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 个人设置控制器
 *
 * <p>负责个人信息的展示、修改、密码修改、手机号修改等交互。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class ProfileController implements Initializable {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileController.class);

    /** 日期格式化器 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 时间格式化器 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ========== FXML 组件 ==========

    @FXML
    private TabPane tabPane;

    @FXML
    private Circle avatarCircle;

    @FXML
    private Button changeAvatarButton;

    @FXML
    private TextField usernameField;

    @FXML
    private TextField phoneField;

    @FXML
    private TextField bioField;

    @FXML
    private ComboBox<String> genderComboBox;

    @FXML
    private Spinner<Integer> ageSpinner;

    @FXML
    private DatePicker birthdayPicker;

    @FXML
    private TextArea detailBioArea;

    @FXML
    private Label createTimeLabel;

    @FXML
    private Label profileMessageLabel;

    @FXML
    private Button saveProfileButton;

    @FXML
    private ProgressIndicator profileLoadingIndicator;

    // 修改密码
    @FXML
    private Label passwordPhoneLabel;

    @FXML
    private Button sendPasswordSmsButton;

    @FXML
    private TextField passwordSmsField;

    @FXML
    private PasswordField newPasswordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private Button changePasswordButton;

    @FXML
    private ProgressIndicator passwordLoadingIndicator;

    // 修改手机号
    @FXML
    private Label changePhoneLabel;

    @FXML
    private Button sendCurrentSmsButton;

    @FXML
    private TextField currentSmsField;

    @FXML
    private TextField newPhoneField;

    @FXML
    private Button sendNewSmsButton;

    @FXML
    private TextField newSmsField;

    @FXML
    private Button changePhoneButton;

    @FXML
    private ProgressIndicator phoneLoadingIndicator;

    @FXML
    private Label securityMessageLabel;

    @FXML
    private Button closeButton;

    // ========== ViewModel ==========

    private ProfileViewModel viewModel;

    /** 主界面 ViewModel（用于同步更新） */
    private MainViewModel mainViewModel;

    /** 对话框 Stage */
    private Stage dialogStage;

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        LOG.info("初始化个人设置控制器");

        viewModel = new ProfileViewModel();

        // 初始化性别下拉框
        genderComboBox.getItems().addAll("未知", "男", "女");
        genderComboBox.getSelectionModel().selectFirst();

        // 初始化年龄 Spinner
        final SpinnerValueFactory.IntegerSpinnerValueFactory ageFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 200, 0);
        ageSpinner.setValueFactory(ageFactory);

        // 绑定属性
        bindProperties();

        // 设置回调
        setupCallbacks();

        // 加载用户信息
        viewModel.loadProfile();
    }

    /**
     * 绑定 ViewModel 属性到 UI 组件
     */
    private void bindProperties() {
        // 基本信息
        usernameField.textProperty().bindBidirectional(viewModel.usernameProperty());
        phoneField.textProperty().bind(viewModel.phoneProperty());
        bioField.textProperty().bindBidirectional(viewModel.bioProperty());

        // 头像绑定
        viewModel.avatarProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                try {
                    final Image avatarImage = new Image(newVal, true);
                    avatarImage.progressProperty().addListener((o, oldP, newP) -> {
                        if (newP.doubleValue() >= 1.0 && !avatarImage.isError()) {
                            avatarCircle.setFill(new ImagePattern(avatarImage));
                        }
                    });
                } catch (final Exception e) {
                    LOG.warn("加载头像图片失败: {}", newVal, e);
                }
            }
        });

        // 性别绑定（需要转换）
        viewModel.genderProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                final int genderValue = newVal.intValue();
                final String genderText = genderValue == 1 ? "男" : (genderValue == 2 ? "女" : "未知");
                genderComboBox.getSelectionModel().select(genderText);
            }
        });
        genderComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                final int genderValue = "男".equals(newVal) ? 1 : ("女".equals(newVal) ? 2 : 0);
                viewModel.genderProperty().set(genderValue);
            }
        });

        // 年龄绑定
        ageSpinner.getValueFactory().valueProperty().bindBidirectional(viewModel.ageProperty().asObject());

        // 生日绑定
        birthdayPicker.valueProperty().bindBidirectional(viewModel.birthdayProperty());

        // 详细说明
        detailBioArea.textProperty().bindBidirectional(viewModel.detailBioProperty());

        // 注册时间
        viewModel.createTimeProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                createTimeLabel.setText(newVal.format(TIME_FORMATTER));
            } else {
                createTimeLabel.setText("-");
            }
        });

        // 加载状态
        profileLoadingIndicator.visibleProperty().bind(viewModel.loadingProperty());
        passwordLoadingIndicator.visibleProperty().bind(viewModel.loadingProperty());
        phoneLoadingIndicator.visibleProperty().bind(viewModel.loadingProperty());
        saveProfileButton.disableProperty().bind(viewModel.loadingProperty());
        changePasswordButton.disableProperty().bind(viewModel.loadingProperty());
        changePhoneButton.disableProperty().bind(viewModel.loadingProperty());

        // 消息提示
        profileMessageLabel.textProperty().bind(viewModel.successMessageProperty());
        securityMessageLabel.textProperty().bind(viewModel.successMessageProperty());

        // 错误消息样式
        viewModel.errorMessageProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                showError(newVal);
            }
        });

        // 修改密码区域手机号显示
        passwordPhoneLabel.textProperty().bind(viewModel.phoneProperty());
        changePhoneLabel.textProperty().bind(viewModel.phoneProperty());

        // 修改密码表单绑定
        passwordSmsField.textProperty().bindBidirectional(viewModel.passwordSmsCodeProperty());
        newPasswordField.textProperty().bindBidirectional(viewModel.newPasswordProperty());
        confirmPasswordField.textProperty().bindBidirectional(viewModel.confirmPasswordProperty());

        // 修改手机号表单绑定
        currentSmsField.textProperty().bindBidirectional(viewModel.currentPhoneSmsCodeProperty());
        newPhoneField.textProperty().bindBidirectional(viewModel.newPhoneProperty());
        newSmsField.textProperty().bindBidirectional(viewModel.newPhoneSmsCodeProperty());
    }

    /**
     * 设置回调
     */
    private void setupCallbacks() {
        viewModel.setOnSuccess(message -> {
            Platform.runLater(() -> {
                showSuccess(message);
            });
        });

        viewModel.setOnFailure(message -> {
            Platform.runLater(() -> {
                showError(message);
            });
        });

        viewModel.setOnProfileUpdated(profile -> {
            // 同步更新主界面显示
            if (mainViewModel != null) {
                mainViewModel.updateCurrentUser(profile);
            }
        });
    }

    /**
     * 显示错误消息
     *
     * @param message 错误消息
     */
    private void showError(final String message) {
        profileMessageLabel.setStyle("-fx-text-fill: #e74c3c;");
        profileMessageLabel.setText(message);
        securityMessageLabel.setStyle("-fx-text-fill: #e74c3c;");
        securityMessageLabel.setText(message);
    }

    /**
     * 显示成功消息
     *
     * @param message 成功消息
     */
    private void showSuccess(final String message) {
        profileMessageLabel.setStyle("-fx-text-fill: #27ae60;");
        profileMessageLabel.setText(message);
        securityMessageLabel.setStyle("-fx-text-fill: #27ae60;");
        securityMessageLabel.setText(message);
    }

    // ========== 事件处理 ==========

    /**
     * 处理保存基本信息
     */
    @FXML
    private void handleSaveProfile() {
        LOG.info("保存基本信息");
        viewModel.saveProfile();
    }

    /**
     * 处理发送密码验证码
     */
    @FXML
    private void handleSendPasswordSms() {
        LOG.info("发送密码验证码");
        viewModel.sendSmsCodeForPassword(sendPasswordSmsButton);
    }

    /**
     * 处理修改密码
     */
    @FXML
    private void handleChangePassword() {
        LOG.info("修改密码");
        viewModel.changePassword();
    }

    /**
     * 处理发送当前手机验证码
     */
    @FXML
    private void handleSendCurrentSms() {
        LOG.info("发送当前手机验证码");
        viewModel.sendSmsCodeForCurrentPhone(sendCurrentSmsButton);
    }

    /**
     * 处理发送新手机验证码
     */
    @FXML
    private void handleSendNewSms() {
        LOG.info("发送新手机验证码");
        viewModel.sendSmsCodeForNewPhone(sendNewSmsButton);
    }

    /**
     * 处理修改手机号
     */
    @FXML
    private void handleChangePhone() {
        LOG.info("修改手机号");
        viewModel.changePhone();
    }

    /**
     * 处理更换头像
     */
    @FXML
    private void handleChangeAvatar() {
        LOG.info("更换头像");
        // TODO: 实现头像上传功能
        final javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("选择头像图片");
        fileChooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("图片文件", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.webp")
        );

        final java.io.File selectedFile = fileChooser.showOpenDialog(dialogStage);
        if (selectedFile != null) {
            // 上传头像
            uploadAvatar(selectedFile);
        }
    }

    /**
     * 上传头像
     *
     * @param file 图片文件
     */
    private void uploadAvatar(final java.io.File file) {
        LOG.info("上传头像: {}", file.getName());

        // 检查文件大小（最大 10MB）
        final long maxSize = 10 * 1024 * 1024;
        if (file.length() > maxSize) {
            showError("图片大小不能超过10MB");
            return;
        }

        // 本地预览：先显示选中的图片
        try {
            final Image previewImage = new Image(file.toURI().toString(), true);
            previewImage.progressProperty().addListener((o, oldP, newP) -> {
                if (newP.doubleValue() >= 1.0 && !previewImage.isError()) {
                    avatarCircle.setFill(new ImagePattern(previewImage));
                }
            });
        } catch (final Exception e) {
            LOG.warn("头像预览失败", e);
        }

        // 显示加载状态
        profileLoadingIndicator.setVisible(true);
        saveProfileButton.setDisable(true);

        // 调用图片上传接口
        org.example.client.service.ChatService.getInstance()
                .uploadImage(file.toPath())
                .thenAcceptAsync(response -> {
                    Platform.runLater(() -> {
                        profileLoadingIndicator.setVisible(false);
                        saveProfileButton.setDisable(false);

                        if (response != null && response.isSuccess() && response.getData() != null) {
                            // 更新头像 URL 到 ViewModel
                            final String avatarUrl = response.getData().getUrl();
                            viewModel.avatarProperty().set(avatarUrl);
                            showSuccess("头像上传成功，请点击保存按钮保存");

                            // 用服务端 URL 重新加载头像
                            try {
                                final Image serverImage = new Image(avatarUrl, true);
                                serverImage.progressProperty().addListener((o, oldP, newP) -> {
                                    if (newP.doubleValue() >= 1.0 && !serverImage.isError()) {
                                        avatarCircle.setFill(new ImagePattern(serverImage));
                                    }
                                });
                            } catch (final Exception e) {
                                LOG.warn("从服务端加载头像失败", e);
                            }
                            LOG.info("头像上传成功: {}", avatarUrl);
                        } else {
                            final String msg = response != null ? response.getMessage() : "上传失败";
                            showError(msg);
                            LOG.warn("头像上传失败: {}", msg);
                        }
                    });
                });
    }

    /**
     * 处理关闭对话框
     */
    @FXML
    private void handleClose() {
        LOG.info("关闭个人设置对话框");
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    // ========== Setter ==========

    /**
     * 设置对话框 Stage
     *
     * @param stage Stage
     */
    public void setDialogStage(final Stage stage) {
        this.dialogStage = stage;
    }

    /**
     * 设置主界面 ViewModel
     *
     * @param mainViewModel 主界面 ViewModel
     */
    public void setMainViewModel(final MainViewModel mainViewModel) {
        this.mainViewModel = mainViewModel;
    }
}