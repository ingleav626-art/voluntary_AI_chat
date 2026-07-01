### 问题 1：群聊 AI 回复未广播
现状 ：AI 回复只推给了发送者本人，群里其他人看不到。

方案 ：在 AiChatService 中新增一个 handleGroupAiChat() 方法，与私聊的 handleAiChat() 区分开。当群聊 AI 回复完成后，以 GROUP_MESSAGE 类型广播给群内所有成员（而非 AI_STREAM 流式推给单人）。

文件 改动 AiChatService.java 新增 handleGroupAiChat() → 接收 groupId , 完成时调用 webSocketHandler.broadcastToGroupExcept() AiWebSocketHandler.java triggerGroupAi() 改为调用 handleGroupAiChat() 而非 handleAiChat() ChatWebSocketHandler.java triggerGroupAi() 调用时传入 senderName（用于上下文）

### 问题 2：群聊 AI 上下文区分消息来源
现状 ：群聊 sessionId g_{groupId}_a_{aiId} 下，所有人的消息都在同一个上下文中，AI 不知道谁说了什么。

方案 ：在 AiChatService.buildContext() 中，读取群聊 sessionId 的历史消息时，获取消息对应的 senderName ，将每条消息格式化为 用户「张三」说：消息内容 。这样 AI 从上下文中就能区分不同的人。

影响范围 ：仅 AiChatService.java 中的 buildContext/ getHistoryMessages 方法。群聊（sessionId 含 g_ ）和私聊走不同格式化逻辑。

### 问题 3：并发调用排队
现状 ：多人同时@同一个 AI 时，会同时触发多个 doHandleAiChat() ，上下文互相干扰。

方案 ：在 AiChatService 中为每个 (groupId, aiId) 维护一个队列（ LinkedBlockingQueue ），新触发任务入队，前一个完成后自动消费下一个。使用 ThreadPoolExecutor 配合队列实现串行化。

文件 改动 AiChatService.java 添加 ConcurrentHashMap<String, Queue<Runnable>> 队列映射，key= groupId:aiId handleGroupAiChat() 入队后立即返回，由队列调度器串行执行

### 问题 4：AI 角色头像上传接口
现状 ：有用户头像（ POST /api/file/upload/avatar ）和群头像（ POST /api/file/upload/group-avatar ）上传接口，但没有 AI 头像上传接口。

方案 ：在 FileController 中新增 POST /api/file/upload/ai-avatar ，复用 ImageUploadService.uploadAvatar() 。

### 问题 5：配置缺失
现状 ： application.yml 、 application-local.yml 、 application-hotspot.yml 、 application-cloud.yml 中缺少 upload.avatar.base-url ，导致头像 URL 生成不正确。

方案 ：补齐各 profile 配置文件中的 avatar.base-url 。

文件 加的值 application.yml base-url: http://localhost:8080/avatars application-local.yml base-url: http://localhost:8080/avatars application-hotspot.yml base-url: http://localhost:8080/avatars application-cloud.yml base-url: https://your-domain.com/avatars

### 文件改动汇总
文件 改动内容 server/.../AiChatService.java 核心改动 ：新增 handleGroupAiChat() 、排队队列、上下文用户名格式化 server/.../AiWebSocketHandler.java triggerGroupAi() 调用新增的群聊方法 server/.../ChatWebSocketHandler.java triggerGroupAi() 传入 senderName server/.../FileController.java 新增 POST /api/file/upload/ai-avatar server/.../resources/application*.yml × 4 补齐 avatar.base-url