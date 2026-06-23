# 图片板块补全计划

## 一、现状分析

### 后端已实现功能
1. ✅ 图片上传接口（POST /message/upload/image）
2. ✅ 图片格式校验（JPEG/PNG/GIF/WebP）
3. ✅ 图片大小校验（10MB）
4. ✅ 图片压缩（最大宽度1080px）
5. ✅ 缩略图生成（最大宽度200px）
6. ✅ 静态资源映射（/files/**）
7. ✅ 单元测试（ImageUploadServiceTest）

### 前端已实现功能
1. ✅ 图片选择界面（FileChooser）
2. ✅ 图片上传逻辑（ChatService.uploadImage）
3. ✅ 图片消息发送（WebSocket发送IMAGE类型消息）
4. ✅ 图片消息显示（ImageView渲染）
5. ✅ 图片大小校验（10MB限制）
6. ✅ 图片加载失败处理（显示链接）

### 前后端差距
**核心功能已完整实现，差距主要在增强功能：**

1. ❌ 图片预览功能（选择图片后显示预览）
2. ❌ 图片上传进度显示
3. ❌ 缩略图显示优化（直接显示原图，应使用缩略图）
4. ❌ 图片点击放大功能
5. ❌ 图片下载功能
6. ❌ 图片上传错误处理优化

---

## 二、补全计划

### 优先级划分

#### P0 - 核心功能（必须实现）
无核心功能缺失，图片上传和显示功能已完整。

#### P1 - 重要增强功能（建议实现）
1. **缩略图显示优化**
   - 当前：直接显示原图（可能很大）
   - 改进：使用缩略图显示，点击后显示原图
   - 影响：提升性能，减少带宽消耗
   - 工作量：2小时

2. **图片预览功能**
   - 当前：选择图片后直接上传，无预览
   - 改进：选择图片后显示预览，用户确认后再上传
   - 影响：提升用户体验，避免误操作
   - 工作量：3小时

3. **图片点击放大功能**
   - 当前：图片固定大小显示
   - 改进：点击图片后弹出窗口显示原图
   - 影响：提升用户体验
   - 工作量：2小时

#### P2 - 次要增强功能（可选实现）
1. **图片上传进度显示**
   - 工作量：3小时

2. **图片下载功能**
   - 工作量：2小时

3. **图片上传错误处理优化**
   - 工作量：1小时

---

## 三、详细实现方案

### 3.1 缩略图显示优化

**修改文件：**
- `client/src/main/java/org/example/client/controller/MainController.java`

**实现步骤：**
1. 图片消息渲染时，优先使用缩略图URL
2. 如果缩略图加载失败，回退到原图URL
3. 点击图片时，切换显示原图

**代码示例：**
```java
// 图片消息渲染
if (item.getMsgType() == MSG_TYPE_IMAGE) {
    ImageView imageView = new ImageView();

    // 优先使用缩略图
    String thumbnailUrl = item.getThumbnailUrl();
    String originalUrl = item.getContent();

    Image image = new Image(thumbnailUrl != null ? thumbnailUrl : originalUrl, true);
    imageView.setImage(image);

    // 设置缩略图尺寸
    imageView.setFitWidth(200);
    imageView.setFitHeight(150);

    // 点击显示原图
    imageView.setOnMouseClicked(e -> showFullImage(originalUrl));
}
```

### 3.2 图片预览功能

**新增文件：**
- `client/src/main/java/org/example/client/controller/ImagePreviewDialog.java`

**实现步骤：**
1. 创建预览对话框FXML
2. 选择图片后显示预览
3. 用户确认后上传

**代码示例：**
```java
@FXML
private void handleSendImage() {
    FileChooser fileChooser = new FileChooser();
    File selectedFile = fileChooser.showOpenDialog(...);

    if (selectedFile != null) {
        // 显示预览对话框
        ImagePreviewDialog dialog = new ImagePreviewDialog(selectedFile);
        if (dialog.showAndWait()) {
            // 用户确认后上传
            chatVm.sendImage(selectedFile.toPath());
        }
    }
}
```

### 3.3 图片点击放大功能

**新增文件：**
- `client/src/main/java/org/example/client/controller/ImageViewerDialog.java`

**实现步骤：**
1. 创建图片查看对话框
2. 点击图片时弹出对话框显示原图
3. 支持缩放和滚动

**代码示例：**
```java
private void showFullImage(String imageUrl) {
    ImageViewerDialog dialog = new ImageViewerDialog(imageUrl);
    dialog.show();
}
```

---

## 四、单元测试计划

### 4.1 ChatService 图片上传测试

**测试文件：**
- `client/src/test/java/org/example/client/service/ChatServiceTest.java`

**测试用例：**
1. `testUploadImageSuccess` - 图片上传成功
2. `testUploadImageFileNotFound` - 文件不存在
3. `testUploadImageFileTooLarge` - 文件超过10MB
4. `testUploadImageInvalidFormat` - 不支持的格式

### 4.2 ChatViewModel 图片发送测试

**测试文件：**
- `client/src/test/java/org/example/client/view/ChatViewModelTest.java`

**测试用例：**
1. `testSendImageSuccess` - 图片发送成功
2. `testSendImageUploadFailed` - 上传失败
3. `testSendImageWebSocketSend` - WebSocket发送验证

### 4.3 ImageUploadResponse 模型测试

**测试文件：**
- `client/src/test/java/org/example/client/model/ImageUploadResponseTest.java`

**测试用例：**
1. `testGetterSetter` - getter/setter测试
2. `testEqualsHashCode` - equals/hashCode测试
3. `testToString` - toString测试

---

## 五、工作量估算

| 功能 | 优先级 | 工作量 | 文件数 |
|------|--------|--------|--------|
| 缩略图显示优化 | P1 | 2小时 | 1 |
| 图片预览功能 | P1 | 3小时 | 2 |
| 图片点击放大 | P1 | 2小时 | 1 |
| 单元测试编写 | P0 | 4小时 | 3 |
| **总计** | - | **11小时** | **7** |

---

## 六、实施建议

### 建议顺序
1. 先编写单元测试（验证现有功能）
2. 实现缩略图显示优化（性能提升）
3. 实现图片点击放大（用户体验）
4. 实现图片预览功能（用户体验）

### 注意事项
1. 图片上传功能已完整，不要重复实现
2. 单元测试优先，确保现有功能正确
3. 增强功能按优先级逐步实现
4. 注意性能优化，避免加载大图片

---

## 七、总结

图片板块前后端功能已基本完整实现，主要差距在增强功能。建议优先编写单元测试验证现有功能，然后按优先级实现增强功能。