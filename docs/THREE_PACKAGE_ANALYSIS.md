# 三包架构分析文档

> 目标：将当前单模块（client + server）拆分为 **测试包 / 云端包 / 客户包** 三套独立分发包
> 日期：2026-06-23
> 状态：分析阶段

---

## 一、当前模块依赖关系

```
┌──────────────────────────────────────────────────────────────────┐
│                         voluntary-ai-chat                         │
│  ┌─────────────┐    ┌─────────────┐    ┌────────────────────┐   │
│  │   common    │◀───│   server    │◀───│     client         │   │
│  │  (jar)      │    │  (jar)      │    │  (jar, depends on  │   │
│  │ 共享DTO/    │    │  SpringBoot │    │   server module)   │   │
│  │ 枚举/常量)  │    │  Controller │    │                    │   │
│  │             │    │  Service    │    │  JavaFX UI         │   │
│  │ 无依赖      │    │  Entity     │    │  ViewModel         │   │
│  └─────────────┘    │  Mapper     │    │  Service(HTTP/WS)  │   │
│                     │  Security   │    │  EmbeddedServer    │   │
│                     │  WebSocket  │    └────────────────────┘   │
│                     │  AI模块     │                              │
│                     └─────────────┘                              │
└──────────────────────────────────────────────────────────────────┘
```

### 1.1 当前分发方式

| 包 | 内容 | 依赖 | 启动方式 |
|----|------|------|---------|
| `common` | 共享DTO/枚举/常量 | 无 | 作为依赖被引用 |
| `server-fat-jar` | server全部类 + 依赖 | common | `java -jar server-exec.jar` |
| `client-fat-jar` | client全部类 + server全部类 + 全依赖 | common + server | `java -jar client-exec.jar` 或 `mvn javafx:run` |

### 1.2 核心问题

**问题1：client 依赖 server 模块导致包体积膨胀**

`client/pom.xml` 直接依赖 `voluntary-ai-chat-server`，打包时嵌入整个 Spring Boot server。这不仅包括真人聊天模块，还包括所有 AI 模块、Security、WebSocket 等。client 的 fat JAR 包含了所有 server 代码，很多是 client 内部调用的（内嵌后端），但也有些是 cloud 部署才需要的。

**问题2：server 模块中 AI 模块和真人模块混在一起**

`@ComponentScan("com.voluntary.chat")` 扫描全部包：

```
com.voluntary.chat.server
  ├── client/           ← AI 后端客户端（OpenAI等，云端也需要）
  ├── common/           ← 共享类
  ├── config/           ← 配置（AI配置、安全配置、CORS等）
  ├── controller/       ← 控制器
  │   ├── AiController.java          ← AI角色管理
  │   ├── AuthController.java        ← 认证
  │   ├── ConversationController.java← 会话
  │   ├── FriendController.java      ← 好友
  │   ├── GroupController.java       ← 群聊
  │   ├── MessageController.java     ← 消息
  │   └── UserController.java        ← 用户
  ├── dto/              ← 请求/响应体
  ├── entity/           ← 数据库实体
  ├── mapper/           ← MyBatis映射
  ├── security/         ← JWT + 限流
  ├── service/          ← 服务
  │   ├── AiChatService.java         ← AI对话
  │   ├── AiGroupConfigService.java  ← AI群配置
  │   ├── AiMemoryService.java       ← AI记忆
  │   ├── AiService.java             ← AI角色
  │   ├── AuthService.java           ← 认证
  │   ├── ConversationService.java   ← 会话
  │   ├── FriendService.java         ← 好友
  │   ├── GroupService.java          ← 群聊
  │   ├── ImageUploadService.java    ← 图片上传
  │   ├── MessageService.java        ← 消息
  │   ├── ServerBroadcastService.java← 广播
  │   └── UserService.java           ← 用户
  └── websocket/        ← WebSocket处理器
```

没有分层隔离，`@Profile("!cloud")` 方案只能跳过 AI Controller，但 AI Service 和 AI Client 类仍然在 classpath 中。

**问题3：内嵌后端启动时加载全部 server beans**

`EmbeddedServerStarter` 的 `@ComponentScan("com.voluntary.chat")` 加载所有包。本地模式（H2）下，MessageService、FriendService 等真人模块的 Bean 也会注册，虽然它们用不到。

**问题4：DualWebSocketManager 是死代码**

设计了 AI 走本地、真人走云端的双连接路由，但 `ChatViewModel` 和 `AiViewModel` 实际都使用 `WebSocketClient`（单连接）。双连接从未被集成。

**问题5：client 包的 REST 服务（AiService 等）使用 `currentBaseUrl`**

`BaseHttpService` 的所有请求都走 `currentBaseUrl`。如果 CLOUD 模式下 AI 请求要走本地（localhost:8080），但 currentBaseUrl 是云端地址，就发错地方了。

---

## 二、三包架构设计

### 2.1 包定义

| 包名 | Maven Artifact | 描述 | 部署形态 |
|------|---------------|------|---------|
| **测试包** | `voluntary-ai-chat-test` | 包含所有测试代码 + 全量依赖 | 仅用于 CI/本地测试 |
| **云端包** | `voluntary-ai-chat-cloud` | 远端计算模块，真人聊天 + 云端AI | Spring Boot fat JAR |
| **客户包** | `voluntary-ai-chat-client` | 桌面客户端，内嵌AI引擎 + 连接云端 | JavaFX fat JAR |

### 2.2 架构图

```
                         ┌──────────────────────┐
                         │      common 模块       │
                         │  (无依赖，所有包共用)   │
                         └────────┬─────────────┘
                                  │
                ┌─────────────────┼─────────────────┐
                │                 │                  │
    ┌───────────▼──────┐  ┌──────▼─────────┐  ┌─────▼────────────┐
    │   云端包 (cloud)  │  │   客户包 (client)│  │   测试包 (test)  │
    │                  │  │                 │  │                 │
    │ Spring Boot JAR  │  │ JavaFX JAR      │  │ Maven test      │
    │                  │  │                 │  │                 │
    │ 真人聊天模块     │  │ JavaFX UI       │  │ 全量测试         │
    │ 群聊             │  │ ViewModel       │  │ 集成测试         │
    │ WebSocket服务    │  │ REST/WS服务     │  │ 端到端测试       │
    │ JWT认证          │  │ 内嵌后端(AI-only)│  │                 │
    │ 云端AI(可选)     │  │ H2数据库        │  │                 │
    │ MySQL + Redis    │  │ 本地AI引擎      │  │                 │
    │                  │  │ UDP广播发现     │  │                 │
    └──────────────────┘  └──────┬─────────┘  └──────────────────┘
                                 │
                        ┌────────▼─────────┐
                        │   云端服务器       │
                        │  (远程连接)       │
                        │ 真人消息/群消息    │
                        └──────────────────┘
```

### 2.3 模块依赖关系

```
┌──────────┐    ┌──────────┐
│  common  │    │  common  │
└────┬─────┘    └────┬─────┘
     │               │
┌────▼──────┐  ┌─────▼─────────┐       ┌─────────────────┐
│ cloud-core│  │ client-core   │       │   test-package  │
│ (Spring   │  │ (JavaFX +     │───────│─▶(依赖全部模块)  │
│  Boot)    │  │  Spring Embed)│       └─────────────────┘
└───────────┘  └───────────────┘
```

### 2.4 云端包 (cloud) — 内容分群

```
voluntary-ai-chat-cloud (Spring Boot Application)
├── 真人聊天核心 (必需)
│   ├── config/
│   │   ├── CorsConfig.java
│   │   ├── SecurityConfig.java
│   │   ├── WebSocketConfig.java
│   │   ├── WebMvcConfig.java
│   │   ├── AsyncConfig.java
│   │   └── ServerStartupRunner.java
│   ├── controller/
│   │   ├── AuthController.java
│   │   ├── ConversationController.java
│   │   ├── FriendController.java
│   │   ├── GroupController.java
│   │   ├── MessageController.java
│   │   └── UserController.java
│   ├── service/
│   │   ├── AuthService.java
│   │   ├── ConversationService.java
│   │   ├── FriendService.java
│   │   ├── GroupService.java
│   │   ├── ImageUploadService.java
│   │   ├── MessageService.java
│   │   └── UserService.java
│   ├── websocket/
│   │   ├── ChatWebSocketHandler.java
│   │   └── JwtHandshakeInterceptor.java
│   ├── security/
│   │   ├── JwtAuthenticationFilter.java
│   │   ├── JwtTokenProvider.java
│   │   ├── SecurityUtils.java
│   │   └── RateLimitingFilter.java
│   ├── entity/
│   │   ├── Friend.java
│   │   ├── FriendApply.java
│   │   ├── GroupEntity.java
│   │   ├── GroupMember.java
│   │   ├── Message.java
│   │   ├── MessageRead.java
│   │   ├── User.java
│   │   └── UserToken.java
│   └── mapper/ (对应 Entity 的 Mapper)
│
├── AI 模块 (可选，@Profile("ai"))
│   ├── client/
│   │   ├── OpenAiClient.java
│   │   ├── EmbeddingClient.java
│   │   └── VectorStoreClient.java
│   ├── service/
│   │   ├── AiChatService.java
│   │   ├── AiGroupConfigService.java
│   │   ├── AiMemoryService.java
│   │   └── AiService.java
│   ├── controller/
│   │   └── AiController.java
│   ├── entity/
│   │   ├── AiProfile.java
│   │   ├── AiGroupConfig.java
│   │   └── AiMemory.java
│   └── mapper/
│
├── dto/ (所有 Request/Response)
├── common/
│   ├── ApiResult.java
│   └── GlobalExceptionHandler.java
├── util/
│   └── AesKeyUtil.java
└── resources/
    ├── application.yml (MySQL + Redis)
    └── application-cloud.yml
```

### 2.5 客户包 (client) — 内容分群

```
voluntary-ai-chat-client (JavaFX Application)
├── JavaFX UI
│   ├── App.java
│   ├── Launcher.java
│   ├── controller/
│   │   ├── LoginController.java
│   │   ├── RegisterController.java
│   │   ├── MainController.java
│   │   ├── FriendController.java
│   │   ├── GroupController.java
│   │   ├── GroupInfoController.java
│   │   ├── GroupCreateController.java
│   │   ├── GroupInviteController.java
│   │   ├── GroupAvatarController.java
│   │   ├── ProfileController.java
│   │   ├── ForgotPasswordController.java
│   │   ├── ImagePreviewDialog.java
│   │   ├── ImageViewerDialog.java
│   │   └── NotificationDialog.java
│   └── AiController/AiEdit* (AI UI)
│
├── ViewModel
│   ├── LoginViewModel.java
│   ├── RegisterViewModel.java
│   ├── MainViewModel.java
│   ├── ChatViewModel.java
│   ├── FriendListViewModel.java
│   ├── GroupListViewModel.java
│   ├── ForgotPasswordViewModel.java
│   ├── ProfileViewModel.java
│   └── AiViewModel.java
│
├── REST/WS 服务 (连接云端服务器)
│   ├── service/
│   │   ├── BaseHttpService.java
│   │   ├── AuthService.java
│   │   ├── ChatService.java
│   │   ├── FriendService.java
│   │   ├── GroupService.java
│   │   └── UserService.java
│   └── WebSocketClient.java (单连接 → 云端)
│
├── 本地 AI 引擎 (内嵌后端，AI-only)
│   ├── server/
│   │   └── EmbeddedServerStarter.java (仅扫描 AI 模块)
│   └── Embedded Spring Context (H2 + AI-Only Beans)
│
├── 配置
│   ├── config/
│   │   ├── ClientConfig.java
│   │   ├── ServerConnectionManager.java
│   │   └── ServerMode.java
│   ├── application-client.properties
│   └── application-client.yml
│
├── 模型 (同包，不依赖 server entity)
│   └── model/
│       ├── MessageInfo.java
│       ├── ConversationInfo.java
│       ├── UserInfo.java
│       ├── LoginRequest.java / LoginResponse.java
│       ├── RegisterRequest.java / RegisterResponse.java
│       ├── FriendResponse.java / FriendApply*.java
│       ├── GroupInfo.java / GroupMemberInfo.java
│       ├── AiProfile.java / AiGroupConfig.java / AiMemory.java
│       └── ... (所有请求/响应 DTO)
│
├── 工具
│   ├── util/
│   │   ├── CredentialStorage.java
│   │   ├── ErrorCodeRegistry.java
│   │   ├── JsonUtils.java
│   │   ├── ServerDiscovery.java
│   │   └── TokenStorage.java
│   └── DualWebSocketManager.java (待清理死代码)
│
└── 内嵌 AI 模块 (从 server 抽出的本地 AI)
    ├── client/ (OpenAI HTTP 客户端)
    │   ├── OpenAiClient.java
    │   ├── EmbeddingClient.java
    │   └── VectorStoreClient.java
    ├── service/
    │   ├── AiChatService.java
    │   └── AiMemoryService.java
    ├── config/
    │   └── AiConfig.java
    └── entity/
        ├── AiProfile.java
        ├── AiGroupConfig.java
        └── AiMemory.java
```

### 2.6 测试包 (test)

```
voluntary-ai-chat-test (多模块组合)
├── cloud-test
│   └── 云端包的所有测试 (controller, service, security, websocket)
│
├── client-test
│   └── 客户包的所有测试 (controller, viewmodel, service, util)
│
├── common-test
│   └── common 模块的所有测试
│
└── e2e-test
    └── 端到端集成测试 (client + cloud 联合测试)
```

---

## 三、关键改动分析

### 3.1 Maven 模块重构

**当前结构**：
```
voluntary-ai-chat (父POM)
├── common
├── server
└── client (依赖 server)
```

**目标结构**：
```
voluntary-ai-chat (父POM)
├── common
├── cloud-core          ← 从 server 拆分出的真人聊天核心
├── cloud-ai            ← 从 server 拆分出的 AI 模块(可选)
├── client-core         ← 现有 client，去掉 server 依赖
├── client-ai-embed     ← 内嵌 AI 引擎(简化版 AI 模块)
└── test-package        ← 测试聚合模块
```

或更精简的版本（两模块 + 测试聚合）：

```
voluntary-ai-chat (父POM)
├── common
├── cloud-server        ← 云端部署包(含真人 + 可选 AI)
│   └── 使用 @Profile 区分 AI 加载
├── client              ← 桌面客户端(含内嵌 AI 引擎)
└── test-report         ← 聚合测试(仅用于 CI)
```

### 3.2 核心改动清单

#### 改动1：AI 模块从 server 包拆分出来

将 AI 相关类从 `com.voluntary.chat.server` 包分离为独立模块或子包：

- `com.voluntary.chat.ai` 包 — AI 核心接口和实体
- 云端部署时通过 `@Profile("ai")` 条件加载
- 客户包内嵌时直接编译进 JAR

#### 改动2：client 不再依赖 server 模块

`client/pom.xml` 移除 `voluntary-ai-chat-server` 依赖，改为：

```xml
<!-- 依赖 common -->
<dependency>
    <groupId>com.voluntary</groupId>
    <artifactId>voluntary-ai-chat-common</artifactId>
</dependency>

<!-- 依赖内嵌 AI 引擎（精简版，不含真人模块） -->
<dependency>
    <groupId>com.voluntary</groupId>
    <artifactId>voluntary-ai-chat-ai-embed</artifactId>
</dependency>

<!-- 内嵌 Spring Boot 需要的运行时依赖 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
</dependency>
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
</dependency>
```

#### 改动3：EmbeddedServerStarter 改为仅扫描 AI 模块

```java
@ComponentScan(basePackages = { "com.voluntary.chat.ai", "org.example.client.server" })
// 不再扫描 com.voluntary.chat 全部
```

#### 改动4：云端的 AI 模块可选加载

`cloud-server` 中 AI 相关 Bean 添加 `@Profile("ai")`，默认只加载真人聊天核心。部署时通过 `--spring.profiles.active=cloud,ai` 启用 AI。

#### 改动5：客户包 AI REST 请求强制走本地

`BaseHttpService` 增加 `buildLocalRequest()` 方法，`AiService.getAiList()` 等方法强制使用 `localBaseUrl`：

```java
// AiService 中
private static final String LOCAL_AI_PATH = "http://localhost:8080/api/ai";

protected HttpRequest.Builder buildAiRequest(final String path) {
    final String url = LOCAL_AI_PATH + path;
    // ...
}
```

#### 改动6：DualWebSocketManager 清理

如果采用"AI 走本地 REST + 真人走云端 WebSocket"的架构，AI 消息不需要 WebSocket 双连接。清理 `DualWebSocketManager` 死代码。

### 3.3 类分布对照表

| 类 | 当前模块 | 云端包 | 客户包 | 备注 |
|----|---------|--------|--------|------|
| **AI 模块** | | | | |
| `AiController.java` (server) | server | ✅ (可选) | ❌ | 云端通过 @Profile(ai) 加载 |
| `AiChatService.java` | server | ✅ (可选) | ✅ (内嵌) | 客户包简化版（无记忆摘要） |
| `AiGroupConfigService.java` | server | ✅ (可选) | ❌ | 群AI配置只在云端 |
| `AiMemoryService.java` | server | ✅ (可选) | ✅ (内嵌) | 简化版 |
| `AiService.java` (server) | server | ✅ (可选) | ✅ (内嵌) | 角色管理，两边都需要 |
| `OpenAiClient.java` | server | ✅ (可选) | ✅ (内嵌) | HTTP客户端，两边都需要 |
| `EmbeddingClient.java` | server | ✅ (可选) | ❌ | 云端需要RAG |
| `VectorStoreClient.java` | server | ✅ (可选) | ❌ | 云端用Milvus/Qdrant |
| **真人聊天模块** | | | | |
| `AuthController.java` | server | ✅ | ❌ | |
| `FriendController.java` | server | ✅ | ❌ | |
| `GroupController.java` | server | ✅ | ❌ | |
| `MessageController.java` | server | ✅ | ❌ | |
| `UserController.java` | server | ✅ | ❌ | |
| `ConversationController.java` | server | ✅ | ❌ | |
| `AuthService.java` (server) | server | ✅ | ❌ | |
| `FriendService.java` (server) | server | ✅ | ❌ | |
| `GroupService.java` (server) | server | ✅ | ❌ | |
| `MessageService.java` (server) | server | ✅ | ❌ | |
| `UserService.java` (server) | server | ✅ | ❌ | |
| `ConversationService.java` (server) | server | ✅ | ❌ | |
| `ChatWebSocketHandler.java` | server | ✅ | ❌ | |
| `JwtHandshakeInterceptor.java` | server | ✅ | ❌ | |
| **共享基础设施** | | | | |
| `JwtTokenProvider.java` | server | ✅ | ❌ | 客户包不需要JWT签发 |
| `JwtAuthenticationFilter.java` | server | ✅ | ❌ | |
| `SecurityUtils.java` | server | ✅ | ❌ | |
| `CorsConfig.java` | server | ✅ | ❌ | |
| `SecurityConfig.java` | server | ✅ | ❌ | |
| `WebSocketConfig.java` | server | ✅ | ❌ | |
| `RateLimitingFilter.java` | server | ✅ | ❌ | |
| **客户端特有** | | | | |
| `App.java` / `Launcher.java` | client | ❌ | ✅ | |
| 所有 Controller (JavaFX) | client | ❌ | ✅ | |
| 所有 ViewModel | client | ❌ | ✅ | |
| `BaseHttpService.java` | client | ❌ | ✅ | |
| `WebSocketClient.java` | client | ❌ | ✅ | 连接云端用 |
| `ServerDiscovery.java` | client | ❌ | ✅ | |
| `ClientConfig.java` | client | ❌ | ✅ | |
| `ServerConnectionManager.java` | client | ❌ | ✅ | |
| **测试代码** | | | | |
| 全部 test 目录 | client/server | ❌ | ❌ | 聚合到 test-report |

### 3.4 数据库分布

| 数据库 | 云端包 | 客户包(内嵌) |
|--------|--------|------------|
| MySQL 8 | ✅ 主库（真人聊天+群聊） | ❌ |
| H2 文件模式 | ❌ | ✅ 本地 AI 数据 |
| Redis | ✅ 认证码+在线状态 | ❌ |
| Milvus/Qdrant | ✅ (可选，AI RAG向量库) | ❌ |

### 3.5 配置文件分布

| 配置文件 | 云端包 | 客户包(内嵌) |
|----------|--------|------------|
| `application.yml` | MySQL+Redis 主配置 | H2 配置 |
| `application-cloud.yml` | ✅ 云端扩展配置 | ❌ |
| `application-local.yml` | ❌ | ✅ 本地精简配置 |
| `application-hotspot.yml` | ❌ | ✅ 热点测试配置 |
| `application-client.properties` | ❌ | ✅ 客户端配置 |

---

## 四、风险与注意事项

### 4.1 包体积

| 分发包 | 当前体积(估) | 拆分后体积(估) | 变化 |
|--------|------------|---------------|------|
| 云端 JAR | ~45MB | ~35MB | -10MB (去掉JavaFX) |
| 客户 JAR | ~65MB | ~45MB | -20MB (去掉真人模块server类) |
| 合计 | ~110MB | ~80MB | -30MB |

### 4.2 兼容性风险

1. **client 包不再依赖 server 包** → client 中所有引用 server 包的 import 需要清理
2. **内嵌 AI 引擎需独立编译** → AI 实体(`AiProfile`等)和 client model 有名称冲突，需统一
3. **云端 AI 可选加载** → 现有云端部署如果启用了 AI，需要调整启动参数
4. **历史数据** → 客户包 H2 数据不受影响，云端 MySQL 数据不受影响

### 4.3 接口兼容性

| 接口 | 是否变化 | 说明 |
|------|---------|------|
| REST API 路径 | 不变 | `/api/auth`, `/api/friend` 等路径不变 |
| WebSocket 消息协议 | 不变 | MessageTypes 枚举不变 |
| entities (数据库结构) | 不变 | 表结构、字段名不变 |
| 客户端配置项 | 不变 | `application-client.properties` key 不变 |

### 4.4 执行风险

1. **AI Entity 重复定义**：当前 `client/model/AiProfile.java` 和 `server/entity/AiProfile.java` 是两份独立代码。拆包后需要合并或明确职责。
2. **client 中 model 类**：`client/model` 下的 Request/Response 类很多和 `server/dto` 下的相同。拆分时可统一放到 common 模块，或各自保留。
3. **OpenAiClient 重复**：云端 AI 和本地 AI 都调用 OpenAI 协议，HTTP 客户端可共用。
4. **MyBatis-Plus Entity 依赖**：内嵌 AI 引擎也需要 MyBatis-Plus 操作 H2 中的 AI 实体表。

---

## 五、建议实施顺序

### 阶段一：代码分析 + 包界定（当前）
- [x] 调研现有模块结构
- [x] 梳理所有类的职责
- [x] 分析依赖关系

### 阶段二：common 模块扩展
- [ ] 将共享的 DTO/Entity 迁移到 common（如果存在重复）
- [ ] 将 WebSocketMessage、MessageTypes 等保持在 common

### 阶段三：AI 模块抽取
- [ ] 新建 `ai-core` 子模块（AI 实体 + Service + Client）
- [ ] 云端包和客户包都依赖 `ai-core`
- [ ] 调整 EmbeddedServerStarter 扫描路径

### 阶段四：客户包解耦
- [ ] 移除 client 对 server 模块的依赖
- [ ] client 改为依赖 common + ai-core
- [ ] client 独立打包，不再打包 server 真人模块

### 阶段五：云端包精简
- [ ] 云端包只包含真人聊天核心 + 可选 AI
- [ ] 通过 @Profile 控制 AI 模块加载
- [ ] 配置文件和资源文件拆分

### 阶段六：测试 + 验证
- [ ] 全量回归测试
- [ ] 云端部署验证
- [ ] 客户端热点模式验证
- [ ] 客户端本地 AI 验证

---

## 六、已决策问题

### 决策1：AI Entity 重复定义 → 各自保留

`client/model/AiProfile.java` 和 `server/entity/AiProfile.java` 职责不同：
- client 的是纯 POJO，用于 HTTP 响应反序列化
- server 的是 MyBatis-Plus Entity，含数据库注解

各自保留，不合并。拆包后各包独立维护自己的模型。

### 决策2：内嵌 AI 引擎边界 → 完整版

客户包内嵌的 AI 引擎保持完整功能，与云端 AI 能力对等：
- 含记忆检索、流式输出、上下文构建
- 含 OpenAI HTTP 客户端
- 只是数据源改为 H2 而非 MySQL
- 编译打包时从 server AI 模块复制（同一份代码，编译两次）

### 决策3：云端 AI 模块加载方式 → 默认全部加载

云端部署时**默认加载所有模块**（包括 AI），不搞条件化加载：
- 理论上云端有 AI 需求（群聊 AI 触发等场景）
- 避免条件加载带来的复杂性和维护成本
- 如果未来需要性能优化，再引入 `@Profile`

### 决策4：测试包组织方式 → 保持现有单模块结构

> **这是最大的设计点。**

测试包**不拆**，保持当前的单模块结构。原因：

```
现有 HOTSPOT 模式：
  客户端(全量模式)
    └── 依赖 server 全部模块（真人 + AI）
    └── 以测试模式启动后端（内嵌）
    └── 小范围测试服务稳定性
```

| 场景 | 包 | 依赖 |
|------|----|------|
| 本地开发/测试 | HOTSPOT 模式 | client + server 全量（当前结构） |
| CI 单元测试 | `mvn test` | 各模块独立测试 |
| 云端部署 | cloud 包 | 仅 server 模块（真人 + AI） |
| 用户运行 | client 包 | 仅 client + 内嵌 AI |

**测试包的形态**：
```
voluntary-ai-chat-test  ← 不新增模块，依赖现有 server + client
├── server/src/test      ← server 的独立测试
├── client/src/test      ← client 的独立测试
└── HOTSPOT 模式         ← 启动 client 依赖 server，全量集成测试
```

不需要新增一个物理的测试模块。HOTSPOT 就是集成测试模式。

### 决策5：DualWebSocketManager → 保留并启用

**WebSocket 是必须的**。本地 AI 流式输出也需要 WebSocket，只是请求的目标地址不同：

| 场景 | WebSocket 连接目标 | 用途 |
|------|-------------------|------|
| 本地 AI 单聊 | `localhost:8080/ws` | AI 流式对话（`AI_CHAT`, `AI_STREAM`） |
| 真人聊天 | 云端服务器 `/ws` | 发送消息、接收、群聊广播 |
| 群 AI 触发(隐私模式) | 本地 `localhost:8080/ws` | AI 回复经云端广播 |

`DualWebSocketManager` 需要真正集成使用：
- `AI_*`、`VECTOR_*` 消息类型 → 本地连接
- 其他消息类型 → 云端连接
- `WebSocketClient` 保留为兼容层或底层实现

---

## 七、实施计划（更新版）

### 阶段一：代码抽取（AI 模块独立）
- 将 server 中 AI 相关代码抽取到 `com.voluntary.chat.ai` 包
- 保留原始包下的类为包装类，逐步迁移
- 内嵌 AI 引擎直接引用此包

### 阶段二：client 解耦
- client 不再直接依赖 server 包
- 改为依赖 common + ai-core
- EmbeddedServerStarter 扫描路径改为 `com.voluntary.chat.ai`
- AiService(client) 的 REST 请求改为 localBaseUrl

### 阶段三：DualWebSocketManager 集成
- ChatViewModel/AiViewModel 改用 DualWebSocketManager
- AI 消息路由到本地连接（localhost:8080）
- 真人消息路由到云端连接
- 清理 WebSocketClient 单连接模式（或用它做底层实现）

### 阶段四：云端打包优化
- server 模块保持原样（全量）
- 通过 Spring Boot 打包插件配置排除无用的 AutoConfiguration
- 配置文件独立管理

### 阶段五：测试验证
- 全量回归测试
- HOTSPOT 模式集成测试
- 云端部署验证
- 客户端本地 AI 验证