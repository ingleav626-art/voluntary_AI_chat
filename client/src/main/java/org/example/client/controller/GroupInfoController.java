package org.example.client.controller;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.example.client.view.GroupListViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 修改群信息对话框控制器
 *
 * <p>提供群名称、群公告、公告置顶等信息的修改功能（仅群主可操作）。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class GroupInfoController implements Initializable {

    private static final Logger LOG = LoggerFactory.getLogger(GroupInfoController.class);

    @FXML
    private TextField groupNameField;

    @FXML
    private TextArea announcementArea;

    @FXML
    private CheckBox pinnedCheckBox;

    @FXML
    private Label errorLabel;

    @FXML
    private Button confirmButton;

    @FXML
    private Button cancelButton;

    /** 群组ID */
    private Long groupId;

    /** ViewModel 引用 */
    private GroupListViewModel viewModel;

    /**
     * 初始化对话框数据
     *
     * @param groupId  群组ID
     * @param viewModel ViewModel
     * @param currentName 当前群名称
     * @param currentAnnouncement 当前群公告
     * @param currentPinned 当前公告是否置顶
     */
    public void initData(final Long groupId, final GroupListViewModel viewModel,
                         final String currentName, final String currentAnnouncement,
                         final boolean currentPinned) {
        this.groupId = groupId;
        this.viewModel = viewModel;
        groupNameField.setText(currentName != null ? currentName : "");
        announcementArea.setText(currentAnnouncement != null ? currentAnnouncement : "");
        pinnedCheckBox.setSelected(currentPinned);
    }

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        LOG.info("修改群信息对话框控制器初始化完成");
    }

    /**
     * 处理确认修改
     */
    @FXML
    private void handleConfirm() {
        final String name = groupNameField.getText();
        if (name == null || name.trim().isEmpty()) {
            errorLabel.setText("群名称不能为空");
            return;
        }
        if (name.trim().length() < 2 || name.trim().length() > 50) {
            errorLabel.setText("群名称长度需为 2-50 字符");
            return;
        }

        confirmButton.setDisable(true);
        errorLabel.setText("");

        LOG.info("修改群信息: groupId={}, name={}", groupId, name);

        viewModel.updateGroupInfo(
                groupId,
                name.trim(),
                announcementArea.getText(),
                pinnedCheckBox.isSelected()
        );

        closeWindow();
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
}
