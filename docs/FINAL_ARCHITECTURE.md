# 最终前后端分离架构方案

> 日期：2026-06-26
> 状态：定稿
> 取代：THREE_PACKAGE_ANALYSIS.md、CLIENT_INDEPENDENCE_PLAN.md、AI_PRIVACY_ARCHITECTURE.md 中的过时部分

---

## 一、现状诊断

### 1.1 架构扭曲的根源

README 规划的是标准 C/S 架构（客户端只做 UI + 网络请求），但实际实现把**完整 Spring Boot 后端塞进了客户包**：

```
当前客户包启动流程：
  JavaFX 启动
    → 同一 JVM 内启动 SpringApplication (EmbeddedServerStarter)
      → Tomcat 初始化 (~1.5s)
      → Spring Security 过滤链构建 (~1.0s)
      → MyBatis Mapper 扫描 (~1.0s)
      → HikariCP + H2 连接 (~0.8s)
      → Spring 核心初始化 (~1.5s)
    → 轮询等待 8080 端口就绪 (~1s)
    → UI 才能发请求
  总计：~7s
```

**7s 启动中，前端 UI 本身只需 <1s，其余 6s 全在 Spring Boot。**

### 1.2 当前三包对照

| 包 | 内容 | 启动 | 大小 | 问题 |
|----|------|------|------|------|
| 客户包 | JavaFX + 内嵌 Spring Boot (ai-core) | 7s | 77MB | 启动慢、包大、架构扭曲 |
| 测试包 | JavaFX + 内嵌 Spring Boot (server全量) | 7s | 80MB | 可接受（开发用） |
| 云端包 | Spring Boot fat JAR | 3s | 47MB | 正常 |

### 1.3 通信路由现状

| 场景 | 当前路由 | 问题 |
|------|---------|------|
| AI 对话 (WebSocket) | 前端 → localhost:8080 WS → 内嵌 AiWebSocketHandler | 多余的 WS 中转 |
| AI 角色管理 (REST) | 前端 → currentBaseUrl REST | LOCAL 模式走本地，但云端可用时 currentBaseUrl 被覆盖到云端，路由不一致 |
| 真人聊天 (WebSocket) | 前端 → 云端 WS | 正常 |
| 真人聊天 (REST) | 前端 → 云端 REST | 正常 |

---

## 二、目标架构

### 2.1 核心原则

1. **客户包 = 纯前端 + 轻量本地 AI 引擎**（无 Spring Boot，无 Tomcat）
2. **测试包 = 前端 + 完整内嵌后端**（开发测试用，保留 Spring Boot）
3. **云端包 = 完整后端**（Spring Boot fat JAR，独立部署）
4. **AI 数据本地化**（API Key、AI 记忆存本地 H2，不上云）
5. **真人聊天云端化**（好友、群、消息走云端服务器）

### 2.2 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                    客户包 (用户安装)                          │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              JavaFX UI 层                             │   │
│  │  LoginView  ChatView  AiView  FriendView  GroupView  │   │
│  └────────────────────────┬────────────────────────────┘   │
│                           │                                 │
│  ┌────────────────────────▼────────────────────────────┐   │
│  │              ViewModel 层 (MVVM)                      │   │
│  │  ChatViewModel    AiViewModel    FriendListViewModel │   │
│  └──────┬─────────────────┬─────────────────────────────┘   │
│         │                 │                                  │
│  ┌──────▼──────┐  ┌──────▼──────────────────────────────┐  │
│  │ 云端通信层   │  │ 本地 AI 引擎 (LocalAiEngine)         │  │
│  │             │  │  ┌─────────────────────────────┐    │  │
│  │ HTTP Client │  │  │ AiChatService (POJO)         │    │  │
│  │ (REST API)  │  │  │  → OpenAiClient (HTTP直调)   │    │  │
│  │             │  │  │  → H2 JDBC (直连,无连接池)    │    │  │
│  │ WS Client   │  │  │  → AiMemoryService (POJO)    │    │  │
│  │ (实时消息)   │  │  │  → AiProfileRepository(JDBC) │    │  │
│  │             │  │  └─────────────────────────────┘    │  │
│  └──────┬──────┘  └──────────────────────────────────────┘  │
│         │                                                   │
└─────────┼───────────────────────────────────────────────────┘
          │
          │ HTTPS + WSS
          │
┌─────────▼───────────────────────────────────────────────────┐
│                    云端服务器 (云端包)                        │
│                                                             │
│  Spring Boot + Tomcat + Security + MyBatis-Plus             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  AuthController    FriendController   GroupController│   │
│  │  MessageController  UserController    ConvController │   │
│  └─────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  ChatWebSocketHandler (真人消息转发 + 群广播)         │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  MySQL 8  +  Redis  +  MinIO                               │
└─────────────────────────────────────────────────────────────┘
```

### 2.3 通信路由（目标）

| 场景 | 路由 | 说明 |
|------|------|------|
| AI 对话 | ViewModel → **LocalAiEngine** (直接方法调用) | 无 HTTP/WS 中转，无延迟 |
| AI 角色管理 | ViewModel → **LocalAiEngine** (JDBC 直读) | 无 REST 请求 |
| AI 记忆 | ViewModel → **LocalAiEngine** (JDBC 直读) | 本地 H2 |
| 真人聊天 | ViewModel → **WebSocketClient** → 云端 WS | 实时消息 |
| 好友/群管理 | ViewModel → **HTTP Client** → 云端 REST | 标准 REST |
| 用户认证 | ViewModel → **HTTP Client** → 云端 REST | JWT |

**关键变化：AI 请求不再走 HTTP/WebSocket 回环，而是 ViewModel 直接调用本地 POJO 方法。**

---

## 三、三包定义

### 3.1 客户包（用户安装包）

```
voluntary-ai-chat-client (JavaFX Application, 无 Spring Boot)
├── JavaFX UI
│   ├── App.java / Launcher.java (启动 <1s)
│   ├── controller/ (FXML 控制器)
│   └── view/ (ViewModel)
│
├── 云端通信层
│   ├── service/
│   │   ├── BaseHttpService.java (→ 云端 REST)
│   │   ├── AuthService.java
│   │   ├── ChatService.java
│   │   ├── FriendService.java
│   │   ├── GroupService.java
│   │   └── UserService.java
│   └── WebSocketClient.java (→ 云端 WS, 真人消息)
│
├── 本地 AI 引擎 (LocalAiEngine, 无 Spring)
│   ├── engine/
│   │   ├── LocalAiEngine.java (门面, 懒加载)
│   │   ├── AiChatEngine.java (AI 对话核心, POJO)
│   │   ├── AiMemoryEngine.java (记忆管理, POJO)
│   │   └── AiProfileRepository.java (JDBC 直连 H2)
│   ├── client/
│   │   ├── OpenAiClient.java (HTTP 调用 AI 提供商)
│   │   └── EmbeddingClient.java (可选, RAG 用)
│   └── config/
│       └── AiConfig.java (AI 配置)
│
├── 本地存储
│   ├── H2 数据库文件 (~/AppData/Voluntary-AI-Chat/data/ai.mv.db)
│   └── util/
│       ├── CredentialStorage.java
│       └── TokenStorage.java
│
└── 配置
    ├── config/
    │   ├── ClientConfig.java
    │   └── ServerConnectionManager.java
    └── application-client.yml (仅前端配置, 无 Spring Boot 配置)
```

**特点：**
- 无 Spring Boot、无 Tomcat、无 Spring Security、无 MyBatis-Spring
- H2 通过 JDBC 直连（`DriverManager.getConnection`），无连接池
- AI 调用通过 OpenAiClient（纯 HTTP）直连 AI 提供商
- 启动时间：<1s（UI 弹出）+ 懒加载 AI 引擎（首次 AI 对话时初始化 ~200ms）
- 包体：~35MB

### 3.2 测试包（开发者一体包）

```
voluntary-ai-chat-test (JavaFX + 内嵌完整 Spring Boot)
├── 全部客户包代码
├── 全部 ai-core 代码
├── 全部 server 代码 (真人聊天模块)
└── EmbeddedServerStarter (内嵌 Spring Boot, 端口 8080)
```

**特点：**
- 保留当前的内嵌 Spring Boot 模式
- 开发者可以端到端测试所有功能
- H2 数据库（本地模式）或 连接 MySQL（热点/云端模式）
- 启动时间：~7s（可接受，仅开发用）
- 包体：~80MB

### 3.3 云端包（服务器部署）

```
voluntary-ai-chat-cloud (Spring Boot fat JAR)
├── 全部 server 代码 (真人聊天 + 群聊 + 认证)
├── 全部 ai-core 代码 (云端 AI, 可选 @Profile)
└── 配置: application-cloud.yml (MySQL + Redis)
```

**特点：**
- 完整后端，独立部署
- `java -jar voluntary-ai-chat-server-exec.jar`
- MySQL + Redis + MinIO
- 真人聊天核心，AI 模块可选加载
- 启动时间：~3s
- 包体：~47MB

---

## 四、LocalAiEngine 设计

### 4.1 核心思路

将 ai-core 中的 Spring `@Service` Bean 重构为纯 POJO，通过手动依赖注入组装：

```
ai-core (重构后, 无 Spring 依赖)
├── engine/                    ← 新增: POJO 引擎层
│   ├── LocalAiEngine.java     ← 门面, 客户包入口
│   ├── AiChatEngine.java      ← 从 AiChatService 重构
│   ├── AiMemoryEngine.java    ← 从 AiMemoryService 重构
│   └── AiProfileRepository.java ← 从 AiService + Mapper 重构
├── client/                    ← 保留: HTTP 客户端 (纯 HTTP, 无 Spring)
│   ├── OpenAiClient.java
│   └── EmbeddingClient.java
├── entity/                    ← 保留: 数据实体 (POJO)
│   ├── AiProfile.java
│   ├── AiMemory.java
│   └── Message.java
├── dto/                       ← 保留: DTO
├── config/
│   └── AiConfig.java          ← 重构: POJO 配置, 非 @ConfigurationProperties
└── util/
    └── AesKeyUtil.java        ← 保留: AES 加密
```

### 4.2 LocalAiEngine 接口

```java
/**
 * 本地 AI 引擎门面（无 Spring 依赖）
 * 客户包通过此类调用所有 AI 功能。
 */
public class LocalAiEngine {

    private static LocalAiEngine instance;

    // 懒加载初始化
    public static synchronized LocalAiEngine getInstance() {
        if (instance == null) {
            instance = new LocalAiEngine();
        }
        return instance;
    }

    private LocalAiEngine() {
        // 1. 加载 H2 JDBC 连接 (~50ms)
        // 2. 读取 AI 配置
        // 3. 组装 OpenAiClient + Repository
        // 总计: ~200ms
    }

    // ===== AI 角色管理 =====
    public List<AiProfile> listAiProfiles();
    public AiProfile createAiProfile(CreateAiProfileRequest req);
    public AiProfile updateAiProfile(Long id, UpdateAiProfileRequest req);
    public void deleteAiProfile(Long id);

    // ===== AI 对话 =====
    public void chat(AiChatRequest req, AiStreamCallback callback);

    // ===== AI 记忆 =====
    public List<AiMemory> listMemories(Long aiId);
    public void summarizeMemory(Long aiId); // 异步

    // ===== 关闭 =====
    public void shutdown();
}
```

### 4.3 AiChatEngine（POJO 版）

```java
/**
 * AI 对话引擎（从 AiChatService 重构，去掉 Spring 注解）
 */
public class AiChatEngine {

    private final AiProfileRepository profileRepo;
    private final OpenAiClient openAiClient;
    private final AiMemoryEngine memoryEngine;
    private final ExecutorService executor;

    // 构造函数注入（手动组装）
    public AiChatEngine(AiProfileRepository repo, OpenAiClient client,
                        AiMemoryEngine memory) {
        this.profileRepo = repo;
        this.openAiClient = client;
        this.memoryEngine = memory;
        this.executor = Executors.newFixedThreadPool(2);
    }

    /**
     * AI 对话（流式）
     * ViewModel 直接调用此方法，不需要 WebSocket 中转。
     */
    public void chat(AiChatRequest req, AiStreamCallback callback) {
        executor.submit(() -> {
            // 1. 查 AI Profile
            AiProfile profile = profileRepo.findById(req.getAiId());
            // 2. 解密 API Key
            String apiKey = AesKeyUtil.decrypt(profile.getApiKeyEnc());
            // 3. 查历史消息 + 记忆
            List<Message> history = profileRepo.findMessages(req.getSessionId());
            List<AiMemory> memories = memoryEngine.findRelevant(req.getContent());
            // 4. 构建 Prompt
            String prompt = buildPrompt(profile, history, memories, req);
            // 5. 调用 AI 提供商 (流式)
            openAiClient.streamChat(apiKey, profile.getModelProvider(), prompt,
                chunk -> callback.onChunk(chunk));
            // 6. 完成
            callback.onComplete();
        });
    }
}
```

### 4.4 AiProfileRepository（JDBC 直连）

```java
/**
 * AI 角色数据访问（JDBC 直连 H2，无 MyBatis）
 */
public class AiProfileRepository {

    private final Connection connection; // H2 单连接, 无需连接池

    public AiProfileRepository(String h2Path) {
        this.connection = DriverManager.getConnection(
            "jdbc:h2:file:" + h2Path + "/ai;AUTO_SERVER=TRUE", "sa", "");
        initSchema(); // 建表
    }

    public AiProfile findById(Long id) {
        // PreparedStatement 查询 → 映射到 AiProfile
    }

    public List<Message> findMessages(String sessionId) {
        // 查历史消息
    }

    // ... 其他 CRUD
}
```

### 4.5 ViewModel 调用方式

```java
// AiViewModel 中
public void sendMessage(String content) {
    AiChatRequest req = new AiChatRequest();
    req.setAiId(currentAiId);
    req.setContent(content);
    req.setSessionId(sessionId);

    // 直接调用本地 AI 引擎，无 HTTP/WebSocket
    LocalAiEngine.getInstance().chat(req, new AiStreamCallback() {
        @Override
        public void onChunk(String chunk) {
            Platform.runLater(() -> appendToMessage(chunk));
        }

        @Override
        public void onComplete() {
            Platform.runLater(() -> finalizeMessage());
        }
    });
}
```

---

## 五、模块依赖关系

### 5.1 Maven 依赖图

```
                    ┌──────────┐
                    │  common  │ (无依赖, 共享 DTO/枚举)
                    └────┬─────┘
                         │
              ┌──────────┼──────────┐
              │          │          │
       ┌──────▼─────┐ ┌─▼────────┐ ┌▼───────────┐
       │  ai-core   │ │  server  │ │   client   │
       │ (POJO 化)  │ │ (Spring) │ │  (JavaFX)  │
       │            │ │          │ │            │
       │ OpenAiClient│ │ 真人聊天  │ │ UI         │
       │ H2 JDBC    │ │ 群聊     │ │ ViewModel  │
       │ AI Engine  │ │ Security │ │ HTTP Client│
       │ AesKeyUtil │ │ WebSocket│ │ WS Client  │
       └──────┬─────┘ └─────┬────┘ │ LocalAiEng │
              │             │      └─────┬──────┘
              │             │            │
              │             │            │ 依赖
              └─────────────┴────────────┘
```

### 5.2 各包的模块组合

| 包 | 依赖模块 | 说明 |
|----|---------|------|
| **客户包** | common + ai-core(POJO) + client | 无 Spring Boot |
| **测试包** | common + ai-core + server + client | 含 Spring Boot |
| **云端包** | common + ai-core + server | Spring Boot fat JAR |

### 5.3 ai-core 双模式

ai-core 需要同时支持两种使用方式：

| 模式 | 使用者 | ai-core 形态 |
|------|--------|-------------|
| **POJO 模式** | 客户包 | 直接 new 对象，无 Spring 容器 |
| **Spring 模式** | 测试包/云端包 | @Service @Component 注解，Spring 容器管理 |

实现方式：ai-core 的 Engine 类**不加 Spring 注解**，而是提供静态工厂方法：

```java
// ai-core 中
public class AiChatEngine {
    // 无 @Service 注解

    // Spring 模式: server/client 的 EmbeddedServerStarter 中声明 @Bean
    // POJO 模式: LocalAiEngine 中直接 new
    public AiChatEngine(AiProfileRepository repo, OpenAiClient client) { ... }
}

// 测试包的 EmbeddedServerStarter 中
@Bean
public AiChatEngine aiChatEngine(AiProfileMapper mapper, OpenAiClient client) {
    return new AiChatEngine(new MyBatisAiProfileRepository(mapper), client);
}

// 客户包的 LocalAiEngine 中
private LocalAiEngine() {
    AiProfileRepository repo = new JdbcAiProfileRepository(h2Path);
    OpenAiClient client = new OpenAiClient();
    this.chatEngine = new AiChatEngine(repo, client);
}
```

---

## 六、关键改动清单

### 6.1 ai-core 重构（最大改动）

| 改动 | 文件 | 说明 |
|------|------|------|
| 去掉 Spring 注解 | AiChatService → AiChatEngine | 去掉 @Service @Autowired，改为构造函数注入 |
| 去掉 Spring 注解 | AiMemoryService → AiMemoryEngine | 同上 |
| 去掉 Spring 注解 | AiService → AiProfileRepository | 拆为数据访问层 |
| 去掉 Spring 注解 | AiGroupConfigService → AiGroupConfigEngine | 同上 |
| 新增 JDBC Repository | JdbcAiProfileRepository | 客户包用，直连 H2 |
| 新增 Spring Adapter | MyBatisAiProfileRepository | 测试包/云端包用，走 MyBatis |
| 保留无注解 | OpenAiClient | 已经是纯 HTTP 客户端 |
| 保留无注解 | EmbeddingClient | 同上 |
| 保留无注解 | AesKeyUtil | 同上 |
| 删除 | AiWebSocketHandler | 客户包不需要 WS 服务端 |
| 删除 | SecurityConfig (ai-core版) | 客户包不需要 Spring Security |
| 删除 | JwtTokenProvider / JwtAuthenticationFilter | 客户包不需要 JWT（本地直连） |

### 6.2 client 改动

| 改动 | 文件 | 说明 |
|------|------|------|
| 新增 | LocalAiEngine.java | 本地 AI 引擎门面 |
| 修改 | Launcher.java | LOCAL 模式不再启动 Spring Boot，改为初始化 LocalAiEngine |
| 修改 | AiViewModel.java | AI 对话调用 LocalAiEngine，不再走 WebSocket |
| 修改 | AiService.java | AI 角色管理调用 LocalAiEngine，不再走 REST |
| 删除 | EmbeddedServerStarter.java | 客户包不再需要 |
| 删除 | ClientWebSocketConfig.java | 客户包不再需要 WS 服务端 |
| 修改 | client/pom.xml | 移除 spring-boot-starter-web 等 Spring 依赖 |

### 6.3 server 改动

| 改动 | 文件 | 说明 |
|------|------|------|
| 保留 | 所有真人聊天代码 | 不变 |
| 修改 | ChatWebSocketHandler | AI 部分改为委托 AiChatEngine（POJO） |
| 保留 | SecurityConfig (server版) | 云端用 |
| 修改 | AiController | 云端可选，@Profile("ai") |

### 6.4 测试包兼容

测试包仍使用 `EmbeddedServerStarter` 启动内嵌 Spring Boot。ai-core 的 POJO Engine 在测试包中通过 `@Bean` 声明注入 Spring 容器。

---

## 七、AI 隐私路由策略

### 7.1 三种场景的 AI 路由

| 场景 | AI 调用方 | API Key 存储 | 消息存储 |
|------|----------|-------------|---------|
| 单聊 AI | 本地 LocalAiEngine | 本地 H2（加密） | 本地 H2 |
| 纯 AI 群 (1人+N AI) | 本地 LocalAiEngine | 本地 H2（加密） | 本地 H2 |
| 多真人群 (隐私优先) | 本地 LocalAiEngine | 本地 H2（加密） | AI 回复经云端广播 |
| 多真人群 (功能优先) | 云端服务器 | 云端 MySQL（群主授权） | 云端 MySQL |

### 7.2 客户包 AI 请求路由

```java
// AiViewModel 中
public void sendAiMessage(String content) {
    // AI 请求永远走本地，不管当前是 LOCAL/CLOUD/HOTSPOT 模式
    LocalAiEngine.getInstance().chat(req, callback);
}

// ChatViewModel 中（真人聊天）
public void sendHumanMessage(String content) {
    // 真人消息走云端 WebSocket
    WebSocketClient.getInstance().send(message);
}
```

**AI 请求与真人请求完全解耦，各走各的路。**

---

## 七补、README 特色功能覆盖（关键补充）

> 本节确保 README 规划的三大特色功能在去 Spring Boot 后仍可实现。

### 7补.1 AI 主动聊天（定时 + 概率触发）

**README 规划**：定时任务(随机间隔) → 读记忆摘要 → 生成提示词 → 调 AI → WS 推送

**问题**：客户包无 Spring `@Scheduled`，无 RabbitMQ。

**方案**：用 JDK 原生 `ScheduledExecutorService` 替代，概率触发替代 MQ 延迟。

```java
// client/engine/ProactiveChatScheduler.java (新增, 无 Spring 依赖)
public class ProactiveChatScheduler {

    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "proactive-ai");
            t.setDaemon(true);
            return t;
        });

    private final LocalAiEngine aiEngine;
    private final Consumer<String> onProactiveMessage; // UI 回调

    /**
     * 启动主动聊天调度
     * @param minIntervalMinutes 最小间隔（分钟）
     * @param maxIntervalMinutes 最大间隔（分钟）
     * @param triggerProbability 每次触发概率 (0.0~1.0)
     */
    public void start(int minIntervalMinutes, int maxIntervalMinutes,
                      double triggerProbability) {
        scheduleNext(minIntervalMinutes, maxIntervalMinutes, triggerProbability);
    }

    private void scheduleNext(int min, int max, double prob) {
        int delayMin = min + (int)(Math.random() * (max - min));
        scheduler.schedule(() -> {
            if (Math.random() < prob) {
                // 1. 读最近记忆摘要
                // 2. 生成主动聊天提示词
                // 3. 调 LocalAiEngine.chat()
                // 4. callback 推送到 UI
            }
            scheduleNext(min, max, prob); // 递归调度下一次
        }, delayMin, TimeUnit.MINUTES);
    }

    public void stop() {
        scheduler.shutdownNow();
    }
}
```

**归属**：`client/engine/`（客户包独有，云端不需要）

**触发条件**：
- 仅 LOCAL 模式（用户在线且本地有 AI 数据）
- CLOUD 模式下由云端 `@Scheduled` + RabbitMQ 实现（README 原方案）

| 模式 | 触发方 | 实现 | MQ |
|------|--------|------|----|
| LOCAL | 客户包 ProactiveChatScheduler | ScheduledExecutorService + 概率 | 无需 |
| CLOUD | 云端服务器 | @Scheduled + RabbitMQ 延迟队列 | RabbitMQ |

### 7补.2 记忆管理 RAG（向量检索本地化）

**README 规划**：聊天达阈值 → 异步总结 → 存 MySQL + Milvus → 向量检索 Top-K

**问题**：客户包无 Milvus/Qdrant，H2 不支持向量索引。

**方案**：本地用 Embedding API + brute-force 余弦相似度（小数据量可行）。

```java
// ai-core/engine/AiMemoryEngine.java (POJO)
public class AiMemoryEngine {

    private final AiMemoryRepository memoryRepo; // JDBC 或 MyBatis
    private final EmbeddingClient embeddingClient; // 调 embedding API

    /**
     * 检索相关记忆 (本地 brute-force)
     * 数据量 <1000 条时性能可接受 (~10ms)
     */
    public List<AiMemory> findRelevant(String query, int topK) {
        // 1. 调 Embedding API 将 query 向量化
        float[] queryVec = embeddingClient.embed(query);

        // 2. 从 H2 加载所有记忆向量 (一次性, <1000条)
        List<AiMemory> allMemories = memoryRepo.findAllByAiId(aiId);

        // 3. 计算余弦相似度, 排序取 Top-K
        return allMemories.stream()
            .map(m -> Map.entry(m, cosineSimilarity(queryVec, m.getVector())))
            .sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))
            .limit(topK)
            .map(Map.Entry::getKey)
            .toList();
    }

    /**
     * 异步总结记忆 (聊天达阈值时触发)
     */
    public void summarizeIfNeeded(Long aiId, int messageCount) {
        if (messageCount % 20 == 0) { // 每20条总结一次
            executor.submit(() -> {
                // 1. 读最近20条消息
                // 2. 调 AI 生成摘要
                // 3. 调 Embedding API 向量化摘要
                // 4. 存 H2 (摘要 + 向量)
            });
        }
    }
}
```

**数据存储**：

| 数据 | LOCAL 模式 | CLOUD 模式 |
|------|-----------|-----------|
| 记忆摘要 | 本地 H2 `ai_memory` 表 | 云端 MySQL |
| 记忆向量 | 本地 H2 `ai_memory.vector` 字段 (BLOB) | 云端 Milvus/Qdrant |
| 向量检索 | brute-force (<1000条, ~10ms) | Milvus ANN (海量) |

**性能边界**：
- <1000 条记忆：brute-force ~10ms ✅
- 1000~10000 条：~100ms，可接受
- >10000 条：需迁移到云端 Milvus（CLOUD 模式）

### 7补.3 本地小模型 DJL（句子完整性识别）

**README 规划**：客户端发送前过模型 `is_complete(text) → bool`，模型 <50MB

**方案**：DJL 是纯 Java 库，无 Spring 依赖，直接放入客户包。

```
client/
├── engine/
│   ├── LocalAiEngine.java
│   ├── ProactiveChatScheduler.java
│   └── SentenceCompletenessChecker.java  ← 新增
├── model/
│   └── djl/
│       └── sentence-completeness.onnx     ← 模型文件 (<50MB)
```

```java
// client/engine/SentenceCompletenessChecker.java (新增, 无 Spring)
public class SentenceCompletenessChecker {

    private Model model; // DJL 模型, 懒加载
    private Predictor<String, Boolean> predictor;

    /**
     * 检查句子是否完整 (本地推理)
     * @param text 用户输入
     * @return true=完整可发送, false=不完整拦截
     */
    public boolean isComplete(String text) {
        if (model == null) {
            loadModel(); // 首次调用时加载 (~500ms)
        }
        return predictor.predict(text);
    }

    private void loadModel() {
        // DJL 加载 ONNX 模型 (BERT-tiny 微调版)
        Criteria<String, Boolean> criteria = Criteria.builder()
            .setTypes(String.class, Boolean.class)
            .optModelPath(Paths.get("model/djl/sentence-completeness.onnx"))
            .build();
        model = criteria.loadModel();
        predictor = model.newPredictor();
    }
}
```

**调用位置**：ViewModel 发送前拦截

```java
// ChatViewModel 中
public void sendMessage(String content) {
    // 句子完整性检查 (本地, 不上传文字)
    if (!SentenceCompletenessChecker.getInstance().isComplete(content)) {
        showWarning("句子不完整，请补充后再发送");
        return;
    }
    // 正常发送...
}
```

**归属**：`client/engine/`（纯客户端功能，云端不需要）

---

## 八、功能覆盖矩阵（README 对照）

| README 功能 | 客户包(LOCAL) | 测试包 | 云端包 | 实现方式 |
|------------|--------------|--------|--------|---------|
| 用户注册登录 | HTTP→云端 | 内嵌 | ✅ | Spring Security + JWT |
| 单聊文字 | WS→云端 | 内嵌 | ✅ | ChatWebSocketHandler |
| 群聊+管理 | WS+REST→云端 | 内嵌 | ✅ | GroupController |
| 好友系统 | REST→云端 | 内嵌 | ✅ | FriendController |
| 图片消息 | REST→云端MinIO | 内嵌 | ✅ | ImageUploadService |
| AI 对话 | LocalAiEngine | 内嵌 | @Profile("ai") | AiChatEngine (POJO) |
| AI 角色管理 | LocalAiEngine | 内嵌 | @Profile("ai") | AiProfileRepository (JDBC) |
| API Key 加密 | AesKeyUtil | 同 | 同 | AES-256-GCM |
| 撤回(人-人) | REST→云端 | 内嵌 | ✅ | MessageController |
| 撤回(AI) | LocalAiEngine | 内嵌 | - | 本地 H2 删除 |
| **AI 主动聊天** | ProactiveChatScheduler | 内嵌 | @Scheduled+MQ | ScheduledExecutorService / RabbitMQ |
| **记忆 RAG** | brute-force H2 | 内嵌 | Milvus | AiMemoryEngine (双模式) |
| **句子完整性** | DJL 本地推理 | 同 | - | SentenceCompletenessChecker |
| AI 主动聊天(CLOUD) | - | - | @Scheduled+RabbitMQ | 云端定时任务 |
| 记忆 RAG(CLOUD) | - | - | Milvus/Qdrant | 向量数据库 ANN |
| 转发 | LocalAiEngine+云端 | 内嵌 | ✅ | 打包 ForwardPackage |
| 背景图 | 本地文件 | 同 | - | 按 session_id 存储 |

**结论：README 规划的所有功能均可实现，无遗漏。**

---

## 九、迁移步骤

### 阶段 1：ai-core POJO 化（核心）

1. 将 AiChatService 重构为 AiChatEngine（去 @Service，构造函数注入）
2. 将 AiMemoryService 重构为 AiMemoryEngine
3. 将 AiService 拆为 AiProfileRepository 接口 + JdbcAiProfileRepository 实现
4. 保留 OpenAiClient / EmbeddingClient / AesKeyUtil 不变
5. 删除 AiWebSocketHandler / SecurityConfig / JWT 三件套（移到 server）

验证：`mvn compile -pl ai-core,server`（server 仍能编译）

### 阶段 2：创建 LocalAiEngine

1. 新建 `client/engine/LocalAiEngine.java`（门面）
2. 新建 `client/engine/AiStreamCallback.java`（流式回调接口）
3. H2 JDBC 初始化 + 建表脚本
4. 懒加载：首次调用时初始化

验证：单元测试 LocalAiEngine.chat()

### 阶段 3：client 解耦 Spring Boot

1. 修改 Launcher：LOCAL 模式改为 `LocalAiEngine.getInstance()` 初始化
2. 修改 AiViewModel：AI 对话调用 LocalAiEngine
3. 修改 AiService（client侧）：AI 角色管理调用 LocalAiEngine
4. 修改 client/pom.xml：移除 spring-boot-starter-web / mybatis-plus / spring-security
5. 删除 EmbeddedServerStarter / ClientWebSocketConfig

验证：`mvn package -Pclient`（无 Spring Boot 依赖）

### 阶段 4：测试包兼容

1. 测试包保留 EmbeddedServerStarter
2. 在 EmbeddedServerStarter 中用 @Bean 声明 AiChatEngine 等 POJO
3. 确保 server 的 ChatWebSocketHandler 能委托 AiChatEngine

验证：`mvn test`（全量测试通过）

### 阶段 5：云端包验证

1. 确认 server 的 AI 模块通过 @Profile("ai") 可选加载
2. 云端部署时 AI REST 接口返回 404（默认不加载）

验证：`mvn package -Pcloud`

### 阶段 6：打包验证

```bash
# 客户包（无 Spring Boot, ~35MB, 启动 <1s）
mvn package -Pclient

# 测试包（含 Spring Boot, ~80MB, 启动 ~7s）
mvn package -Ptest

# 云端包（Spring Boot JAR, ~47MB）
mvn package -Pcloud
```

---

## 十、预期效果对比

| 指标 | 改造前 | 改造后 |
|------|--------|--------|
| 客户包启动 | 7s | **<1s** |
| 客户包大小 | 77MB | **~35MB** |
| AI 对话延迟 | HTTP/WS 回环 ~50ms | **直接调用 ~0ms** |
| AI Key 隐私 | 本地 H2 ✅ | 本地 H2 ✅（不变） |
| 真人聊天 | 云端 WS ✅ | 云端 WS ✅（不变） |
| 测试包启动 | 7s | 7s（不变） |
| 云端包 | 47MB | 47MB（不变） |
| 代码复用 | ai-core 共享 | ai-core 共享（双模式） |

---

## 十一、风险与回滚

### 风险

| 风险 | 概率 | 影响 | 应对 |
|------|------|------|------|
| ai-core POJO 化破坏 server 功能 | 中 | 云端包不可用 | 阶段 1 后全量测试 |
| H2 JDBC 直连与 MyBatis 行为不一致 | 低 | AI 数据异常 | 统一 SQL 方言 |
| 测试包 EmbeddedServerStarter 兼容 | 中 | 测试包启动失败 | 阶段 4 专门验证 |

### 回滚

```bash
git checkout -- ai-core/ client/ server/
mvn package -Pclient -pl client -am
```

---

## 十二、开箱即用 vs 可选扩展（功能边界）

> **核心原则**：所有功能代码我们全写好，但体积过大的依赖（模型文件、向量库、MQ 等）不打包进安装包。用户想用某功能时，自行下载依赖 + 填参数即可启用，无需写任何代码。

### 12.1 功能分级

| 级别 | 含义 | 我们提供 | 用户需做什么 |
|------|------|---------|------------|
| **L1 开箱即用** | 安装即可用 | 代码 + 依赖 + 默认配置 | 无 |
| **L2 填配置即用** | 功能代码已内置，填参数即启用 | 代码 + 配置项 | 填 Key / 地址 / 开关 |
| **L3 下载即用** | 功能代码已内置，但依赖未打包 | 代码 + 加载入口 + 依赖下载文档 | 下载依赖 + 填路径 |

**不存在"自行实现"级别。所有功能的代码我们全部写好，用户只需决定是否启用。**

### 12.2 功能分级清单

| 功能 | 级别 | 默认 | 不打包的依赖 | 用户启用步骤 |
|------|------|------|------------|------------|
| **基础 AI 对话** | L2 | 关 | 无（代码内置） | 设置界面填 API Key |
| **AI 角色管理** | L1 | 开 | 无 | 无 |
| **真人聊天** | L2 | 关 | 无（代码内置） | 填云端服务器地址 |
| **群聊 / 好友** | L2 | 关 | 无（代码内置） | 填云端服务器地址 |
| **撤回 / 转发** | L1 | 开 | 无 | 无 |
| **AI 主动聊天 (本地)** | L2 | 关 | 无（代码内置） | 设置界面开关 + 填 API Key |
| **记忆 RAG (本地)** | L2 | 关 | 无（brute-force 内置） | 设置界面开关 + 填 Embedding API |
| **记忆 RAG (云端)** | L3 | 关 | Milvus/Qdrant 服务端 | 部署向量库 + 填地址 |
| **Embedding 调用** | L2 | 关 | 无（代码内置） | 填 Embedding API 地址 |
| **句子完整性 DJL** | L3 | 关 | ONNX 模型文件 (~50MB) | 下载模型放指定目录 + 开关 |
| **图片消息 MinIO** | L3 | 关 | MinIO 服务端 | 部署 MinIO + 填地址 |
| **AI 主动聊天 (云端MQ)** | L3 | 关 | RabbitMQ 服务端 | 部署 RabbitMQ + 填地址 |
| **TTS 语音合成** | L3 | 关 | TTS 模型文件 | 下载模型 + 填路径 |
| **图片识别** | L3 | 关 | 识别模型文件 | 下载模型 + 填路径 |
| **随机陌生人聊天** | L2 | 关 | 无（代码内置） | 需云端服务器部署该模块 |

### 12.3 依赖下载文档

安装包内置 `docs/扩展功能指南.md`，列出每个 L3 功能的依赖下载地址和配置方法：

```markdown
# 扩展功能指南

## 启用句子完整性识别（DJL）
1. 下载模型: https://example.com/sentence-completeness.onnx (~50MB)
2. 放到: ~/AppData/Voluntary-AI-Chat/models/
3. 设置 → AI → 句子完整性 → 开启
4. 自动加载，无需重启

## 启用记忆 RAG（云端/本地向量库）
1. 部署 Qdrant: docker run -p 6333:6333 qdrant/qdrant
2. 设置 → AI → 记忆管理 → 向量库地址: http://localhost:6333
3. 设置 → AI → 记忆管理 → 开启

## 启用图片消息（MinIO）
1. 部署 MinIO: docker run -p 9000:9000 minio/minio
2. 设置 → 存储 → MinIO 地址 / AccessKey / SecretKey
3. 设置 → 存储 → 开启
...
```

### 12.4 配置驱动开关

用户通过设置界面或配置文件控制，无需改代码：

```yaml
# application-client.yml (用户可编辑)

ai:
  # L2: 基础对话
  chat:
    enabled: true
    api-key: ""               # 用户在设置界面填

  # L2: 记忆 RAG（本地 brute-force，无需额外下载）
  memory:
    enabled: false
    embedding:
      api-key: ""             # 用户填 Embedding API Key
      base-url: ""            # 用户填 Embedding API 地址
    vector-store: local       # local(内置) / qdrant(L3)

  # L2: 主动聊天
  proactive:
    enabled: false
    min-interval-min: 10
    max-interval-min: 60
    trigger-probability: 0.3

  # L3: 句子完整性（需下载模型）
  sentence-completeness:
    enabled: false
    model-path: ""            # 用户填下载的 ONNX 模型路径

  # L3: TTS（需下载模型）
  tts:
    enabled: false
    model-path: ""            # 用户填 TTS 模型路径

# L2: 云端服务
cloud:
  server-url: ""              # 用户填云端服务器地址
  websocket-url: ""

# L3: MinIO（需部署）
storage:
  minio:
    enabled: false
    endpoint: ""
    access-key: ""
    secret-key: ""

# L3: 向量库（云端 RAG 用，需部署）
vector-store:
  provider: local             # local(内置) / qdrant / milvus
  qdrant:
    url: ""
  milvus:
    host: ""
    port: 19530
```

### 12.5 自动检测 + 优雅降级

启动时自动检测依赖是否存在，不存在则降级，不报错：

```
功能启用检查:
  L2 功能:
    1. 读 enabled 开关
    2. 检查必填配置项是否为空
    3. 配置完整 → 初始化功能
    4. 配置缺失 → 跳过，日志提示"XX功能未配置，已跳过"

  L3 功能:
    1. 读 enabled 开关
    2. 检查依赖是否就绪（文件存在 / 服务可连通）
    3. 依赖就绪 → 初始化功能
    4. 依赖缺失 → 降级到 L2/L1 替代方案，日志提示

降级策略:
  ┌─────────────────┬───────────────────────────┐
  │ 缺失的依赖        │ 降级行为                    │
  ├─────────────────┼───────────────────────────┤
  │ API Key 未填     │ AI 对话不可用，其余正常       │
  │ Embedding 未配   │ RAG 退化为无记忆对话          │
  │ 向量库未部署      │ 云端 RAG 退化为本地 brute-force│
  │ DJL 模型未下载   │ 句子完整性退化为规则匹配       │
  │ MinIO 未部署     │ 图片消息不可用，文字正常       │
  │ 云端未部署       │ 仅本地 AI 可用，真人聊天不可用 │
  │ RabbitMQ 未部署  │ 云端主动聊天不可用，本地可用   │
  │ TTS 模型未下载   │ TTS 不可用                   │
  └─────────────────┴───────────────────────────┘
```

### 12.6 内置实现 vs 外部依赖

**所有功能代码我们全写好**，区别仅在于依赖是否打包：

| 功能 | 代码 | 内置依赖 | 需用户下载的依赖 |
|------|------|---------|----------------|
| AI 对话 | ✅ 写好 | OpenAiClient (HTTP) | 无（用户填 API Key） |
| 记忆 RAG (本地) | ✅ 写好 | brute-force 检索 | 无（用户填 Embedding API） |
| 记忆 RAG (云端) | ✅ 写好 | QdrantClient | Qdrant 服务端 (docker) |
| 句子完整性 | ✅ 写好 | 规则匹配 (降级方案) | ONNX 模型文件 |
| 图片消息 | ✅ 写好 | MinIO Client | MinIO 服务端 (docker) |
| 主动聊天 (本地) | ✅ 写好 | ScheduledExecutorService | 无 |
| 主动聊天 (云端) | ✅ 写好 | Spring @Scheduled | RabbitMQ (docker) |
| TTS | ✅ 写好 | 加载逻辑 | TTS 模型文件 |
| 图片识别 | ✅ 写好 | 加载逻辑 | 识别模型文件 |

**用户永远不需要写代码，只需下载依赖 + 填参数。**

---

## 附录：废弃文档

以下文档中的方案已被本文档取代：

| 文档 | 废弃原因 |
|------|---------|
| THREE_PACKAGE_ANALYSIS.md | 三包分析已落地，本文档重新定义三包内容 |
| CLIENT_INDEPENDENCE_PLAN.md | ai-core 迁移已完成，本文档定义下一步 POJO 化 |
| AI_PRIVACY_ARCHITECTURE.md | 隐私架构方案已融入本文档第七章 |
