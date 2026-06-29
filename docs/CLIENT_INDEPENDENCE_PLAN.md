# 客户包独立拆分计划

> 目标：client 彻底摆脱 server 依赖，只依赖 ai-core，打出的客户包不含真人聊天模块
> 日期：2026-06-25
> 状态：待执行

---

## 一、当前依赖链分析

### 1.1 客户包内嵌后端需要的 AI 模块

```
AiController
├── AiService              ← AI 角色管理
│   ├── AiProfileMapper    ← 已在 ai-core
│   ├── AiConfig           ← 已在 ai-core
│   ├── AesKeyUtil          ← 已在 ai-core
│   └── AI DTO             ← 需迁移
├── AiChatService          ← AI 对话
│   ├── AiService
│   ├── OpenAiClient        ← 已在 ai-core
│   ├── AiConfig            ← 已在 ai-core
│   ├── MessageMapper       ← ⚠️ 真人模块，需迁移
│   ├── AiMemoryService
│   └── ChatWebSocketHandler ← ⚠️ 需拆分 AI 部分
├── AiMemoryService        ← AI 记忆
│   ├── AiMemoryMapper      ← 已在 ai-core
│   ├── MessageMapper       ← ⚠️ 真人模块，需迁移
│   ├── EmbeddingClient     ← 已在 ai-core
│   ├── VectorStoreClient   ← 已在 ai-core
│   ├── OpenAiClient        ← 已在 ai-core
│   ├── AiConfig            ← 已在 ai-core
│   └── AiService
├── AiGroupConfigService   ← AI 群配置
│   ├── AiGroupConfigMapper ← 已在 ai-core
│   ├── AiService
│   └── StringRedisTemplate ← @Autowired(required=false)，可选
└── SecurityUtils          ← 需迁移（仅 18 行）
```

### 1.2 ChatWebSocketHandler 拆分

| 方法 | 归属 | 说明 |
|------|------|------|
| `handleAiChat()` | **ai-core** | AI 对话入口 |
| `sendAiStream()` | **ai-core** | AI 流式输出推送 |
| `triggerGroupAi()` | **ai-core** | 群聊 AI 触发 |
| `handleSendMessage()` | server | 真人消息转发 |
| `broadcastGroupMessage()` | server | 群消息广播 |
| `forwardPrivateMessage()` | server | 私聊转发 |
| `handleReconnect()` | server | 断线重连补发 |
| `handlePing()` | **两者都需要** | 心跳，复制到 ai-core |
| `afterConnectionEstablished()` | **两者都需要** | 连接建立，ai-core 简化版 |
| `afterConnectionClosed()` | **两者都需要** | 连接关闭 |
| `sendToUser()` | **两者都需要** | 推送消息给用户 |

### 1.3 其他依赖

| 类 | 归属 | 说明 |
|----|------|------|
| `Message` (entity) | **ai-core** | AI 消息也要存 message 表 |
| `MessageMapper` | **ai-core** | 同上 |
| `SecurityConfig` | **ai-core 简化版** | 只需 JWT 过滤器，不需 BCrypt |
| `JwtAuthenticationFilter` | **ai-core** | JWT 验证 |
| `JwtTokenProvider` | **ai-core** | JWT 生成/验证 |
| `JwtHandshakeInterceptor` | **ai-core** | WebSocket 握手验签 |
| `WebSocketConfig` | **ai-core** | 注册 AI WebSocket |
| `ApiResult` | **ai-core** | 通用响应体 |
| `GlobalExceptionHandler` | **ai-core** | 异常处理 |
| `SecurityUtils` | **ai-core** | 获取当前用户 ID（18 行） |
| AI DTO (6 个) | **ai-core** | AI 请求/响应 DTO |

---

## 二、目标架构

```
┌─────────────────────────────────────────────────────┐
│                   ai-core 模块                       │
│                                                     │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────┐  │
│  │ Entity      │  │ Mapper       │  │ Client    │  │
│  │ AiProfile   │  │ AiProfileMap │  │ OpenAi    │  │
│  │ AiGroupConf │  │ AiGroupMap   │  │ Embedding │  │
│  │ AiMemory    │  │ AiMemoryMap  │  │ VectorSto │  │
│  │ Message ⭐  │  │ MessageMap ⭐│  │           │  │
│  └─────────────┘  └──────────────┘  └───────────┘  │
│                                                     │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────┐  │
│  │ Service     │  │ Controller   │  │ WebSocket │  │
│  │ AiService ⭐│  │ AiController ⭐│ │ AiWebSocketHandler ⭐│
│  │ AiChatSvc ⭐│  │              │  │ JwtHandshakeInt ⭐  │  │
│  │ AiMemorySvc⭐│ │              │  │ WebSocketConfig ⭐  │  │
│  │ AiGroupSvc⭐│  │              │  │           │  │
│  └─────────────┘  └──────────────┘  └───────────┘  │
│                                                     │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────┐  │
│  │ Config      │  │ Security     │  │ Common    │  │
│  │ AiConfig    │  │ SecurityConf ⭐│  │ ApiResult ⭐│  │
│  │             │  │ JwtAuthFilter⭐│  │ GlobalEx ⭐│  │
│  │             │  │ JwtTokenProv⭐│  │ SecurityUt⭐│  │
│  │             │  │              │  │           │  │
│  └─────────────┘  └──────────────┘  └───────────┘  │
│                                                     │
│  ┌─────────────┐  ┌──────────────┐                 │
│  │ DTO         │  │ Util         │                 │
│  │ AI 请求/响应 ⭐│  │ AesKeyUtil   │                 │
│  └─────────────┘  └──────────────┘                 │
└─────────────────────────────────────────────────────┘
         ⭐ = 本次新增迁移到 ai-core

┌──────────┐         ┌──────────────────┐
│  common  │         │     server       │
│ (零依赖)  │         │ 真人聊天模块      │
└──────────┘         │ (依赖 ai-core)   │
     │                └──────────────────┘
     │
┌────▼─────────────┐
│    client        │
│ (依赖 ai-core)   │
│ 不再依赖 server！ │
└──────────────────┘
```

---

## 三、详细迁移清单

### 3.1 迁移到 ai-core 的文件（⭐ 新增）

| # | 文件 | 当前位置 | 迁移后位置 | 依赖说明 |
|---|------|---------|-----------|---------|
| **Entity** ||||
| 1 | `Message.java` | server/entity | ai-core/entity | AI 消息持久化 |
| **Mapper** ||||
| 2 | `MessageMapper.java` | server/mapper | ai-core/mapper | 同上 |
| **Service** ||||
| 3 | `AiService.java` | server/service | ai-core/service | 只依赖 ai-core 类 |
| 4 | `AiChatService.java` | server/service | ai-core/service | 依赖 MessageMapper + WebSocketHandler |
| 5 | `AiMemoryService.java` | server/service | ai-core/service | 依赖 MessageMapper |
| 6 | `AiGroupConfigService.java` | server/service | ai-core/service | Redis 可选 |
| **Controller** ||||
| 7 | `AiController.java` | server/controller | ai-core/controller | 依赖 AI Service + SecurityUtils |
| **WebSocket** ||||
| 8 | `AiWebSocketHandler.java` | **新建** | ai-core/websocket | 从 ChatWebSocketHandler 拆出 AI 部分 |
| 9 | `JwtHandshakeInterceptor.java` | server/websocket | ai-core/websocket | WebSocket 握手验签 |
| 10 | `AiWebSocketConfig.java` | **新建** | ai-core/config | 注册 AI WebSocket |
| **Security** ||||
| 11 | `SecurityConfig.java` | server/config | ai-core/config | 简化版，只需 JWT |
| 12 | `JwtAuthenticationFilter.java` | server/security | ai-core/security | JWT 过滤器 |
| 13 | `JwtTokenProvider.java` | server/security | ai-core/security | JWT 生成/验证 |
| 14 | `SecurityUtils.java` | server/security | ai-core/security | 获取当前用户 ID |
| **DTO** ||||
| 15 | `CreateAiProfileRequest.java` | server/dto/request | ai-core/dto/request | |
| 16 | `UpdateAiProfileRequest.java` | server/dto/request | ai-core/dto/request | |
| 17 | `AiGroupConfigRequest.java` | server/dto/request | ai-core/dto/request | |
| 18 | `AiProfileResponse.java` | server/dto/response | ai-core/dto/response | |
| 19 | `AiMemoryResponse.java` | server/dto/response | ai-core/dto/response | |
| 20 | `AiGroupConfigResponse.java` | server/dto/response | ai-core/dto/response | |
| **Common** ||||
| 21 | `ApiResult.java` | server/common | ai-core/common | 通用响应体 |
| 22 | `GlobalExceptionHandler.java` | server/common | ai-core/common | 异常处理 |

### 3.2 留在 server 的文件（不迁移）

| 文件 | 原因 |
|------|------|
| `ChatWebSocketHandler.java` | 真人消息转发，改为继承/委托 AiWebSocketHandler |
| `MessageController.java` | 真人消息 REST 接口 |
| `MessageService.java` | 真人消息业务逻辑 |
| `AuthController/Service` | 云端认证 |
| `FriendController/Service` | 云端好友 |
| `GroupController/Service` | 云端群组 |
| `UserController/Service` | 云端用户 |
| `ConversationController/Service` | 云端会话 |
| 所有真人 Entity/Mapper | 真人聊天数据 |

### 3.3 server 侧改动

`ChatWebSocketHandler` 改为继承 `AiWebSocketHandler`：

```java
// server 侧
@Component
public class ChatWebSocketHandler extends AiWebSocketHandler {
    // 保留真人消息转发逻辑
    // AI 相关方法（handleAiChat, sendAiStream）继承自父类
    
    @Override
    protected void handleTextMessage(...) {
        // 先判断消息类型
        // AI_CHAT / PING → 调用父类
        // SEND_MESSAGE / RECONNECT → 自己处理
    }
}
```

---

## 四、执行步骤（6 步）

### 步骤 1：迁移基础类到 ai-core（无风险）

迁移 Message + MessageMapper + SecurityUtils + JWT 三件套 + ApiResult + GlobalExceptionHandler。

**包名保持 `com.voluntary.chat.server.xxx`，server import 一行不改。**

验证：`mvn compile -pl ai-core,server`

### 步骤 2：迁移 AI Service 到 ai-core（低风险）

迁移 AiService, AiChatService, AiMemoryService, AiGroupConfigService。

**关键改动**：AiChatService 对 ChatWebSocketHandler 的依赖改为接口或抽象类。

```java
// ai-core 中定义接口
public interface AiStreamSender {
    void sendAiStream(Long userId, String messageId, String content, boolean done);
    void sendAiStream(Long userId, String messageId, String content, boolean done, Long aiMessageId);
}
```

AiChatService 依赖 `AiStreamSender` 接口，不依赖具体的 WebSocketHandler。

验证：`mvn compile -pl ai-core,server`

### 步骤 3：迁移 AiController + DTO（低风险）

迁移 AiController 和 6 个 AI DTO。

验证：`mvn compile -pl ai-core,server`

### 步骤 4：创建 AiWebSocketHandler（中风险）

从 ChatWebSocketHandler 拆出 AI 部分，创建 `AiWebSocketHandler`：

- `handleAiChat()` 
- `sendAiStream()` + 实现 `AiStreamSender`
- `triggerGroupAi()`
- `handlePing()`
- `afterConnectionEstablished()`（简化版，不含顶号逻辑）
- `afterConnectionClosed()`
- `sendToUser()`

server 的 `ChatWebSocketHandler` 改为继承 `AiWebSocketHandler`。

验证：`mvn compile -pl ai-core,server`

### 步骤 5：client 解耦 server（中风险）

1. `client/pom.xml`：移除 server 依赖，改为依赖 ai-core
2. `EmbeddedServerStarter`：`@ComponentScan` 只扫描 ai-core 包
3. `@MapperScan` 只扫描 ai-core 的 mapper

```java
@ComponentScan(basePackages = { "com.voluntary.chat.server" })  // ai-core 的包
@MapperScan("com.voluntary.chat.server.mapper")                 // ai-core 的 mapper
```

验证：`mvn compile -pl ai-core,client`

### 步骤 6：全量测试 + 打包验证

```bash
# 全量测试
mvn test -Dcheckstyle.skip=true

# 云端包
mvn package -Pcloud -pl ai-core,server -am -DskipTests

# 客户包
mvn package -Pclient -pl ai-core,client -am -DskipTests

# 测试包（仍含 server）
mvn package -Ptest -pl ai-core,server,client -am -DskipTests
```

---

## 五、风险评估

| 风险 | 概率 | 影响 | 应对 |
|------|------|------|------|
| ChatWebSocketHandler 拆分后真人消息异常 | 中 | 群聊/单聊不可用 | 步骤 4 后全量测试 |
| Mapper 扫描不到 | 低 | Bean 注入失败 | @MapperScan 包名一致 |
| Bean 重复注册 | 低 | 启动报错 | server 删除已迁移文件 |
| client 缺少依赖 | 中 | 编译失败 | 步骤 5 后编译验证 |
| SecurityConfig 冲突 | 低 | 两个 SecurityFilterChain | ai-core 用 @ConditionalOnMissingBean |

### 回滚方案

```bash
# 还原所有改动
git checkout -- ai-core/ server/ client/ pom.xml
```

---

## 六、迁移后包体积预估

| 包 | 当前 | 迁移后 | 变化 |
|----|------|--------|------|
| 客户包 fat JAR | 56MB | ~40MB | **-16MB**（去掉真人模块） |
| 云端包 fat JAR | 47MB | ~47MB | 不变（还是全量） |
| 测试包 fat JAR | 56MB | ~56MB | 不变（含 server + ai-core） |

---

## 七、关键设计决策

### 7.1 AiStreamSender 接口

AiChatService 不直接依赖 WebSocketHandler，而是依赖接口：

```
ai-core:  AiStreamSender (接口)  ← AiChatService 依赖它
          AiWebSocketHandler implements AiStreamSender

server:   ChatWebSocketHandler extends AiWebSocketHandler
          （继承 sendAiStream 实现）
```

### 7.2 SecurityConfig 简化

ai-core 的 SecurityConfig 只需：
- JWT 过滤器
- 放行 `/api/auth/**`（虽然客户包没有 AuthController，但保留兼容）
- 放行 `/ws/**`
- 不需要 BCrypt（客户包不处理密码）

server 的 SecurityConfig 保留完整版（覆盖 ai-core 的）。

### 7.3 测试包仍含 server

测试包（`-Ptest`）依赖 ai-core + server + client，保持全量代码，HOTSPOT 模式集成测试。
