# AI 隐私架构设计文档

> 本文档定义 AI API Key 的隐私架构，确保用户 API Key 不离开本地设备。
> 状态：设计阶段，待评审
> 日期：2026-06-24

---

## 一、设计目标

### 核心原则

| 场景 | 隐私级别 | API Key 存放 | AI 调用方 |
|------|---------|-------------|-----------|
| 单聊 AI | 🔒 绝对隐私 | 本地 H2 | 本地内嵌后端 |
| 纯 AI 群（1 真人 + N AI） | 🔒 绝对隐私 | 本地 H2 | 本地内嵌后端 |
| 多真人群 - 隐私优先 | 🔒 隐私 | 本地 H2 | 本地内嵌后端（回复经云端广播） |
| 多真人群 - 功能优先 | ⚠️ 功能优先 | 云端 MySQL（群主授权） | 云端服务器 |

### 不可妥协的底线

1. **单聊 AI 的 API Key 绝对不上云**
2. **纯 AI 群（仅 1 真人）的 API Key 绝对不上云**
3. **多真人群必须提供"隐私优先"模式**，用户可选择 Key 不上云

---

## 二、现状调研

### 2.1 实际使用的通信组件

| 组件 | 类型 | 是否实际使用 | 说明 |
|------|------|-------------|------|
| `WebSocketClient` | 单连接 | ✅ 实际使用 | `ChatViewModel`/`AiViewModel` 都用它，连 `getBaseUrlByMode(mode)` |
| `BaseHttpService` | 单 baseUrl | ✅ 实际使用 | AI REST 请求用它，走 `currentBaseUrl` |
| `DualWebSocketManager` | 双连接 | ❌ 未集成 | 设计了 AI_* 走本地的路由，但从未被调用，是死代码 |

### 2.2 各模式 AI 实际行为（修正版）

**LOCAL 模式：**
```
内嵌后端：启动 ✅
WebSocketClient → localhost:8080 ✅
AI_CHAT → 本地内嵌后端处理 ✅
API Key → 本地 H2 加密存储 ✅
结论：AI 可用，Key 不上云（符合隐私）
缺陷：LOCAL 模式下若云端可用且未开隐私模式，currentBaseUrl 会被覆盖到云端，
      导致 AI REST 请求（角色管理）发往云端，与 WebSocket 路由不一致
```

**CLOUD 模式：**
```
内嵌后端：不启动 ❌
WebSocketClient → 云端服务器
AI_CHAT → 云端 ChatWebSocketHandler 处理 → 云端 AiChatService → 云端调用 AI 提供商
AI REST（/ai/*）→ 云端 AiController → 云端 MySQL 存 Key
API Key → 云端 MySQL 加密存储 ❌
结论：AI 能用，但 API Key 完全上云，零隐私
```

### 2.3 五个核心矛盾

#### 矛盾1：CLOUD 模式不启动内嵌后端

[Launcher.java:88-93](file:///d:/voluntary_AI_chat/client/src/main/java/org/example/client/Launcher.java) CLOUD 模式直接 break，不启动内嵌后端。导致 CLOUD 模式下无本地 AI 服务。

#### 矛盾2：DualWebSocketManager 未集成

[DualWebSocketManager.java](file:///d:/voluntary_AI_chat/client/src/main/java/org/example/client/service/DualWebSocketManager.java) 设计了 AI_* 走本地、其他走云端的路由，但 `ChatViewModel` 和 `AiViewModel` 实际都用 `WebSocketClient`（单连接），DualWebSocketManager 从未被调用。

#### 矛盾3：REST 请求与 WebSocket 路由不一致

[BaseHttpService.java:79](file:///d:/voluntary_AI_chat/client/src/main/java/org/example/client/service/BaseHttpService.java) 所有 REST 请求用 `currentBaseUrl`。LOCAL 模式下云端可用时 currentBaseUrl 被覆盖到云端，但 WebSocketClient 仍连本地 → AI 角色管理（REST）走云端，AI 对话（WebSocket）走本地，数据不一致。

#### 矛盾4：AI 模块在 server 包，云端部署时被加载

`EmbeddedServerStarter` 的 `@ComponentScan("com.voluntary.chat")` 扫描整个 server 模块。云端部署 server jar 时，`AiController`、`AiService`、`AiChatService` 全部注册到云端，API Key 存云端 MySQL。

#### 矛盾5：local 配置声称 ai-only 但未真正排除

[application-local.yml:65-72](file:///d:/voluntary_AI_chat/server/src/main/resources/application-local.yml) 声明 `ai-only: true` 和 `exclude-modules`，但无代码消费此配置，ComponentScan 全扫描。

### 2.4 群成员结构

| 表 | 存什么 | 关键字段 |
|----|--------|---------|
| `group_member` | **只存真人成员** | `user_id`, `role` |
| `ai_group_config` | AI 挂载配置 | `group_id`, `ai_id`, `trigger_keywords` |

"纯 AI 群"判定：`group_member` 中该群只有 1 个真人（群主）+ `ai_group_config` 有 N 条 AI 配置。

[ServerConnectionManager.requiresCloudConnection()](file:///d:/voluntary_AI_chat/client/src/main/java/org/example/client/config/ServerConnectionManager.java) 已实现此判断逻辑，但未被调用。

---

## 三、架构方案

### 3.1 总体架构

```
┌─────────────────────────────────────────────────────────┐
│                      客户端 JVM                          │
│  ┌──────────────┐    ┌──────────────────────────────┐  │
│  │  JavaFX UI   │    │   内嵌后端（始终启动）        │  │
│  │  ChatViewModel│───▶│   localhost:8080             │  │
│  │  AiViewModel  │    │   ┌─────────────────────┐   │  │
│  └──────┬───────┘    │   │ AI 模块（本地）      │   │  │
│         │            │   │  AiController        │   │  │
│         │ REST/WS    │   │  AiChatService       │   │  │
│         │ (AI强制本地)│   │  OpenAiClient ───────┼───┼──┼──▶ AI 提供商
│         │            │   │  H2 本地数据库       │   │  │   (Key 不上云)
│         │            │   └─────────────────────┘   │  │
│         │            └──────────────────────────────┘  │
│         │                                              │
│         │ 真人消息/群消息                                │
│         ▼                                              │
│  ┌──────────────┐                                      │
│  │ WebSocketClient│──── 云端服务器 ────┐               │
│  │ (真人/群广播)  │                    │               │
│  └──────────────┘                    │               │
│                                       ▼               │
└───────────────────────────────────────────────────────┐
                                       │  云端服务器     │
                                       │  真人聊天模块   │
                                       │  群消息广播     │
                                       │  （无 AI 模块） │
                                       └────────────────┘
```

### 3.2 AI 模块云端隔离方案

**采用条件化加载（@Profile）方案**：

在 server 模块的 AI 相关 Bean 上添加 `@Profile("!cloud")`，云端 profile 下不注册 AI Bean：

```java
// AiController.java
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Profile("!cloud")  // 云端不加载
public class AiController { ... }

// AiChatService.java
@Service
@RequiredArgsConstructor
@Profile("!cloud")  // 云端不加载
public class AiChatService { ... }

// AiService.java, AiMemoryService.java, AiGroupConfigService.java 同理
// OpenAiClient.java, EmbeddingClient.java, VectorStoreClient.java 同理
```

云端 `ChatWebSocketHandler` 中的 `triggerGroupAi` 逻辑需要相应调整（见 3.4）。

### 3.3 客户端通信路由调整

#### 调整1：CLOUD 模式也启动内嵌后端

修改 [Launcher.java](file:///d:/voluntary_AI_chat/client/src/main/java/org/example/client/Launcher.java)：

```java
case CLOUD:
    // 云端模式：也启动内嵌后端（仅 AI 组件），用于本地 AI 隐私处理
    LOG.info("云端模式：启动内嵌后端（AI 隐私模块）");
    startEmbeddedServer();
    break;
```

#### 调整2：AI 请求强制走本地

`BaseHttpService` 新增本地请求构造方法，`AiService` 强制使用本地地址：

```java
// BaseHttpService.java 新增
protected HttpRequest.Builder buildLocalGetRequest(final String path) {
    final String url = ClientConfig.getInstance().getLocalBaseUrl() + path;
    // ... 其余同 buildGetRequest
}

// AiService.java 改用 buildLocalGetRequest / buildLocalPostRequest
```

#### 调整3：WebSocket 双连接集成

启用 `DualWebSocketManager` 替代 `WebSocketClient` 的 AI 消息路由：

- `AI_CHAT`、`AI_STREAM` → 本地连接（localhost:8080）
- `SEND_MESSAGE`、`GROUP_MESSAGE`、`RECEIVE_MESSAGE` → 云端连接

或更简单的方案：`WebSocketClient` 内部根据消息类型选择目标连接。

### 3.4 群聊 AI 双模式设计

#### 纯 AI 群 / 单聊 AI（绝对隐私）

```
前端 AI_CHAT → 本地 WebSocket → 本地 AiChatService
                                    ↓
                              本地 H2 存消息
                              本地调用 AI 提供商（用本地 Key）
                                    ↓
                              本地 WebSocket 推送 AI_STREAM 给前端
```

完全不经过云端。

#### 多真人群 - 隐私优先模式（本地生成 + 云端广播）

```
群成员发消息 → 云端 WebSocket 广播给所有真人成员
                    ↓
              本地客户端收到群消息
                    ↓
              本地检查 AI 触发（关键词/@/概率）
                    ↓ (触发)
              本地调用 AI 提供商（用本地 Key）
                    ↓
              AI 回复生成完成
                    ↓
              通过云端 WebSocket 广播 AI 回复给群成员
```

**关键点**：
- API Key 不上云 ✅
- AI 调用在本地 ✅
- AI 回复内容经云端广播（群聊固有代价，回复内容对群成员可见）
- 每个群成员本地都会独立触发 AI（需去重机制，见 3.5）

#### 多真人群 - 功能优先模式

```
群消息 → 云端广播
       → 云端检查 AI 触发
       → 云端调用 AI 提供商（用群主授权的云端 Key）
       → 云端广播 AI 回复
```

群主创建群 AI 配置时明确授权 API Key 上云，需要 `AI_ENCRYPTION_KEY`。

### 3.5 隐私优先模式的关键问题：AI 触发去重

隐私优先模式下，每个群成员本地都会收到群消息并检查 AI 触发。如果都触发，会产生多个 AI 回复。需要去重机制：

**方案：触发权竞争（基于一致性哈希）**

```
群消息到达 → 本地计算 hash(messageId + aiId) % 群真人数
           → 若结果 == 自己在群内的序号 → 触发 AI
           → 否则 → 不触发
```

这样保证每条消息只由一个群成员触发 AI，且该成员本地调用后广播给所有人。

**替代方案：群主独占触发权**

只有群主本地触发 AI，其他成员不触发。更简单但群主不在线时 AI 不响应。

### 3.6 API Key 加密密钥分层

| 模式 | AI_ENCRYPTION_KEY | 用途 |
|------|-------------------|------|
| LOCAL | 可选（本地 H2，可用机器绑定密钥或固定开发密钥） | 加密本地存储的 Key |
| CLOUD 隐私优先 | 不需要（Key 不上云） | - |
| CLOUD 功能优先 | **必需** | 加密云端存储的群主授权 Key |

`AiService.getEncryptionKey()` 的校验逻辑调整为：仅在云端 profile + 功能优先模式时强制要求。

---

## 四、实施计划

### 阶段一：AI 模块云端隔离（最小可用）

**目标**：云端不再加载 AI 模块，API Key 无法上云

1. server 模块 AI Bean 添加 `@Profile("!cloud")`
2. 云端 `ChatWebSocketHandler.triggerGroupAi` 移除或加条件判断
3. 云端部署配置确认 AI 模块不注册
4. 测试：云端 `/api/ai/*` 接口返回 404

**风险**：现有 CLOUD 模式用户 AI 功能暂时不可用（需阶段二补齐）

### 阶段二：客户端本地 AI 能力打通

**目标**：CLOUD 模式下客户端能本地调用 AI

1. 修改 Launcher，CLOUD 模式启动内嵌后端
2. BaseHttpService 新增本地请求方法，AiService 强制走本地
3. 集成 DualWebSocketManager 或 WebSocketClient 双连接路由
4. 测试：CLOUD 模式下单聊 AI 可用，Key 存本地 H2

### 阶段三：纯 AI 群本地化

**目标**：纯 AI 群完全本地处理

1. 客户端识别纯 AI 群（调用 requiresCloudConnection）
2. 纯 AI 群消息走本地 WebSocket，不广播云端
3. 纯 AI 群 AI 触发走本地 AiChatService
4. 测试：纯 AI 群 AI 响应正常，Key 不上云

### 阶段四：多真人群双模式

**目标**：多真人群支持隐私优先 / 功能优先切换

1. 群设置新增"AI 模式"选项（隐私优先 / 功能优先）
2. 隐私优先：实现本地生成 + 云端广播 + 触发去重
3. 功能优先：群主授权 Key 上云，云端调用
4. 测试：两种模式群聊 AI 均可用

### 阶段五：清理与优化

1. 删除或完善 `DualWebSocketManager` 死代码
2. application-local.yml 的 ai-only 配置真正生效（条件化加载真人模块）
3. AI_ENCRYPTION_KEY 分层校验
4. 全量回归测试

---

## 五、待决策问题

### 问题1：隐私优先模式 AI 触发去重机制

- 选项A：一致性哈希（每条消息一个触发者，去中心化）
- 选项B：群主独占触发（简单，但群主离线则 AI 不响应）
- 选项C：首个在线成员触发（需在线状态同步）

### 问题2：隐私优先模式 AI 回复内容是否算"上云"

AI 回复经云端广播，回复内容会经过云端服务器。这是否可接受？
- 若不可接受 → 隐私优先模式不启用群 AI（仅纯 AI 群和单聊可用）
- 若可接受 → 采用"本地生成 + 云端广播"

### 问题3：功能优先模式 Key 来源

- 选项A：群主创建群 AI 配置时输入自己的 Key（群主承担费用）
- 选项B：群成员各自贡献 Key（复杂，暂不考虑）

### 问题4：LOCAL 模式 currentBaseUrl 覆盖问题

LOCAL 模式下云端可用时，currentBaseUrl 被覆盖到云端，导致 AI REST 走云端。
- 选项A：AI 请求强制走 localBaseUrl（与云端覆盖无关）
- 选项B：LOCAL 模式不覆盖 currentBaseUrl（影响真人聊天的自动切换）

---

## 六、附录

### 相关文件索引

| 文件 | 作用 |
|------|------|
| [Launcher.java](file:///d:/voluntary_AI_chat/client/src/main/java/org/example/client/Launcher.java) | 客户端启动，三模式策略 |
| [EmbeddedServerStarter.java](file:///d:/voluntary_AI_chat/client/src/main/java/org/example/client/server/EmbeddedServerStarter.java) | 内嵌后端启动器 |
| [WebSocketClient.java](file:///d:/voluntary_AI_chat/client/src/main/java/org/example/client/service/WebSocketClient.java) | 实际使用的 WebSocket 单连接 |
| [DualWebSocketManager.java](file:///d:/voluntary_AI_chat/client/src/main/java/org/example/client/service/DualWebSocketManager.java) | 未集成的双连接设计 |
| [ServerConnectionManager.java](file:///d:/voluntary_AI_chat/client/src/main/java/org/example/client/config/ServerConnectionManager.java) | 模式管理，含 requiresCloudConnection |
| [ClientConfig.java](file:///d:/voluntary_AI_chat/client/src/main/java/org/example/client/config/ClientConfig.java) | 双地址配置 |
| [BaseHttpService.java](file:///d:/voluntary_AI_chat/client/src/main/java/org/example/client/service/BaseHttpService.java) | REST 请求基类 |
| [AiService.java](file:///d:/voluntary_AI_chat/client/src/main/java/org/example/client/service/AiService.java) | 客户端 AI 角色管理 |
| [AiChatService.java](file:///d:/voluntary_AI_chat/server/src/main/java/com/voluntary/chat/server/service/AiChatService.java) | 服务端 AI 对话核心 |
| [ChatWebSocketHandler.java](file:///d:/voluntary_AI_chat/server/src/main/java/com/voluntary/chat/server/websocket/ChatWebSocketHandler.java) | WebSocket 消息处理，含群 AI 触发 |
| [AiConfig.java](file:///d:/voluntary_AI_chat/server/src/main/java/com/voluntary/chat/server/config/AiConfig.java) | AI 模块配置 |
| [application-local.yml](file:///d:/voluntary_AI_chat/server/src/main/resources/application-local.yml) | 本地模式配置 |
| [application-cloud.yml](file:///d:/voluntary_AI_chat/server/src/main/resources/application-cloud.yml) | 云端模式配置 |

### 设计依据文档

- [API.md](file:///d:/voluntary_AI_chat/docs/API.md) - 原始混合架构设计
- [AI_MODULE_PLAN.md](file:///d:/voluntary_AI_chat/docs/AI_MODULE_PLAN.md) - AI 模块原始计划
- [ARCHITECTURE.md](file:///d:/voluntary_AI_chat/docs/ARCHITECTURE.md) - 整体架构
