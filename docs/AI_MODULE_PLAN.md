# AI 模块详细计划书

> 本文档规划 AI 模块的完整实现方案，包括架构设计、功能模块、开发阶段和接口规范。

---

## 一、模块概述

### 1.1 目标
实现一个支持多模型、多角色的 AI 对话系统，具备以下核心能力：
- **AI 单聊**：用户与 AI 角色一对一对话，支持流式输出
- **AI 群聊**：AI 角色参与群聊，按关键词/概率触发回复
- **RAG 记忆**：基于向量检索的长期记忆，让 AI 记住用户偏好
- **API Key 管理**：用户自带 API Key，服务端加密存储

### 1.2 技术选型

| 组件 | 技术 | 说明 |
|------|------|------|
| AI 调用 | OpenAI 兼容协议 | 支持 OpenAI / DeepSeek / 通义千问等 |
| 流式输出 | WebSocket + SSE | 实时推送 AI 生成内容 |
| 向量存储 | Milvus / Qdrant | AI 记忆向量检索 |
| API Key 加密 | AES-256-GCM | 服务端加密存储 |
| 记忆摘要 | AI 自动生成 | 定期总结对话生成记忆 |

### 1.3 现有基础

**已完成**：
- 数据库表设计（`ai_profile`、`ai_group_config`、`ai_memory`）
- API 接口文档定义（6.1-6.8）
- WebSocket 消息类型定义（`AI_CHAT`、`AI_STREAM`）
- 消息表 `sender_type` 字段支持 AI（1-AI）

**待实现**：
- 全部后端代码（Entity、Mapper、Service、Controller）
- AI 调用客户端（HTTP/WebSocket 流式）
- RAG 记忆系统
- API Key 加密/解密工具
- 单元测试

---

## 二、架构设计

### 2.1 分层架构

```
┌─────────────────────────────────────────────┐
│                  Controller                  │
│              AiController.java               │
├─────────────────────────────────────────────┤
│                   Service                    │
│  ┌──────────┐ ┌──────────┐ ┌──────────────┐ │
│  │AiService │ │AiChat    │ │AiMemory      │ │
│  │          │ │Service   │ │Service       │ │
│  └──────────┘ └──────────┘ └──────────────┘ │
├─────────────────────────────────────────────┤
│                  Client                      │
│  ┌──────────┐ ┌──────────┐ ┌──────────────┐ │
│  │OpenAI    │ │Embedding │ │VectorStore   │ │
│  │Client    │ │Client    │ │Client        │ │
│  └──────────┘ └──────────┘ └──────────────┘ │
├─────────────────────────────────────────────┤
│                  Mapper                      │
│  AiProfileMapper │ AiGroupConfigMapper │     │
│  AiMemoryMapper  │                       │   │
└─────────────────────────────────────────────┘
```

### 2.2 核心流程

**AI 单聊流程**：
```
客户端 → WebSocket AI_CHAT → AiChatService.handleAiChat()
  → 1. 查询 AI 角色信息（AiProfile）
  → 2. 解密 API Key（AesKeyUtil）
  → 3. 构建对话上下文（历史消息 + 记忆）
  → 4. 调用 OpenAI 兼容 API（流式）
  → 5. 逐 chunk 推送 AI_STREAM 给客户端
  → 6. 完成后保存消息 + 更新记忆
```

**AI 群聊触发流程**：
```
群消息 → ChatWebSocketHandler → AiChatService.checkGroupAiTrigger()
  → 1. 查询群 AI 配置（AiGroupConfig）
  → 2. 检查关键词匹配 / 概率触发
  → 3. 触发则执行 AI 回复流程
  → 4. 推送 GROUP_MESSAGE 给群成员
```

**RAG 记忆流程**：
```
对话累积 → AiMemoryService.summarizeIfNeeded()
  → 1. 统计对话轮数
  → 2. 达到阈值时调用 AI 生成摘要
  → 3. 生成 Embedding 向量
  → 4. 存入 Milvus + MySQL
  → 下次对话时检索相关记忆注入上下文
```

---

## 三、功能模块详细设计

### 3.1 AI 角色管理（AiService）

**职责**：AI 角色的 CRUD 操作

| 方法 | 说明 |
|------|------|
| `listAiProfiles(userId, page, size)` | 获取用户的 AI 列表 |
| `createAiProfile(userId, request)` | 创建 AI 角色 |
| `updateAiProfile(userId, aiId, request)` | 修改 AI 角色 |
| `deleteAiProfile(userId, aiId)` | 删除 AI 角色 |
| `getAiProfile(aiId)` | 获取 AI 角色详情 |

**API Key 加密**：
- 创建/修改时：`AesKeyUtil.encrypt(apiKey)` → 存入 `api_key_enc`
- 调用时：`AesKeyUtil.decrypt(apiKeyEnc)` → 传给 OpenAI Client
- 密钥来源：配置文件 `ai.encryption.key`

### 3.2 AI 对话服务（AiChatService）

**职责**：处理 AI 对话请求，管理对话上下文

| 方法 | 说明 |
|------|------|
| `handleAiChat(userId, aiId, sessionId, content)` | 处理 AI 单聊 |
| `handleGroupAiTrigger(groupId, message)` | 处理群 AI 触发 |
| `buildContext(aiId, userId, sessionId)` | 构建对话上下文 |
| `streamChat(context, apiKey, callback)` | 流式调用 AI |
| `saveAiMessage(aiId, sessionId, content)` | 保存 AI 消息 |

**对话上下文构建**：
```
System Prompt（AI 人设）
  + RAG 检索记忆（top 3 相关记忆）
  + 最近 N 轮历史消息（N=10）
  + 当前用户输入
```

**流式输出**：
- 使用 WebSocket 逐 chunk 推送 `AI_STREAM`
- 每个 chunk 包含 `content`（增量文本）和 `done`（是否结束）
- 结束时推送完整 `content` + `messageId`

### 3.3 AI 记忆服务（AiMemoryService）

**职责**：AI 长期记忆的存储、检索和更新

| 方法 | 说明 |
|------|------|
| `getMemories(aiId, userId, page, size)` | 获取记忆列表 |
| `searchRelevantMemories(aiId, userId, query)` | 向量检索相关记忆 |
| `summarizeIfNeeded(aiId, userId, sessionId)` | 按需生成记忆摘要 |
| `deleteMemory(memoryId)` | 删除记忆 |

**记忆生成策略**：
- 每积累 20 轮对话触发一次摘要
- 调用 AI 生成摘要 + 提取关键词
- 生成 Embedding 向量存入 Milvus
- 关键词存入 MySQL `keywords` 字段（逗号分隔）

**向量检索策略**：
- 将用户当前输入生成 Embedding
- 在 Milvus 中搜索 top 3 相似记忆
- 相似度阈值 > 0.7 才注入上下文
- 降级策略：Milvus 不可用时用关键词模糊匹配

### 3.4 AI 群配置服务（AiGroupConfigService）

**职责**：群 AI 触发规则管理

| 方法 | 说明 |
|------|------|
| `createConfig(groupId, aiId, request)` | 创建群 AI 配置 |
| `getConfigs(groupId)` | 获取群 AI 配置列表 |
| `updateConfig(configId, request)` | 修改配置 |
| `deleteConfig(configId)` | 删除配置 |
| `checkTrigger(groupId, message)` | 检查是否触发 AI |

**触发规则**：
- **关键词触发**：消息包含 `triggerKeywords` 中的任一关键词
- **概率触发**：随机数 < `triggerProbability` 时触发
- **@触发**：消息中 @AI 名称时必定触发（优先级最高）
- **冷却时间**：AI 回复后 30 秒内不再触发

### 3.5 OpenAI 兼容客户端（OpenAiClient）

**职责**：封装 OpenAI 兼容 API 调用

| 方法 | 说明 |
|------|------|
| `chatCompletion(request)` | 同步对话 |
| `streamChatCompletion(request, callback)` | 流式对话 |
| `createEmbedding(text)` | 生成 Embedding 向量 |

**支持的模型提供商**：
| 提供商 | Base URL | 模型示例 |
|--------|----------|---------|
| openai | https://api.openai.com/v1 | gpt-4, gpt-3.5-turbo |
| deepseek | https://api.deepseek.com/v1 | deepseek-chat |
| qwen | https://dashscope.aliyuncs.com/compatible-mode/v1 | qwen-turbo |
| zhipu | https://open.bigmodel.cn/api/paas/v4 | glm-4 |
| custom | 用户自定义 | - |

**配置**：
```yaml
ai:
  providers:
    openai:
      base-url: https://api.openai.com/v1
      default-model: gpt-3.5-turbo
    deepseek:
      base-url: https://api.deepseek.com/v1
      default-model: deepseek-chat
  embedding:
    provider: openai
    model: text-embedding-3-small
    dimension: 1536
  context:
    max-history-rounds: 10
    max-memory-count: 3
    memory-similarity-threshold: 0.7
  memory:
    summarize-threshold: 20
    max-summary-length: 500
  encryption:
    key: ${AI_ENCRYPTION_KEY:default-key-for-dev-only}
```

---

## 四、数据库变更

### 4.1 新增表

无需新增表，现有 `ai_profile`、`ai_group_config`、`ai_memory` 表已满足需求。

### 4.2 可能的调整

| 表 | 字段 | 变更 | 原因 |
|----|------|------|------|
| `ai_profile` | `temperature` | 新增 DECIMAL(3,2) DEFAULT 0.7 | AI 回复创造性参数 |
| `ai_profile` | `max_tokens` | 新增 INT DEFAULT 2048 | AI 回复最大长度 |
| `ai_profile` | `system_prompt` | 新增 TEXT | 系统提示词（与 persona 合并） |
| `ai_memory` | `importance` | 新增 DECIMAL(3,2) DEFAULT 0.5 | 记忆重要度评分 |
| `ai_group_config` | `cooldown_seconds` | 新增 INT DEFAULT 30 | AI 回复冷却时间 |

### 4.3 Redis 缓存设计

```
# AI 对话上下文缓存
Key: ai:context:{aiId}:{userId}
Type: List
Value: 最近N轮对话JSON
TTL: 3600秒

# AI 群冷却时间
Key: ai:cooldown:{groupId}:{aiId}
Type: String
Value: 上次回复时间戳
TTL: cooldown_seconds

# AI 角色信息缓存
Key: ai:profile:{aiId}
Type: Hash
Value: AI角色信息
TTL: 1800秒
```

---

## 五、接口实现清单

### 5.1 REST 接口

| 接口 | 方法 | Controller | Service |
|------|------|------------|---------|
| `GET /ai/list` | listAiProfiles | AiController | AiService |
| `POST /ai/create` | createAiProfile | AiController | AiService |
| `PUT /ai/{aiId}` | updateAiProfile | AiController | AiService |
| `DELETE /ai/{aiId}` | deleteAiProfile | AiController | AiService |
| `POST /ai/chat` | chatSync | AiController | AiChatService |
| `GET /ai/{aiId}/memories` | getMemories | AiController | AiMemoryService |
| `POST /ai/group/{groupId}/config` | createGroupConfig | AiController | AiGroupConfigService |
| `GET /ai/group/{groupId}/configs` | getGroupConfigs | AiController | AiGroupConfigService |

### 5.2 WebSocket 接口

| 消息类型 | 方向 | 处理类 |
|---------|------|--------|
| `AI_CHAT` | 客户端→服务端 | AiChatService.handleAiChat() |
| `AI_STREAM` | 服务端→客户端 | AiChatService 流式推送 |

---

## 六、文件清单

### 6.1 新增文件

**Entity**：
- `server/.../entity/AiProfile.java`
- `server/.../entity/AiGroupConfig.java`
- `server/.../entity/AiMemory.java`

**Mapper**：
- `server/.../mapper/AiProfileMapper.java`
- `server/.../mapper/AiGroupConfigMapper.java`
- `server/.../mapper/AiMemoryMapper.java`

**DTO**：
- `server/.../dto/request/CreateAiProfileRequest.java`
- `server/.../dto/request/UpdateAiProfileRequest.java`
- `server/.../dto/request/AiChatRequest.java`
- `server/.../dto/request/AiGroupConfigRequest.java`
- `server/.../dto/response/AiProfileResponse.java`
- `server/.../dto/response/AiChatResponse.java`
- `server/.../dto/response/AiMemoryResponse.java`
- `server/.../dto/response/AiGroupConfigResponse.java`

**Service**：
- `server/.../service/AiService.java`
- `server/.../service/AiChatService.java`
- `server/.../service/AiMemoryService.java`
- `server/.../service/AiGroupConfigService.java`

**Client**：
- `server/.../client/OpenAiClient.java`
- `server/.../client/EmbeddingClient.java`
- `server/.../client/VectorStoreClient.java`

**Util**：
- `server/.../util/AesKeyUtil.java`

**Controller**：
- `server/.../controller/AiController.java`

**Config**：
- `server/.../config/AiConfig.java`

**Test**：
- `server/.../service/AiServiceTest.java`
- `server/.../service/AiChatServiceTest.java`
- `server/.../service/AiMemoryServiceTest.java`
- `server/.../service/AiGroupConfigServiceTest.java`
- `server/.../controller/AiControllerTest.java`
- `server/.../client/OpenAiClientTest.java`
- `server/.../util/AesKeyUtilTest.java`

### 6.2 修改文件

- `ChatWebSocketHandler.java`：添加 AI_CHAT 消息处理
- `MessageTypes.java`：可能新增 AI 相关消息类型
- `application.yml`：添加 AI 配置项
- `schema.sql`：添加 AI 相关表结构

---

## 七、开发阶段

### 阶段一：AI 角色管理（基础 CRUD）

**目标**：完成 AI 角色的创建、查询、修改、删除

**任务**：
1. 创建 Entity（AiProfile）+ Mapper
2. 实现 AiService（CRUD）
3. 实现 AiController（REST 接口 6.1-6.4）
4. 实现 AesKeyUtil（API Key 加密/解密）
5. 编写单元测试

**验收标准**：
- 可通过 REST API 创建/查询/修改/删除 AI 角色
- API Key 加密存储，数据库中无明文
- 单元测试覆盖率 ≥ 80%

### 阶段二：AI 单聊（流式对话）

**目标**：实现 AI 单聊功能，支持流式输出

**任务**：
1. 实现 OpenAiClient（HTTP 调用 + 流式解析）
2. 实现 AiChatService（对话上下文构建 + 流式推送）
3. 修改 ChatWebSocketHandler 处理 AI_CHAT
4. 实现 AI_STREAM 消息推送
5. 编写单元测试

**验收标准**：
- 客户端发送 AI_CHAT，服务端流式返回 AI_STREAM
- 支持 OpenAI / DeepSeek / 通义千问等模型
- 对话上下文包含历史消息
- 单元测试覆盖率 ≥ 80%

### 阶段三：AI 群聊（触发机制）

**目标**：AI 参与群聊，按规则触发回复

**任务**：
1. 创建 Entity（AiGroupConfig）+ Mapper
2. 实现 AiGroupConfigService
3. 实现 AiController（REST 接口 6.7-6.8）
4. 修改 ChatWebSocketHandler 处理群消息触发
5. 实现关键词匹配 + 概率触发 + 冷却时间
6. 编写单元测试

**验收标准**：
- 群消息可触发 AI 回复
- 支持关键词/概率/@触发
- AI 回复后冷却 30 秒
- 单元测试覆盖率 ≥ 80%

### 阶段四：RAG 记忆系统

**目标**：AI 具备长期记忆能力

**任务**：
1. 创建 Entity（AiMemory）+ Mapper
2. 实现 EmbeddingClient（向量生成）
3. 实现 VectorStoreClient（Milvus/Qdrant 操作）
4. 实现 AiMemoryService（记忆存储 + 检索 + 摘要）
5. 对话时注入相关记忆到上下文
6. 实现 AiController（REST 接口 6.6）
7. 编写单元测试

**验收标准**：
- AI 可记住用户偏好和对话历史
- 向量检索相似度 > 0.7 的记忆注入上下文
- Milvus 不可用时降级为关键词匹配
- 单元测试覆盖率 ≥ 80%

### 阶段五：优化与完善

**目标**：性能优化、错误处理、体验提升

**任务**：
1. Redis 缓存 AI 上下文和角色信息
2. AI 调用失败重试机制
3. API Key 余额/额度检查
4. AI 对话限流（防止滥用）
5. 前端 AI 对话界面适配
6. 集成测试

**验收标准**：
- AI 响应延迟 < 3s（首 token）
- 限流：每用户每分钟最多 20 次 AI 请求
- 错误友好提示
- 集成测试通过

---

## 八、安全设计

### 8.1 API Key 安全
- AES-256-GCM 加密存储，密钥从环境变量读取
- 日志中禁止输出 API Key（脱敏处理）
- API Key 仅在调用 AI 时解密，不缓存明文

### 8.2 权限控制
- AI 角色仅所有者可修改/删除
- 群 AI 配置仅群主/管理员可操作
- AI 对话限流，防止 API 滥用

### 8.3 输入验证
- AI 人设长度限制（最长 2000 字符）
- 对话内容长度限制（最长 4000 字符）
- 群 AI 触发关键词数量限制（最多 10 个）

---

## 九、风险与应对

| 风险 | 影响 | 应对措施 |
|------|------|---------|
| Milvus/Qdrant 部署复杂 | RAG 功能不可用 | 降级为关键词匹配，记忆仍存 MySQL |
| AI API 不稳定 | 对话中断 | 重试 3 次 + 友好错误提示 |
| API Key 泄露 | 用户财产损失 | AES 加密 + 环境变量密钥 + 日志脱敏 |
| 流式输出断连 | 用户体验差 | 客户端重连后补发完整 AI 回复 |
| AI 滥用 | API 费用过高 | 限流 + 每日调用上限 |

---

## 十、依赖关系

```
阶段一（AI 角色 CRUD）
  ↓
阶段二（AI 单聊）← 依赖阶段一的 AI 角色数据
  ↓
阶段三（AI 群聊）← 依赖阶段二的 AI 对话能力
  ↓
阶段四（RAG 记忆）← 依赖阶段二的对话上下文
  ↓
阶段五（优化完善）← 依赖全部功能完成
```

每个阶段独立可测试，阶段间无强耦合。