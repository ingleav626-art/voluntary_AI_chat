# 消息撤回持久化修复计划书

> 修复"发送消息后第一次撤回，刷新页面后消息又显示出来"的问题

---

## 一、问题现象

- 发送消息后，点击撤回，UI 显示"消息已撤回"
- 刷新页面（重新加载历史消息）后，撤回的消息又恢复显示
- **"第一次"撤回** 出问题，后续撤回（已确认消息路径）正常

---

## 二、根因分析

### 2.1 撤回流程对比

| 路径 | 触发条件 | 执行方式 | 问题 |
|------|---------|---------|------|
| 乐观消息撤回 | ACK 到达前撤回（messageId < 0） | 立即标记 `recalled=true`，ACK 到达后延迟 REST 撤回 | **延迟 REST 撤回可能失败** |
| 已确认消息撤回 | ACK 到达后撤回（messageId > 0） | 异步 REST 撤回，成功后才标记 `recalled=true` | 正常 |

### 2.2 乐观消息路径的时序问题

```
用户发送消息 → 乐观消息加入列表（messageId = -1）
用户点击撤回 → 标记 recalled=true，注册延迟撤回
ACK 到达     → updateMessageAck 执行延迟 REST 撤回
```

在 `ChatViewModel.updateMessageAck` 中：

```java
// 使用 .join() 同步等待，在 JavaFX Application Thread 上执行
if (pendingRecallClientIds.remove(clientId)) {
    final var recallResponse = ChatService.getInstance().recallMessage(messageId).join();
}
```

### 2.3 三个核心缺陷

#### 缺陷1：`.join()` 阻塞 JavaFX 线程

`handleAck` 在 `Platform.runLater` 中执行，`.join()` 会**阻塞 JavaFX 线程**，导致：
- UI 冻结直到 HTTP 响应返回
- 如果 HTTP 请求超时，`.join()` 抛出 `CompletionException`

#### 缺陷2：延迟撤回失败后不回滚 UI 状态

```java
try {
    final var recallResponse = ChatService.getInstance().recallMessage(messageId).join();
    // 只记录日志，不检查 success
} catch (final Exception e) {
    LOG.error("延迟 REST 撤回失败: messageId={}", messageId, e);
    // 失败后不取消 recalled 标记，UI 仍显示"已撤回"
}
```

客户端已经标记 `recalled=true`，但如果 REST 调用失败：
- 服务端 `recall_time` 未更新
- 客户端 UI 仍显示"已撤回"
- 刷新页面后服务端返回 `recalled=false`，消息恢复显示

#### 缺陷3：`ChatService.recallMessage` 返回类型不匹配

```java
// 客户端期望
public CompletableFuture<ApiResponse<Void>> recallMessage(final Long messageId)

// 服务端实际返回
return ApiResult.ok("已撤回", response);  // data 是 RecallMessageResponse 对象
```

虽然 Jackson 通常能将 `Void` 类型的 `data` 设为 `null`，但这是潜在风险。

---

## 三、修复方案

### 3.1 修复1：将延迟撤回移出 JavaFX 线程

**文件**：`client/src/main/java/org/example/client/view/ChatViewModel.java`

**修改 `updateMessageAck` 方法**：

```java
public void updateMessageAck(final String clientId, final Long messageId,
                              final java.time.LocalDateTime createTime) {
    final MessageInfo pending = pendingMessages.remove(clientId);
    if (pending != null) {
        pending.setMessageId(messageId);
        pending.setCreateTime(createTime);

        final int index = messages.indexOf(pending);
        if (index >= 0) {
            messages.set(index, pending);
        }

        // 延迟撤回：异步执行，不阻塞 JavaFX 线程
        if (pendingRecallClientIds.remove(clientId)) {
            LOG.info("ACK 到达，执行延迟 REST 撤回: clientId={}, messageId={}", clientId, messageId);
            ChatService.getInstance().recallMessage(messageId)
                    .thenAccept(response -> {
                        Platform.runLater(() -> {
                            if (response != null && response.isSuccess()) {
                                LOG.info("延迟 REST 撤回成功: messageId={}", messageId);
                            } else {
                                // 撤回失败，回滚 UI 状态
                                pending.setRecalled(false);
                                final int idx = messages.indexOf(pending);
                                if (idx >= 0) {
                                    messages.set(idx, pending);
                                }
                                final String msg = response != null ? response.getMessage() : "撤回失败";
                                errorMessage.set(msg);
                                LOG.warn("延迟 REST 撤回失败，已回滚 UI: messageId={}, error={}",
                                        messageId, msg);
                            }
                        });
                    })
                    .exceptionally(ex -> {
                        LOG.error("延迟 REST 撤回异常: messageId={}", messageId, ex);
                        Platform.runLater(() -> {
                            pending.setRecalled(false);
                            final int idx = messages.indexOf(pending);
                            if (idx >= 0) {
                                messages.set(idx, pending);
                            }
                            errorMessage.set("撤回失败，请重试");
                        });
                        return null;
                    });
        }

        LOG.debug("消息确认更新: clientId={}, messageId={}", clientId, messageId);
    }
}
```

### 3.2 修复2：`ChatService.recallMessage` 返回类型对齐

**文件**：`client/src/main/java/org/example/client/service/ChatService.java`

将 `ApiResponse<Void>` 改为 `ApiResponse<RecallMessageResponse>`，与服务端返回类型对齐：

```java
public CompletableFuture<ApiResponse<RecallMessageResponse>> recallMessage(final Long messageId) {
    final HttpRequest httpRequest = buildPostRequest(
                    MESSAGE_RECALL_PATH, java.util.Map.of("messageId", String.valueOf(messageId))).build();
    return sendRequest(httpRequest, getTypeFactory().constructParametricType(
                    ApiResponse.class, RecallMessageResponse.class));
}
```

需要在客户端新增 `RecallMessageResponse` 模型类（对应服务端 `com.voluntary.chat.server.dto.RecallMessageResponse`）。

### 3.3 修复3：已确认消息路径增加异常处理

**文件**：`client/src/main/java/org/example/client/view/ChatViewModel.java`

在 `recallMessage` 的已确认消息路径增加 `.exceptionally` 处理：

```java
ChatService.getInstance().recallMessage(messageId)
        .thenAccept(response -> {
            Platform.runLater(() -> {
                if (response != null && response.isSuccess()) {
                    message.setRecalled(true);
                    final int index = messages.indexOf(message);
                    if (index >= 0) {
                        messages.set(index, message);
                    }
                    LOG.info("消息撤回成功: messageId={}", messageId);
                } else {
                    final String msg = response != null ? response.getMessage() : "撤回失败";
                    errorMessage.set(msg);
                    LOG.warn("消息撤回失败: {}", msg);
                }
            });
        })
        .exceptionally(ex -> {
            LOG.error("消息撤回异常: messageId={}", messageId, ex);
            Platform.runLater(() -> errorMessage.set("网络异常，撤回失败"));
            return null;
        });
```

---

## 四、实施步骤

| 步骤 | 文件 | 内容 | 优先级 |
|------|------|------|--------|
| 1 | `client/.../model/RecallMessageResponse.java` | 新增客户端 `RecallMessageResponse` 模型类 | 高 |
| 2 | `client/.../service/ChatService.java` | 修改 `recallMessage` 返回类型为 `ApiResponse<RecallMessageResponse>` | 高 |
| 3 | `client/.../view/ChatViewModel.java` | 修改 `updateMessageAck`：异步延迟撤回 + 失败回滚 | 高 |
| 4 | `client/.../view/ChatViewModel.java` | 修改 `recallMessage` 已确认路径：增加 `.exceptionally` | 中 |
| 5 | 单元测试 | 补充 `ChatViewModelTest` 覆盖乐观消息撤回失败回滚场景 | 高 |
| 6 | 集成测试 | 验证撤回后刷新页面，消息仍显示"已撤回" | 高 |

---

## 五、测试计划

### 5.1 单元测试

| 测试用例 | 验证点 |
|---------|--------|
| 乐观消息撤回-ACK到达后REST成功 | `recalled=true` 保持，服务端 `recall_time` 已更新 |
| 乐观消息撤回-ACK到达后REST失败 | UI 回滚 `recalled=false`，提示"撤回失败" |
| 乐观消息撤回-ACK到达后REST异常 | UI 回滚 `recalled=false`，提示"网络异常" |
| 已确认消息撤回-REST成功 | `recalled=true`，服务端 `recall_time` 已更新 |
| 已确认消息撤回-REST失败 | `recalled=false`，提示错误消息 |
| 已确认消息撤回-REST异常 | `recalled=false`，提示"网络异常" |

### 5.2 集成测试

| 测试场景 | 操作步骤 | 预期结果 |
|---------|---------|---------|
| 乐观消息撤回后刷新 | 发送消息→立即撤回→等ACK→刷新页面 | 消息显示"已撤回" |
| 乐观消息撤回失败后刷新 | 发送消息→立即撤回→模拟REST失败→刷新页面 | 消息恢复显示（非"已撤回"） |
| 已确认消息撤回后刷新 | 发送消息→等ACK→撤回→刷新页面 | 消息显示"已撤回" |

### 5.3 回归测试

- 验证正常撤回流程不受影响
- 验证群聊撤回流程
- 验证 AI 消息撤回流程

---

## 六、风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 异步延迟撤回期间用户刷新页面 | `recall_time` 可能未更新 | 可接受，用户可再次撤回 |
| 回滚 UI 状态时消息已不在列表中 | `messages.indexOf` 返回 -1 | 已有 `if (index >= 0)` 保护 |
| `RecallMessageResponse` 模型类字段不匹配 | 反序列化失败 | 严格对齐服务端字段 |

---

## 七、验收标准

- [ ] 发送消息后立即撤回，刷新页面后消息仍显示"已撤回"
- [ ] 延迟撤回失败时，UI 回滚并提示用户
- [ ] JavaFX 线程不再被 `.join()` 阻塞
- [ ] 单元测试覆盖率 ≥ 80%
- [ ] 集成测试全部通过
