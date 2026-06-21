# 聊天板块开发计划书

> 基于后端已实现接口，规划客户端聊天功能的完善与增强

---

## 一、现状分析

### 1.1 后端已实现（开发者B 完成）

| 模块 | 文件 | 状态 | 说明 |
|------|------|------|------|
| 消息发送（REST） | [MessageController.java](file:///c:/Users/OTC/Desktop/学习/demo/server/src/main/java/com/voluntary/chat/server/controller/MessageController.java) | 已完成 | `POST /api/message/send` 备用通道 |
| 聊天记录 | MessageController.java | 已完成 | `GET /api/message/history` 分页查询 |
| 消息撤回 | MessageController.java | 已完成 | `POST /api/message/recall` 2分钟限制 |
| 已读上报 | MessageController.java | 已完成 | `POST /api/message/read` 批量标记 |
| 会话列表 | [ConversationController.java](file:///c:/Users/OTC/Desktop/学习/demo/server/src/main/java/com/voluntary/chat/server/controller/ConversationController.java) | 已完成 | `GET /api/conversation/list` |
| WebSocket转发 | [ChatWebSocketHandler.java](file:///c:/Users/OTC/Desktop/学习/demo/server/src/main/java/com/voluntary/chat/server/websocket/ChatWebSocketHandler.java) | 已完成 | SEND_MESSAGE / RECEIVE_MESSAGE / MESSAGE_ACK |
| 在线状态广播 | ChatWebSocketHandler.java | 已完成 | STATUS_CHANGE |
| 心跳 | ChatWebSocketHandler.java | 已完成 | PING / PONG |
| 图片上传 | API.md 4.6 节 | 接口已定义 | `POST /api/message/upload/image` |

### 1.2 客户端已实现

| 模块 | 文件 | 状态 | 说明 |
|------|------|------|------|
| REST API 封装 | [ChatService.java](file:///c:/Users/OTC/Desktop/学习/demo/client/src/main/java/org/example/client/service/ChatService.java) | 已完成 | 会话列表/历史/发送/已读/撤回 |
| WebSocket 客户端 | [WebSocketClient.java](file:///c:/Users/OTC/Desktop/学习/demo/client/src/main/java/org/example/client/service/WebSocketClient.java) | 已完成 | 连接/心跳/重连/消息回调 |
| 聊天视图模型 | [ChatViewModel.java](file:///c:/Users/OTC/Desktop/学习/demo/client/src/main/java/org/example/client/view/ChatViewModel.java) | 已完成 | 消息列表/乐观发送/ACK匹配 |
| 主界面视图模型 | [MainViewModel.java](file:///c:/Users/OTC/Desktop/学习/demo/client/src/main/java/org/example/client/view/MainViewModel.java) | 已完成 | 会话列表/消息分发/状态管理 |
| 主界面控制器 | [MainController.java](file:///c:/Users/OTC/Desktop/学习/demo/client/src/main/java/org/example/client/controller/MainController.java) | 已完成 | 会话Cell/消息Cell/输入发送 |
| 消息气泡UI | [chat.css](file:///c:/Users/OTC/Desktop/学习/demo/client/src/main/resources/css/chat.css) | 已完成 | 发送/接收气泡样式 |
| 数据模型 | MessageInfo / ConversationInfo | 已完成 | 对应后端 DTO |

### 1.3 客户端待完善功能

| 优先级 | 功能 | 说明 | 后端支持 |
|--------|------|------|---------|
| P0 | 消息撤回UI | 右键菜单触发撤回，撤回后更新气泡 | 已有 `POST /message/recall` |
| P0 | 已读上报 | 打开会话时批量上报已读消息 | 已有 `POST /message/read` |
| P0 | 加载更多历史 | 滚动到顶部触发分页加载 | 已有分页支持 |
| P1 | 断线重连消息补发 | 使用 RECONNECT 消息类型拉取离线消息 | 已有 RECONNECT_ACK |
| P1 | 已读回执展示 | 处理 READ_RECEIPT 消息，显示对方已读 | 已有 WebSocket 类型 |
| P1 | 图片消息发送 | 上传图片后发送 IMAGE 类型消息 | 已有上传接口 |
| P1 | 图片消息展示 | 消息气泡中渲染图片 | 消息 type 支持 IMAGE |
| P2 | 会话搜索 | 搜索框过滤会话列表 | 客户端本地过滤 |
| P2 | 消息复制 | 右键菜单复制消息文本 | 纯前端 |
| P2 | 未读数实时更新 | 收到消息时更新会话未读角标 | 已有数据 |

---

## 二、功能模块设计

### 2.1 消息撤回（P0）

**后端接口**：
```
POST /api/message/recall
Request: { "messageId": "msg_001" }
```

**后端逻辑**（参考 [MessageService.java](file:///c:/Users/OTC/Desktop/学习/demo/server/src/main/java/com/voluntary/chat/server/service/MessageService.java) `recallMessage` 方法）：
- 人-人消息：2分钟内可撤回，超时返回错误码 `4001`
- 只能撤回自己的消息，否则返回 `4002`
- AI 消息可随时撤回
- 撤回后设置 `recallTime`，消息 `recalled` 字段变为 `true`

**客户端实现方案**：
1. 在 `MessageCell` 中为消息气泡添加右键菜单（ContextMenu）
2. 菜单项「撤回」仅在 `sentByMe == true` 且 `recalled == false` 时显示
3. 点击撤回 → 调用 `ChatService.getInstance().recallMessage(messageId)`
4. 成功后更新消息的 `recalled` 字段，触发 UI 刷新显示「消息已撤回」
5. 失败时显示错误提示（如超时、无权限）

**涉及文件**：
- 修改：`MainController.java` - MessageCell 添加 ContextMenu
- 修改：`ChatViewModel.java` - 添加 `recallMessage(Long messageId)` 方法
- 修改：`ChatService.java` - 已有 `recallMessage` 方法，无需改动

### 2.2 已读上报（P0）

**后端接口**：
```
POST /api/message/read
Request: { "sessionId": "p_1001_1002", "messageIds": ["msg_001", "msg_002"] }
```

**后端逻辑**（参考 `MessageService.markRead` 方法）：
- 批量插入 `MessageRead` 记录，利用唯一索引去重
- 未读数 = 对方发来的消息总数 - 已读记录数

**客户端实现方案**：
1. 在 `ChatViewModel.loadHistory()` 成功后，收集所有非自己发送的消息 ID
2. 调用 `ChatService.getInstance().markRead(request)` 上报已读
3. 上报成功后清零该会话的未读角标（更新 `ConversationInfo.unreadCount`）

**涉及文件**：
- 修改：`ChatViewModel.java` - `loadHistory()` 方法末尾追加已读上报逻辑
- 修改：`MainViewModel.java` - `selectConversation` 时清零未读数

### 2.3 加载更多历史消息（P0）

**后端接口**：
```
GET /api/message/history?sessionId=p_1001_1002&page=2&size=20
```

**客户端实现方案**：
1. 在 `MainController` 中监听 `messageList` 的滚动事件
2. 当滚动到顶部（垂直滚动值 == 0）时触发 `ChatViewModel.loadMoreHistory()`
3. `loadMoreHistory()` 已实现：页码 +1，请求历史，插入到列表头部
4. 加载完成后保持滚动位置（记录加载前的消息数量，加载后跳转到对应偏移）

**涉及文件**：
- 修改：`MainController.java` - 为 `messageList` 添加滚动监听器
- `ChatViewModel.java` - `loadMoreHistory()` 已实现，需补充防重复加载逻辑

### 2.4 断线重连消息补发（P1）

**后端 WebSocket 协议**：
```
客户端 → 服务端: { "type": "RECONNECT", "data": { "lastMessageId": "msg_010" } }
服务端 → 客户端: { "type": "RECONNECT_ACK", "data": { "missedMessages": [...] } }
```

**客户端实现方案**：
1. `WebSocketClient` 记录最后收到的服务端消息 ID（`lastMessageId`）
2. 重连成功后发送 `RECONNECT` 消息，携带 `lastMessageId`
3. `MainViewModel` 处理 `RECONNECT_ACK` 消息，将 `missedMessages` 追加到对应会话

**涉及文件**：
- 修改：`WebSocketClient.java` - 重连成功后发送 RECONNECT
- 修改：`MainViewModel.java` - `handleWebSocketMessage` 添加 `RECONNECT_ACK` 分支
- 新增：`WebSocketClient` 中维护 `lastMessageId` 字段

### 2.5 已读回执展示（P1）

**后端 WebSocket 协议**：
```
服务端 → 客户端: { "type": "READ_RECEIPT", "data": { "sessionId", "userId", "lastReadMessageId", "readTime" } }
```

**客户端实现方案**：
1. `MainViewModel.handleWebSocketMessage` 添加 `READ_RECEIPT` 处理分支
2. 如果当前正在查看该会话，更新已发送消息的已读状态
3. 在消息气泡下方显示「已读」/「未读」标签

**涉及文件**：
- 修改：`MainViewModel.java` - 添加 `READ_RECEIPT` 处理
- 修改：`MessageInfo.java` - 添加 `read` 字段
- 修改：`MainController.java` - MessageCell 中添加已读标签
- 修改：`chat.css` - 添加 `.message-read-status` 样式

### 2.6 图片消息（P1）

**后端接口**：
```
POST /api/message/upload/image (multipart/form-data)
Response: { "url", "thumbnailUrl", "width", "height", "fileType" }
```

**消息发送**：通过 WebSocket `SEND_MESSAGE`，`msgType` 为 `IMAGE`，`content` 为图片 URL。

**客户端实现方案**：
1. 在输入区域添加图片按钮，点击后打开文件选择器
2. 选择图片 → 校验格式（JPEG/PNG/GIF/WebP）和大小（≤10MB）
3. 调用上传接口获取 URL
4. 通过 WebSocket 发送 `IMAGE` 类型消息，`content` 为图片 URL
5. `MessageCell` 中根据 `type` 判断：`TEXT` 显示文本，`IMAGE` 显示 `ImageView`

**涉及文件**：
- 修改：`MainController.java` - 添加图片按钮和文件选择逻辑
- 修改：`ChatViewModel.java` - 添加 `sendImage(String imageUrl)` 方法
- 修改：`ChatService.java` - 添加 `uploadImage(File file)` 方法
- 修改：`MainController.java` - MessageCell 支持 IMAGE 类型渲染
- 修改：`main.fxml` - 输入区域添加图片按钮
- 修改：`chat.css` - 添加 `.message-image` 样式

### 2.7 会话搜索（P2）

**客户端实现方案**：
1. 监听 `searchField` 的文本变化
2. 根据输入文本过滤 `conversations` 列表（按 `targetName` 模糊匹配）
3. 使用过滤后的列表替换 ListView 显示

**涉及文件**：
- 修改：`MainController.java` - 为 `searchField` 添加监听器
- 修改：`MainViewModel.java` - 添加 `filterConversations(String keyword)` 方法

### 2.8 未读数实时更新（P2）

**客户端实现方案**：
1. 收到 `RECEIVE_MESSAGE` 时，如果不在当前会话窗口，增加该会话的 `unreadCount`
2. 如果在当前会话窗口，自动上报已读，不增加未读数
3. 会话列表 Cell 的未读角标自动更新（已绑定 `unreadCount`）

**涉及文件**：
- 修改：`MainViewModel.java` - `handleReceiveMessage` 方法中增加未读数逻辑

---

## 三、任务清单

### 第一阶段（P0 - 核心功能完善）

| 序号 | 任务 | 涉及文件 | 测试要求 |
|------|------|---------|---------|
| 1 | 消息撤回 - 右键菜单 | MainController, ChatViewModel | 撤回成功/超时/无权限 |
| 2 | 已读上报 - 打开会话时上报 | ChatViewModel, MainViewModel | 批量上报/空列表处理 |
| 3 | 加载更多 - 滚动到顶部触发 | MainController, ChatViewModel | 分页加载/防重复/保持滚动位置 |

### 第二阶段（P1 - 功能增强）

| 序号 | 任务 | 涉及文件 | 测试要求 |
|------|------|---------|---------|
| 4 | 断线重连消息补发 | WebSocketClient, MainViewModel | 重连后消息不丢失 |
| 5 | 已读回执展示 | MainViewModel, MessageInfo, MainController | 已读/未读状态正确 |
| 6 | 图片消息发送 | MainController, ChatViewModel, ChatService | 上传/发送/展示 |
| 7 | 图片消息渲染 | MainController (MessageCell) | 不同尺寸图片适配 |

### 第三阶段（P2 - 体验优化）

| 序号 | 任务 | 涉及文件 | 测试要求 |
|------|------|---------|---------|
| 8 | 会话搜索 | MainController, MainViewModel | 模糊匹配/清空恢复 |
| 9 | 消息复制 | MainController (MessageCell) | 复制到剪贴板 |
| 10 | 未读数实时更新 | MainViewModel | 在线/离线消息计数 |

---

## 四、接口对接速查

### 4.1 REST 接口

| 功能 | 方法 | 路径 | 客户端方法 |
|------|------|------|-----------|
| 会话列表 | GET | `/api/conversation/list` | `ChatService.getConversations()` |
| 聊天记录 | GET | `/api/message/history` | `ChatService.getHistory()` |
| 发送消息（备用） | POST | `/api/message/send` | `ChatService.sendMessage()` |
| 撤回消息 | POST | `/api/message/recall` | `ChatService.recallMessage()` |
| 已读上报 | POST | `/api/message/read` | `ChatService.markRead()` |
| 上传图片 | POST | `/api/message/upload/image` | 待实现 |

### 4.2 WebSocket 消息类型

| 类型 | 方向 | 客户端处理 | 状态 |
|------|------|-----------|------|
| `SEND_MESSAGE` | C→S | 发送消息 | 已实现 |
| `RECEIVE_MESSAGE` | S→C | 接收消息，追加到列表 | 已实现 |
| `MESSAGE_ACK` | S→C | 更新乐观消息的 messageId | 已实现 |
| `STATUS_CHANGE` | S→C | 更新在线状态 | 已实现（日志） |
| `READ_RECEIPT` | S→C | 更新消息已读状态 | 待实现 |
| `RECONNECT` | C→S | 断线重连请求 | 待实现 |
| `RECONNECT_ACK` | S→C | 补发离线消息 | 待实现 |
| `PING` / `PONG` | 双向 | 心跳保活 | 已实现 |

---

## 五、测试计划

### 5.1 单元测试

| 测试类 | 覆盖范围 | 目标覆盖率 |
|--------|---------|-----------|
| `ChatViewModelTest` | 消息发送/撤回/已读上报/加载更多 | ≥ 80% |
| `MainViewModelTest` | 会话列表/消息分发/未读数/重连 | ≥ 80% |
| `ChatServiceTest` | REST API 调用/Mock 响应 | ≥ 90% |

### 5.2 关键测试用例

**消息撤回**：
- 撤回自己的消息 → 成功，气泡显示「消息已撤回」
- 撤回他人消息 → 失败，提示错误码 4002
- 超过2分钟撤回 → 失败，提示错误码 4001
- 撤回已撤回的消息 → 失败，提示「消息不存在」

**已读上报**：
- 打开会话 → 上报所有未读消息 ID
- 空消息列表 → 不发起上报请求
- 上报成功 → 未读数清零

**加载更多**：
- 滚动到顶部 → 触发加载，新消息插入头部
- 加载中再次滚动 → 不重复触发
- 无更多数据 → 不再发起请求

**断线重连**：
- 断线后重连 → 发送 RECONNECT 携带 lastMessageId
- 收到 RECONNECT_ACK → 追加 missedMessages 到对应会话

---

## 六、开发规范

### 6.1 代码规范（遵循 AGENTS.md）
- Controller ≤ 300 行，Service ≤ 400 行，Entity/DTO ≤ 150 行
- UI 更新必须使用 `Platform.runLater()`
- 使用 Lombok 减少样板代码
- 复杂逻辑添加中文注释说明「为什么」

### 6.2 分支命名
```
feature/A/client/chat-recall          # 消息撤回
feature/A/client/chat-read-report      # 已读上报
feature/A/client/chat-load-more        # 加载更多
feature/A/client/chat-reconnect       # 断线重连
feature/A/client/chat-image-message   # 图片消息
```

### 6.3 提交格式
```
feat(chat): 实现消息撤回功能

添加右键菜单触发撤回，调用 REST API 并更新气泡状态
支持2分钟内撤回自己的消息，超时提示错误
```
