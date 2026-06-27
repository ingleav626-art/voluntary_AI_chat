# 架构设计文档

> 系统整体架构、模块设计和技术选型

---

## 一、系统架构

### 1.1 整体架构图（当前）

```
┌──────────────────────────────────────────────────────────────┐
│                     JavaFX 桌面客户端                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐ │
│  │  UI 层   │  │ 业务逻辑 │  │ WebSocket│  │ LocalAiEngine│ │
│  │ (FXML)   │  │  (MVVM)  │  │  Client  │  │  (POJO/H2)   │ │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └──────┬───────┘ │
│       └──────────────┴─────────────┴───────────────┘         │
└──────────────────────────────┬───────────────────────────────┘
                               │ HTTPS + WSS
                               │
┌──────────────────────────────▼───────────────────────────────┐
│                   Spring Boot 服务端                           │
│  ┌───────────────────────────────────────────────────────┐   │
│  │                  Controller 层                         │   │
│  │  AuthController │ UserController │ FriendController    │   │
│  │  ConversationController                               │   │
│  └──────────────────────────┬────────────────────────────┘   │
│                              │                                │
│  ┌──────────────────────────▼────────────────────────────┐   │
│  │                   Service 层                           │   │
│  │  AuthService │ UserService │ FriendService             │   │
│  │  ImageUploadService │ ServerBroadcastService           │   │
│  └──────────────────────────┬────────────────────────────┘   │
│                              │                                │
│  ┌──────────────────────────▼────────────────────────────┐   │
│  │                   Data 层                              │   │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐            │   │
│  │  │ MyBatis  │  │  Redis   │  │ 本地文件  │            │   │
│  │  │ -Plus    │  │ (可选)   │  │  存储     │            │   │
│  │  └────┬─────┘  └────┬─────┘  └────┬─────┘            │   │
│  └───────┼──────────────┼─────────────┼──────────────────┘   │
└──────────┼──────────────┼─────────────┼──────────────────────┘
           │              │             │
     ┌─────▼─────┐  ┌─────▼─────┐  ┌───▼───────────┐
     │  MySQL 8  │  │   Redis   │  │ uploads/      │
     │   / H2    │  │  (可选)   │  │ 本地磁盘      │
     └───────────┘  └───────────┘  └───────────────┘
```

### 1.2 通信架构

```
客户端（cloud/test模式）               服务端
  │                                      │
  │──── REST API (HTTPS) ──────────────▶│  用户/好友/群组/消息 CRUD
  │◀─── REST API 响应 ─────────────────│
  │                                      │
  │──── WebSocket (WSS) ──────────────▶│  人人实时消息
  │◀─── WebSocket 消息推送 ────────────│  AI 流式回复
  │                                      │
  │──── WebSocket 心跳 ───────────────▶│  保活
  │◀─── WebSocket 心跳响应 ───────────│

客户端（client模式）                  LocalAiEngine
  │                                      │
  │──── 直接方法调用（POJO）──────────▶│  AI 角色 CRUD / 流式聊天 / 记忆
  │◀─── 回调返回 ─────────────────────│  H2 本地数据库
```

### 1.3 三种运行模式

项目通过 `ServerMode` 枚举和 `Launcher` 启动策略支持三种运行模式：

| 模式 | 后端来源 | 数据库 | 适用场景 |
|------|----------|--------|----------|
| **LOCAL**（客户端包） | `LocalAiEngine` POJO 直接调用 | H2 本地文件 | 最终用户，仅 AI 功能 |
| **LOCAL**（测试包） | 嵌入式 Spring Boot（端口 8080） | H2 本地文件 | 开发调试，全功能 |
| **HOTSPOT** | 连接局域网测试服务器 | 服务器 MySQL | 局域网多人测试 |
| **CLOUD** | 连接公网服务器 | 服务器 MySQL + Redis | 正式部署，多用户并发 |

---

## 二、模块设计

### 2.1 四模块结构

```
voluntary_AI_chat/
├── common/        # 公共层（零依赖）
├── ai-core/       # AI 核心层（Spring 依赖均 optional）
├── server/        # 服务端（Spring Boot 全家桶）
└── client/        # 客户端（JavaFX + LocalAiEngine）
```

模块依赖关系：

```
common  ◀──── ai-core  ◀──── server
                 ▲
                 │
               client
```

### 2.2 common 模块

公共层，零 Spring 依赖，被所有模块引用。

```
common/src/main/java/com/voluntary/chat/common/
├── constant/
│   └── MessageTypes.java           # 消息类型常量
├── dto/
│   └── PageResult.java             # 分页结果封装
├── enums/
│   ├── GroupRole.java              # 群角色枚举（普通成员/管理员/群主）
│   ├── MessageType.java            # 消息类型枚举（文本/图片/AI/撤回/转发）
│   ├── SenderType.java             # 发送者类型（用户/AI）
│   └── TargetType.java             # 目标类型（用户/群组）
├── exception/
│   ├── BusinessException.java      # 业务异常
│   └── ErrorCode.java              # 错误码枚举
└── model/
    └── WebSocketMessage.java       # WebSocket 消息模型
```

### 2.3 ai-core 模块

AI 核心层，包名保持 `com.voluntary.chat.server` 以便 server 模块零改动引用。所有 Spring 依赖标记为 `optional`，支持纯 POJO 模式（客户端包）和 Spring 模式（服务端/测试包）。

```
ai-core/src/main/java/com/voluntary/chat/server/
├── client/                           # LLM HTTP 客户端
│   ├── OpenAiClient.java             # OpenAI 兼容协议调用（支持 OpenAI/DeepSeek/通义/智谱）
│   ├── EmbeddingClient.java          # 文本向量化客户端
│   └── VectorStoreClient.java        # 向量存储客户端（预留接口）
├── common/
│   ├── ApiResult.java                # 统一响应封装
│   └── GlobalExceptionHandler.java   # 全局异常处理
├── config/
│   ├── AiConfig.java                 # AI 配置 POJO（线程池、超时等）
│   ├── ClientBeansConfig.java        # 客户端 Bean 配置
│   └── SecurityConfig.java           # 安全配置
├── controller/
│   └── LocalController.java          # 本地 REST 控制器（嵌入式模式用）
├── dto/
│   ├── request/                      # AI 请求 DTO（4 个）
│   └── response/                     # AI 响应 DTO（4 个）
├── entity/
│   ├── AiProfile.java                # AI 角色实体
│   ├── AiGroupConfig.java            # AI 群配置实体
│   ├── AiMemory.java                 # AI 记忆实体
│   └── Message.java                  # 消息实体（共享）
├── mapper/
│   ├── AiProfileMapper.java          # AI 角色 Mapper
│   ├── AiGroupConfigMapper.java      # AI 群配置 Mapper
│   ├── AiMemoryMapper.java           # AI 记忆 Mapper
│   └── MessageMapper.java            # 消息 Mapper
├── security/
│   ├── JwtAuthenticationFilter.java  # JWT 认证过滤器
│   ├── JwtTokenProvider.java         # JWT 令牌提供者
│   └── SecurityUtils.java            # 安全工具类
├── service/
│   ├── AiChatService.java            # AI 对话服务（流式聊天、消息存储）
│   ├── AiGroupConfigService.java     # AI 群配置服务
│   ├── AiMemoryService.java          # AI 记忆服务（CRUD + 关键词检索）
│   └── BaseAiService.java            # AI 服务基类
├── util/
│   └── AesKeyUtil.java               # AES-256-GCM 加密工具（API Key 加密）
└── websocket/
    ├── AiStreamSender.java           # AI 流式消息推送器
    ├── AiWebSocketHandler.java       # AI WebSocket 处理器
    └── JwtHandshakeInterceptor.java  # WebSocket 握手 JWT 认证拦截器
```

### 2.4 server 模块

Spring Boot 服务端，提供完整的 REST API 和 WebSocket 实时通信。

```
server/src/main/java/com/voluntary/chat/server/
├── VoluntaryAiChatApplication.java   # Spring Boot 启动类
├── config/
│   ├── MyMetaObjectHandler.java      # MyBatis-Plus 自动填充（创建/更新时间）
│   ├── ServerStartupRunner.java      # 服务启动后执行器
│   ├── WebMvcConfig.java             # Web MVC 配置（静态资源映射）
│   └── redis/
│       ├── SmsCodeStorage.java       # 验证码存储接口
│       ├── RedisSmsCodeStorage.java  # Redis 实现
│       └── MemorySmsCodeStorage.java # 内存实现（无 Redis 时降级）
├── controller/
│   ├── AuthController.java           # 认证（注册/登录/刷新/忘记密码）
│   ├── UserController.java           # 用户（资料查询/修改/搜索）
│   ├── FriendController.java         # 好友（申请/列表/删除）
│   └── ConversationController.java   # 会话列表
├── dto/
│   ├── request/                      # 请求 DTO（22 个）
│   └── response/                     # 响应 DTO（17 个）
├── entity/
│   ├── User.java                     # 用户实体
│   ├── UserToken.java                # Token 实体
│   ├── Friend.java                   # 好友关系实体
│   ├── FriendApply.java              # 好友申请实体
│   ├── GroupEntity.java              # 群组实体
│   ├── GroupMember.java              # 群成员实体
│   ├── Message.java                  # 消息实体
│   └── MessageRead.java              # 消息已读实体
├── mapper/
│   ├── UserMapper.java
│   ├── UserTokenMapper.java
│   ├── FriendMapper.java
│   ├── FriendApplyMapper.java
│   ├── GroupMapper.java
│   ├── MessageMapper.java
│   └── MessageReadMapper.java
├── security/
│   ├── JwtAuthenticationFilter.java  # JWT 认证过滤器（服务端版）
│   ├── JwtTokenProvider.java         # JWT 令牌提供者
│   └── SecurityUtils.java            # 安全工具类
├── service/
│   ├── AuthService.java              # 认证服务（注册/登录/Token管理）
│   ├── UserService.java              # 用户服务
│   ├── FriendService.java            # 好友服务
│   ├── ImageUploadService.java       # 图片上传服务（本地文件存储）
│   └── ServerBroadcastService.java   # 服务器广播服务（WebSocket 在线用户通知）
└── websocket/
    └── ChatWebSocketHandler.java     # 聊天 WebSocket 处理器（继承 AiWebSocketHandler）
```

### 2.5 client 模块

JavaFX 桌面客户端，采用 MVVM 架构。

```
client/src/main/java/org/example/client/
├── Launcher.java                     # 启动入口（三模式启动策略 + 单例检测）
├── App.java                          # JavaFX Application（窗口管理、视图切换、系统托盘）
├── config/
│   ├── ClientConfig.java             # 客户端配置
│   ├── ServerConnectionManager.java  # 服务器连接管理器
│   └── ServerMode.java               # 运行模式枚举（LOCAL/HOTSPOT/CLOUD）
├── controller/                       # FXML 控制器（17 个）
│   ├── LoginController.java          # 登录
│   ├── RegisterController.java       # 注册
│   ├── ForgotPasswordController.java # 忘记密码
│   ├── MainController.java           # 主界面
│   ├── FriendController.java         # 好友面板
│   ├── GroupController.java          # 群聊面板
│   ├── GroupCreateController.java    # 创建群组对话框
│   ├── GroupInviteController.java    # 邀请成员对话框
│   ├── GroupInfoController.java      # 群信息对话框
│   ├── GroupAvatarController.java    # 群头像修改
│   ├── AiController.java             # AI 面板
│   ├── AiEditController.java         # AI 角色编辑
│   ├── AiEditDialog.java             # AI 编辑对话框
│   ├── ProfileController.java        # 个人资料
│   ├── ImagePreviewDialog.java       # 图片预览（发送前）
│   ├── ImageViewerDialog.java        # 图片查看器（点击放大）
│   └── NotificationDialog.java       # 通知对话框
├── engine/                           # 本地 AI 引擎
│   ├── LocalAiEngine.java            # 核心引擎（单例、懒加载、H2 JDBC 直连）
│   ├── AiStreamCallback.java         # 流式回调接口
│   ├── JdbcAiProfileRepository.java  # AI 角色 JDBC 仓库
│   └── JdbcUserRepository.java       # 用户 JDBC 仓库
├── model/                            # 数据模型（22 个）
│   ├── ApiResponse.java              # 通用 API 响应
│   ├── LoginRequest/Response.java    # 登录请求/响应
│   ├── SendMessageRequest.java       # 发送消息请求
│   ├── MessageInfo.java              # 消息信息
│   ├── ConversationInfo.java         # 会话信息
│   ├── AiProfile.java                # AI 角色
│   ├── FriendResponse.java           # 好友信息
│   ├── GroupInfo.java                # 群组信息
│   └── ...                           # 其他请求/响应模型
├── server/
│   └── EmbeddedServerStarter.java    # 嵌入式 Spring Boot 启动配置（测试包用）
├── service/                          # 业务服务
│   ├── BaseHttpService.java          # HTTP 基类（OkHttp 封装、Token 注入、错误处理）
│   ├── AuthService.java              # 认证服务
│   ├── ChatService.java              # 聊天服务（消息发送、撤回、历史）
│   ├── FriendService.java            # 好友服务
│   ├── GroupService.java             # 群组服务
│   ├── AiService.java                # AI 服务（角色 CRUD、群配置）
│   ├── UserService.java              # 用户服务
│   ├── WebSocketClient.java          # WebSocket 客户端
│   ├── WebSocketConnection.java      # WebSocket 连接封装
│   └── DualWebSocketManager.java     # 双 WebSocket 管理器（预留）
├── util/
│   ├── ErrorCodeRegistry.java        # 错误码注册表
│   ├── JsonUtils.java                # JSON 工具类
│   └── ServerDiscovery.java          # 服务器自动发现（UDP 广播 + IP 扫描）
└── view/                             # ViewModel 层（MVVM）
    ├── LoginViewModel.java           # 登录 ViewModel
    ├── RegisterViewModel.java        # 注册 ViewModel
    ├── ForgotPasswordViewModel.java  # 忘记密码 ViewModel
    ├── MainViewModel.java            # 主界面 ViewModel
    ├── ChatViewModel.java            # 聊天 ViewModel
    ├── FriendListViewModel.java      # 好友列表 ViewModel
    ├── GroupListViewModel.java       # 群组列表 ViewModel
    ├── ProfileViewModel.java         # 个人资料 ViewModel
    └── AiViewModel.java              # AI ViewModel
```

客户端 UI 资源：

```
client/src/main/resources/
├── fxml/                             # 13 个 FXML 布局文件
│   ├── login.fxml, register.fxml, forgot_password.fxml
│   ├── main.fxml, friend_panel.fxml, group_panel.fxml, ai_panel.fxml
│   ├── group_create_dialog.fxml, group_invite_dialog.fxml
│   ├── ai_edit_dialog.fxml, group_avatar_dialog.fxml
│   ├── group_info_dialog.fxml, profile_dialog.fxml
└── css/                              # 9 个样式文件
    ├── default.css, auth.css, common.css
    ├── friend.css, group.css, group_create.css
    ├── chat.css, ai.css, main.css
```

---

## 三、核心流程

### 3.1 消息发送流程（人人聊天 - 云端模式）

```
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│  客户端  │───▶│WebSocket │───▶│  Chat    │───▶│  消息    │
│ 发送消息 │    │ Handler  │    │WebSocket │    │  存储    │
│          │    │          │    │ Handler  │    │  MySQL   │
└──────────┘    └──────────┘    └────┬─────┘    └──────────┘
                                     │
                                     ▼
                               ┌──────────┐
                               │  广播    │
                               │WebSocket │
                               └────┬─────┘
                                    │
                      ┌─────────────┼─────────────┐
                      ▼             ▼             ▼
                ┌──────────┐ ┌──────────┐ ┌──────────┐
                │ 目标用户 │ │ 群组成员 │ │  消息    │
                │  在线推送│ │  批量推送│ │  已读表  │
                └──────────┘ └──────────┘ └──────────┘
```

### 3.2 AI 对话流程（云端/测试模式）

```
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│  客户端  │───▶│WebSocket │───▶│   Ai     │───▶│  OpenAi  │
│ 发送消息 │    │ Handler  │    │  Chat    │    │  Client  │
│          │    │          │    │ Service  │    │ (HTTP)   │
└──────────┘    └──────────┘    └────┬─────┘    └────┬─────┘
                                     │               │
                                     ▼               ▼
                               ┌──────────┐    ┌──────────┐
                               │  记忆    │    │  LLM     │
                               │  检索    │    │  API     │
                               │(关键词)  │    │(流式SSE) │
                               └──────────┘    └────┬─────┘
                                                    │
                                                    ▼
                                              ┌──────────┐
                                              │  AiStream│
                                              │  Sender  │
                                              │(WebSocket│
                                              │  流式推送)│
                                              └────┬─────┘
                                                   │
                                                   ▼
                                             ┌──────────┐
                                             │  客户端  │
                                             │  实时显示│
                                             └──────────┘
```

### 3.3 AI 对话流程（客户端包 - POJO 模式）

```
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│  客户端  │───▶│  Local   │───▶│  AiChat  │───▶│  OpenAi  │
│  UI 层   │    │ AiEngine │    │ Service  │    │  Client  │
│          │    │ (POJO)   │    │(无Spring)│    │ (HTTP)   │
└──────────┘    └──────────┘    └────┬─────┘    └────┬─────┘
                                     │               │
                                     ▼               ▼
                               ┌──────────┐    ┌──────────┐
                               │  H2 本地 │    │  LLM     │
                               │  数据库  │    │  API     │
                               │(JDBC直连)│    │(流式SSE) │
                               └──────────┘    └────┬─────┘
                                                    │
                                                    ▼
                                              ┌──────────┐
                                              │  回调    │
                                              │ AiStream │
                                              │ Callback │
                                              └────┬─────┘
                                                   │
                                                   ▼
                                             ┌──────────┐
                                             │  客户端  │
                                             │  实时显示│
                                             └──────────┘
```

### 3.4 客户端启动流程

```
Launcher.main()
      │
      ▼
┌───────────┐    是    ┌────────────────┐
│ 端口 59999│────────▶│ 已有实例运行   │
│ 可绑定？  │         │ 发送唤醒信号   │
└─────┬─────┘         │ 退出           │
      │ 否            └────────────────┘
      ▼
┌───────────┐
│ 读取模式  │
│ ServerMode│
└─────┬─────┘
      │
      ├─ LOCAL ──▶ Class.forName(server模块)
      │              │
      │              ├─ 找到（测试包）──▶ 启动嵌入式 Spring Boot
      │              │                     端口 8080，profile=local
      │              │
      │              └─ 未找到（客户端包）──▶ 初始化 LocalAiEngine
      │                                       H2 JDBC 直连，~200ms
      │
      ├─ HOTSPOT ──▶ UDP 广播发现 + IP 扫描
      │               连接局域网服务器
      │
      └─ CLOUD ────▶ 连接公网服务器
                      base-url 从配置读取
      │
      ▼
┌───────────┐
│ 启动      │
│ JavaFX UI │
│ App.java  │
└───────────┘
```

---

## 四、技术选型

### 4.1 已采用技术

| 技术 | 版本 | 用途 | 状态 |
|------|------|------|------|
| Java | 17 | 运行时 | 已采用 |
| JavaFX | 17.0.13 | 客户端 UI 框架 | 已采用 |
| Spring Boot | 3.5.15 | 服务端主框架 | 已采用 |
| Spring Security | (随 Boot) | 安全认证框架 | 已采用 |
| MyBatis-Plus | 3.5.15 | ORM（注解模式，无 XML Mapper） | 已采用 |
| JJWT | 0.12.5 / 0.12.6 | JWT 令牌处理 | 已采用 |
| Flyway | (随 Boot) | 数据库迁移（MySQL + H2 双 vendor） | 已采用 |
| H2 Database | (随 Boot) | 本地嵌入式数据库（MySQL 兼容模式） | 已采用 |
| MySQL | 8.0+ | 云端关系型数据库 | 已采用 |
| Redis | (可选) | 缓存（在线状态、Token、验证码） | 已采用（可选） |
| Jackson | 2.x | JSON 序列化 | 已采用 |
| Lombok | - | 代码简化 | 已采用 |
| MapStruct | 1.5.5.Final | DTO 映射 | 已采用 |
| OkHttp | - | 客户端 HTTP 请求 | 已采用 |
| SLF4J + Logback | - | 日志 | 已采用 |
| jpackage | (JDK 内置) | 桌面应用打包（exe 安装包 + 内嵌 JRE） | 已采用 |

### 4.2 代码质量工具

| 工具 | 版本 | 用途 | 配置文件 |
|------|------|------|----------|
| Checkstyle | 10.14.2 | 代码风格检查 | `checkstyle.xml` |
| SpotBugs | 4.8.3.1 | 静态缺陷分析 | `findbugs-exclude.xml` |
| JaCoCo | 0.8.11 | 测试覆盖率（行 ≥70%，分支 ≥60%） | POM 配置 |
| Sonar | - | 代码质量平台 | `mvn sonar:sonar -Dsonar` |

### 4.3 规划中技术（云端演进）

以下技术在项目初期设计时被纳入技术选型，用于支撑**云端多用户部署**场景。当前实现使用了更轻量的替代方案，后续上云时按需引入。

| 技术 | 规划用途 | 当前替代方案 | 引入时机 |
|------|----------|-------------|----------|
| **MinIO** | 分布式对象存储（图片/文件） | 本地磁盘 `uploads/` 目录 | 多服务器部署、需要 CDN 时 |
| **Milvus / Qdrant** | 向量数据库（AI 记忆 RAG 检索） | MySQL `ai_memory` 表 + 关键词匹配 | 单用户记忆 >1 万条、或多用户并发检索时 |
| **RabbitMQ** | 消息队列（异步消息处理、延迟队列） | `ScheduledExecutorService` 直接调度 | 多服务器消息投递、需要可靠队列时 |
| **DJL + ONNX** | 本地小模型推理（句子完整性检测） | 直接发送消息给远程 AI | 客户端需要本地预判断、离线 AI 能力时 |

> **设计原则**：本地用户（客户端包）开箱即用，不依赖任何重型中间件。云端用户按需加载，通过 Spring Profile 或 Maven Profile 控制组件启用。

---

## 五、安全设计

### 5.1 JWT 认证流程

```
┌──────────┐    ┌──────────┐    ┌──────────┐
│  客户端  │───▶│  服务端  │───▶│  MySQL   │
│ 登录请求 │    │AuthService│   │ 查询用户 │
└──────────┘    └────┬─────┘    └──────────┘
                     │
                     ▼
               ┌──────────┐
               │ PBKDF2   │
               │ 密码验证 │
               └────┬─────┘
                    │
                    ▼
               ┌──────────┐
               │ 生成     │
               │ JWT Token│
               │ Access+  │
               │ Refresh  │
               └────┬─────┘
                    │
                    ▼
               ┌──────────┐
               │ 存储到   │
               │ user_token│
               │ 表       │
               └────┬─────┘
                    │
                    ▼
               ┌──────────┐
               │ 返回给   │
               │ 客户端   │
               └──────────┘
```

- **Access Token**：有效期 2 小时，用于 API 认证
- **Refresh Token**：有效期 7 天，用于刷新 Access Token
- **过滤器链**：`JwtAuthenticationFilter` 拦截请求，从 Header 提取 Token 验证

### 5.2 API Key 加密

```
用户输入 API Key
      │
      ▼
┌──────────────┐
│  用户密码    │
│  PBKDF2 派生 │
│  AES 密钥    │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ AES-256-GCM  │
│ 加密 API Key │
│ (随机 IV)    │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ 存储到       │
│ ai_profile   │
│ .api_key_enc │
└──────────────┘
```

- 加密工具：`AesKeyUtil`（ai-core 模块）
- 密钥派生：用户密码 + PBKDF2 → 256 位 AES 密钥
- 加密算法：AES-256-GCM（认证加密，防篡改）
- 每次加密使用随机 IV，IV 与密文一起存储

### 5.3 密码安全

- 注册时：密码 + 随机盐值 → PBKDF2 哈希 → 存储 `password_hash` + `salt`
- 登录时：取出盐值，重新计算哈希比对
- 密码不以明文形式存储或传输

---

## 六、数据库设计概览

### 6.1 数据库表

| 表 | 说明 | 所属模块 |
|----|------|----------|
| `user` | 用户（ID、手机号、用户名、密码、个人资料） | server |
| `user_token` | JWT 令牌（访问令牌 + 刷新令牌） | server |
| `message` | 消息（文本/图片/AI/撤回/转发，支持用户和群组目标） | ai-core（实体）+ server（Mapper） |
| `message_read` | 消息已读记录 | server |
| `friend_apply` | 好友申请 | server |
| `friend` | 好友关系 | server |
| `chat_group` | 群组 | server |
| `group_member` | 群成员 | server |
| `ai_profile` | AI 角色（名称、人设、模型、加密 API Key） | ai-core |
| `ai_group_config` | AI 群配置（触发关键词、概率、冷却时间） | ai-core |
| `ai_memory` | AI 记忆（摘要、关键词、重要度、向量 ID） | ai-core |

### 6.2 数据库迁移

使用 Flyway 管理 Schema 版本，支持 MySQL 和 H2 两种 vendor：

```
server/src/main/resources/db/
├── schema.sql                        # 完整 MySQL Schema（初始化用）
├── schema-h2.sql                     # H2 兼容 Schema
└── migration/
    ├── mysql/
    │   ├── V1__init_schema.sql       # 初始表结构
    │   └── V2__add_user_profile_fields.sql  # 用户资料扩展字段
    └── h2/
        ├── V1__init_schema.sql
        └── V2__add_user_profile_fields.sql
```

### 6.3 Redis 缓存设计（云端模式）

| Key 模式 | 用途 | TTL |
|----------|------|-----|
| `online:{userId}` | 用户在线状态 | 5 分钟 |
| `token:{accessToken}` | Token 缓存 | 2 小时 |
| `unread:{userId}:{sessionId}` | 未读消息计数 | 永久 |
| `group:members:{groupId}` | 群成员列表缓存 | 10 分钟 |
| `sms:code:{phone}` | 短信验证码 | 5 分钟 |

> 无 Redis 时自动降级为内存实现（`MemorySmsCodeStorage`），本地/测试模式可完全不依赖 Redis。

---

## 七、性能优化

### 7.1 客户端优化
- `LocalAiEngine` 懒加载（首次调用 ~200ms 初始化）
- WebSocket 连接复用
- 图片异步加载 + 缩略图
- JavaFX UI 线程与业务线程分离

### 7.2 服务端优化
- MyBatis-Plus 自动填充（创建/更新时间）
- Redis 缓存热点数据（可选）
- 嵌入式模式：最小化 Tomcat（2 线程）、懒初始化、关闭 Banner
- Flyway 按 vendor 分离迁移脚本

### 7.3 构建优化
- Maven Profile 按场景打包，避免无关依赖
- ai-core 模块 Spring 依赖全部 optional，客户端包不引入 Spring 运行时
- jpackage 内嵌 JRE，用户无需安装 Java

---

## 八、监控与运维

### 8.1 日志
- SLF4J + Logback
- 云端模式日志输出到 `/opt/app/logs/server.log`
- 敏感信息（密码、API Key、Token）不记录到日志

### 8.2 关键监控指标
- 接口响应时间
- WebSocket 在线连接数
- 消息吞吐量
- AI 调用成功率与延迟
- 数据库连接池使用率
