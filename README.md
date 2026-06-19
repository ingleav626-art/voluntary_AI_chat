# voluntary_AI_chat

这是一个主动式ai的仓库

## 项目规范
请参考 [AI开发规范文档](AI_SPEC.md) 了解项目开发规范。

# Java 桌面 AI 特色聊天软件 — 技术与架构规划

下面我按"技术选型 → 整体架构 → 模块拆解 → 关键难点 → 开发路线"五个层面给你完整规划。

## 一、技术选型

### 1. 客户端（桌面端）

| 技术 | 用途 | 说明 |
|---|---|---|
| JavaFX + FXML | UI 框架 | 现代 Java 桌面首选，支持 CSS 美化、MVVM |
| ControlsFX / AtlantaFX | UI 组件库 | 提供美观的对话框、气泡、列表 |
| WebView (JavaFX 内置) | 富文本/Markdown 渲染 | AI 回复用 Markdown 渲染 |
| Netty / Java-WebSocket | WebSocket 客户端 | 实时消息收发 |
| Jackson | JSON 序列化 | 与后端统一 |
| DJL (Deep Java Library) | 本地小模型推理 | 句子完整性识别 |

> 不建议用 Swing，JavaFX 在动画、CSS、富文本上更适合聊天软件。

### 2. 服务端

| 技术 | 用途 |
|---|---|
| Spring Boot 3.x (JDK 25) | 后端主框架 |
| Spring Security + JWT | 鉴权 |
| Netty / Spring WebSocket (STOMP) | 实时通信 |
| MyBatis-Plus | ORM |
| Redis | 在线状态、Token、消息队列缓存、未读数 |
| RabbitMQ / RocketMQ | 异步消息（AI 主动聊天、记忆总结任务） |
| MinIO | 图片/文件对象存储 |

### 3. 数据存储

| 数据库 | 用途 |
|---|---|
| MySQL 8 | 用户、好友、群、消息元数据 |
| Redis | 在线状态、会话缓存、限流 |
| Milvus / Qdrant（向量数据库） | AI 记忆向量检索 |
| MongoDB（可选） | 聊天记录大文本存储，水平扩展友好 |

### 4. AI 能力

| 能力 | 方案 |
|---|---|
| 大模型对话 | 用户自带 API Key，后端做代理转发（OpenAI 兼容协议） |
| 记忆管理 | 向量化历史 + 检索增强（RAG） |
| 句子完整性识别 | 本地小模型（BERT-tiny / ONNX），DJL 加载 |
| AI 主动聊天 | 定时任务 + 概率触发 + 上下文摘要 |

## 二、整体架构

```plainText
┌─────────────────────────────────────────────┐
│            JavaFX 桌面客户端                  │
│  ┌─────────┐ ┌──────────┐ ┌──────────────┐  │
│  │ UI 层    │ │ WebSocket│ │ 本地模型推理  │  │
│  │(FXML/MVVM)│ │  Client  │ │ (句子完整性)  │  │
│  └─────────┘ └──────────┘ └──────────────┘  │
└──────────────────┬──────────────────────────┘
                   │ HTTPS + WSS
┌──────────────────▼──────────────────────────┐
│            Spring Boot 服务端                 │
│  ┌──────────────────────────────────────┐   │
│  │ Gateway / Controller 层 (REST + WS)   │   │
│  ├──────────────────────────────────────┤   │
│  │ Service 层                            │   │
│  │  用户│好友│群聊│消息│AI代理│记忆管理    │   │
│  ├──────────────────────────────────────┤   │
│  │ AI 编排层 (多AI群路由 / 主动聊天调度)  │   │
│  └──────────────────────────────────────┘   │
└───┬──────────┬──────────┬──────────┬────────┘
    │          │          │          │
┌───▼───┐ ┌───▼───┐ ┌────▼────┐ ┌───▼────┐
│ MySQL │ │ Redis │ │ Milvus  │ │ MinIO  │
└───────┘ └───────┘ └─────────┘ └────────┘
```

## 三、模块拆解与关键设计

### 1. 用户系统
- **UID 生成**：雪花算法（Snowflake），保证分布式唯一且有序。
- **在线状态**：Redis 维护 `uid -> WebSocket session`，好友查询时读 Redis，状态变更通过 WebSocket 推送给好友列表。
- **好友关系**：MySQL 表 `friend_relation(uid, friend_uid, remark, status)`，加好友走"申请-同意"流程。

### 2. 单人聊天
- **消息模型**：统一抽象为 `Message`，包含 `type(TEXT/IMAGE/AI/RECALL/FORWARD)`。
- **撤回逻辑**：
  - 人-人：消息表加 `create_time`，撤回时校验 `now - create_time < 2min`。
  - 人-AI：AI 消息不强制时间限制，客户端可随时撤回（仅本地 + 通知服务端删除）。
- **背景图**：客户端本地存储，按 `session_id` 关联，不上传服务端（隐私 + 省流量）。
- **转发**：转发本质是把若干 `Message` 打包成 `ForwardPackage`，作为一条新消息发送。

### 3. 群聊
- **群角色**：`owner / admin / member`，权限用枚举 + 策略模式校验（踢人、加管理员等）。
- **多 AI 群（特色）**：群内可挂多个 AI，每个 AI 有独立人设和 API Key。消息路由策略：
  - `@某个AI` → 定向回复
  - 未 @ → 按概率/轮询/关键词触发某个 AI
- **群公告置顶**：公告表加 `is_pinned` 字段，置顶项单独查询。

### 4. 特色功能（核心难点）

#### a) AI 主动找用户聊天
```plainText
定时任务(每用户随机间隔) → 读取最近N条记忆摘要
→ 生成"主动聊天"提示词 → 调用AI → 推送消息给在线用户
```
- 用 RabbitMQ 延迟队列实现"随机间隔"，避免定时任务集中。

#### b) 记忆管理（AI 日记 + 向量检索）
```plainText
聊天达到阈值(如20条) → 异步任务总结为"日记"
→ 存MySQL(摘要) + Milvus(向量)
→ 下次对话时: 当前问题向量化 → Milvus检索Top-K相关记忆 → 拼入Prompt
```
- 向量化用 Embedding 模型（如 `text-embedding-3-small` 或本地 BGE）。
- 这是典型的 **RAG** 架构，能解决长上下文记忆问题。

#### c) 本地小模型识别句子完整性
- 用 DJL 加载一个轻量分类模型（可基于 BERT-tiny 微调）。
- 客户端发送前先过模型：`is_complete(text) -> bool`，不完整则拦截。
- 模型放客户端本地推理，**不上传文字**，保护隐私。

### 5. AI 代理层（用户自带 Key）
- 后端不存明文 Key，加密存储（AES + 用户密码派生密钥）。
- 调用时解密，代理转发到 OpenAI 兼容接口，流式返回给客户端。
- 这样支持 OpenAI / DeepSeek / 通义 / 智谱 等任何兼容接口。

## 四、关键数据库表（简版）

```sql
-- 用户
user(uid PK, phone, username, password_hash, salt, bio, avatar, create_time)

-- 好友关系
friend(id PK, uid, friend_uid, remark, status, create_time)

-- 群
chat_group(group_id PK, name, owner_uid, announcement, announcement_pinned, create_time)

-- 群成员
group_member(group_id, uid, role, join_time)

-- AI 角色
ai_profile(ai_id PK, owner_uid, name, persona, model_provider, api_key_enc, is_group)

-- 消息
message(msg_id PK, session_id, sender_uid, sender_type(USER/AI),
        msg_type, content, ref_msg_id, create_time, is_recalled)

-- AI 记忆摘要
ai_memory(id PK, ai_id, user_uid, summary, vector_id, create_time)
```

## 五、推荐技术栈总结

```plainText
后端:  Spring Boot 3 + MyBatis-Plus + Spring Security + JWT
通信:  WebSocket(STOMP) + REST
存储:  MySQL + Redis + Milvus + MinIO
消息:  RabbitMQ(延迟队列做AI主动聊天)
AI:    OpenAI兼容协议代理 + DJL(本地模型) + Embedding(RAG)
桌面:  JavaFX + FXML + AtlantaFX + WebView
构建:  Maven(jdk17)
部署:  Docker + docker-compose
```

## 六、开发路线建议（分阶段）

| 阶段 | 内容 | 周期参考 |
|---|---|---|
| P0 | 用户系统 + 单聊文字 + WebSocket 打通 | 基础 |
| P1 | 群聊 + 好友/群管理 + 图片消息 | 核心 |
| P2 | AI 接入（单聊 AI + 自定义人设 + Key 管理） | 特色起步 |
| P3 | 多 AI 群 + AI 主动聊天 + 记忆管理(RAG) | 特色核心 |
| P4 | 本地句子完整性模型 + 撤回/转发/背景图 | 体验打磨 |
| P5 | 文件传输、TTS、图片识别、随机陌生人聊天 | 未来扩展 |

## 七、几个关键提醒
1. **API Key 安全**：用户 Key 一定加密存储，前端不直连第三方 API（避免 CORS 和 Key 泄露）。
2. **本地模型体积**：句子完整性模型控制在 50MB 以内，否则客户端打包臃肿。
3. **向量库选型**：个人/小规模项目用 `Qdrant` 比 Milvus 更轻量，部署简单。
4. **桌面端打包**：用 `jpackage` 生成 `.exe` / `.dmg`，内嵌 JRE。
5. **JavaFX 线程**：WebSocket 回调不在 JavaFX UI 线程，更新 UI 必须用 `Platform.runLater()`。

---

如果你确认这个技术方向，我可以帮你：
- 生成项目骨架（Maven 多模块：`client` / `server` / `common`）
- 设计完整的数据库 DDL
- 搭建 Spring Boot + WebSocket 基础通信
