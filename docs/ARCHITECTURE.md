# 架构设计文档

> 系统整体架构、模块设计和技术选型

---

## 一、系统架构

### 1.1 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                     JavaFX 桌面客户端                        │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────────┐  │
│  │  UI 层   │  │ 业务逻辑 │  │ WebSocket│  │ 本地模型   │  │
│  │ (FXML)   │  │  (MVVM)  │  │  Client  │  │   (DJL)    │  │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └─────┬──────┘  │
│       └──────────────┴─────────────┴──────────────┘         │
└─────────────────────────────┬───────────────────────────────┘
                              │ HTTPS + WSS
                              │
┌─────────────────────────────▼───────────────────────────────┐
│                   Spring Boot 服务端                         │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                    Gateway 层                        │   │
│  │              (REST Controller + WebSocket)            │   │
│  └──────────────────────────┬──────────────────────────┘   │
│                              │                              │
│  ┌──────────────────────────▼──────────────────────────┐   │
│  │                    Service 层                        │   │
│  │  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐       │   │
│  │  │ User   │ │ Friend │ │ Group  │ │Message │       │   │
│  │  │Service │ │Service │ │Service │ │Service │       │   │
│  │  └────────┘ └────────┘ └────────┘ └────────┘       │   │
│  │  ┌────────┐ ┌────────┐ ┌────────┐                  │   │
│  │  │   AI   │ │ Memory │ │ File   │                  │   │
│  │  │Service │ │Service │ │Service │                  │   │
│  │  └────────┘ └────────┘ └────────┘                  │   │
│  └──────────────────────────┬──────────────────────────┘   │
│                              │                              │
│  ┌──────────────────────────▼──────────────────────────┐   │
│  │                    Data 层                           │   │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐            │   │
│  │  │ MyBatis │  │  Redis  │  │  MinIO  │            │   │
│  │  │ -Plus   │  │ Client  │  │ Client  │            │   │
│  │  └────┬────┘  └────┬────┘  └────┬────┘            │   │
│  └───────┼─────────────┼─────────────┼────────────────┘   │
│          │             │             │                     │
└──────────┼─────────────┼─────────────┼─────────────────────┘
           │             │             │
     ┌─────▼─────┐ ┌─────▼─────┐ ┌─────▼─────┐
     │  MySQL 8  │ │   Redis   │ │   MinIO   │
     └───────────┘ └───────────┘ └───────────┘
```

### 1.2 通信架构

```
客户端                          服务端
  │                               │
  │──── WebSocket 连接 ──────────▶│
  │                               │
  │──── REST API 请求 ───────────▶│
  │◀─── REST API 响应 ───────────│
  │                               │
  │◀─── WebSocket 消息推送 ──────│
  │                               │
  │──── WebSocket 心跳 ─────────▶│
  │◀─── WebSocket 心跳响应 ─────│
```

---

## 二、模块设计

### 2.1 客户端模块划分

```
client/
├── src/main/java/com/voluntary/chat/client/
│   ├── App.java                    # 启动类
│   ├── controller/                 # FXML 控制器
│   │   ├── LoginController.java
│   │   ├── MainController.java
│   │   ├── ChatController.java
│   │   ├── GroupController.java
│   │   └── AIController.java
│   ├── view/                       # 视图模型（MVVM）
│   │   ├── LoginViewModel.java
│   │   ├── MainViewModel.java
│   │   ├── ChatViewModel.java
│   │   └── ...
│   ├── service/                    # 业务逻辑
│   │   ├── AuthService.java
│   │   ├── ChatService.java
│   │   ├── GroupService.java
│   │   ├── AIService.java
│   │   └── LocalModelService.java
│   ├── websocket/                  # WebSocket 客户端
│   │   ├── WebSocketClient.java
│   │   ├── MessageHandler.java
│   │   └── ConnectionManager.java
│   ├── model/                      # 数据模型
│   │   ├── User.java
│   │   ├── Message.java
│   │   ├── Group.java
│   │   └── AI.java
│   └── util/                       # 工具类
│       ├── FXUtils.java
│       ├── CryptoUtils.java
│       └── DateUtils.java
└── src/main/resources/
    ├── fxml/                       # FXML 布局
    │   ├── login.fxml
    │   ├── main.fxml
    │   ├── chat.fxml
    │   └── ...
    └── css/                        # 样式
        ├── default.css
        ├── dark.css
        └── ...
```

### 2.2 服务端模块划分

```
server/
├── src/main/java/com/voluntary/chat/server/
│   ├── App.java                    # 启动类
│   ├── controller/                 # REST 控制器
│   │   ├── AuthController.java
│   │   ├── UserController.java
│   │   ├── FriendController.java
│   │   ├── MessageController.java
│   │   ├── GroupController.java
│   │   └── AIController.java
│   ├── service/                    # 业务逻辑
│   │   ├── AuthService.java
│   │   ├── UserService.java
│   │   ├── FriendService.java
│   │   ├── MessageService.java
│   │   ├── GroupService.java
│   │   ├── AIService.java
│   │   ├── MemoryService.java
│   │   └── FileService.java
│   ├── mapper/                     # MyBatis Mapper
│   │   ├── UserMapper.java
│   │   ├── FriendRelationMapper.java
│   │   ├── MessageMapper.java
│   │   ├── GroupMapper.java
│   │   ├── AIMapper.java
│   │   └── ...
│   ├── entity/                     # 数据库实体
│   │   ├── User.java
│   │   ├── FriendRelation.java
│   │   ├── Message.java
│   │   ├── ChatGroup.java
│   │   ├── AIProfile.java
│   │   └── ...
│   ├── dto/                        # 数据传输对象
│   │   ├── request/
│   │   │   ├── LoginRequest.java
│   │   │   ├── RegisterRequest.java
│   │   │   └── ...
│   │   └── response/
│   │       ├── UserResponse.java
│   │       ├── MessageResponse.java
│   │       └── ...
│   ├── config/                     # 配置类
│   │   ├── WebConfig.java
│   │   ├── SecurityConfig.java
│   │   ├── WebSocketConfig.java
│   │   ├── RedisConfig.java
│   │   └── MinIOConfig.java
│   ├── security/                   # 安全模块
│   │   ├── JwtTokenProvider.java
│   │   ├── JwtAuthenticationFilter.java
│   │   └── UserDetailsServiceImpl.java
│   ├── websocket/                  # WebSocket 处理
│   │   ├── WebSocketHandler.java
│   │   ├── SessionManager.java
│   │   └── MessageDispatcher.java
│   ├── ai/                         # AI 模块
│   │   ├── AIProxy.java
│   │   ├── AIProvider.java
│   │   ├── OpenAIProvider.java
│   │   ├── MemoryManager.java
│   │   └── ProactiveChatTask.java
│   ├── task/                       # 定时任务
│   │   ├── MemorySummaryTask.java
│   │   └── ProactiveChatTask.java
│   └── util/                       # 工具类
│       ├── CryptoUtils.java
│       ├── SnowflakeIdGenerator.java
│       └── ...
└── src/main/resources/
    ├── mapper/                     # MyBatis XML
    │   ├── UserMapper.xml
    │   ├── MessageMapper.xml
    │   └── ...
    ├── application.yml             # 配置文件
    └── application-dev.yml         # 开发环境配置
```

### 2.3 公共模块

```
common/
├── src/main/java/com/voluntary/chat/common/
│   ├── constant/                   # 常量定义
│   │   ├── MessageTypes.java
│   │   ├── UserStatus.java
│   │   └── GroupRoles.java
│   ├── enums/                      # 枚举类
│   │   ├── MessageType.java
│   │   ├── SenderType.java
│   │   ├── TargetType.java
│   │   └── ...
│   ├── exception/                  # 自定义异常
│   │   ├── BusinessException.java
│   │   ├── AuthException.java
│   │   └── ErrorCode.java
│   ├── util/                       # 通用工具类
│   │   ├── StringUtils.java
│   │   ├── DateUtils.java
│   │   └── JsonUtils.java
│   └── model/                      # 共享数据模型
│       ├── WebSocketMessage.java
│       ├── PageResult.java
│       └── ...
└── pom.xml
```

---

## 三、核心流程

### 3.1 消息发送流程

```
┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐
│ 客户端  │───▶│WebSocket│───▶│消息分发 │───▶│消息存储 │
│ 发送消息│    │ 服务端  │    │  器     │    │  MySQL  │
└─────────┘    └─────────┘    └────┬────┘    └─────────┘
                                   │
                                   ▼
                             ┌─────────┐
                             │消息推送 │
                             │  Redis  │
                             └────┬────┘
                                  │
                    ┌─────────────┼─────────────┐
                    ▼             ▼             ▼
              ┌─────────┐  ┌─────────┐  ┌─────────┐
              │ 目标用户│  │ 目标群组│  │  AI     │
              │  在线   │  │  成员   │  │  回复   │
              └─────────┘  └─────────┘  └─────────┘
```

### 3.2 AI对话流程

```
┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐
│ 客户端  │───▶│服务端   │───▶│记忆检索 │───▶│AI代理层│
│ 发送消息│    │接收消息 │    │  RAG    │    │         │
└─────────┘    └─────────┘    └────┬────┘    └────┬────┘
                                   │              │
                                   ▼              ▼
                             ┌─────────┐    ┌─────────┐
                             │ Milvus  │    │ OpenAI  │
                             │向量检索 │    │   API   │
                             └─────────┘    └────┬────┘
                                                 │
                                                 ▼
                                           ┌─────────┐
                                           │流式响应 │
                                           │WebSocket│
                                           └────┬────┘
                                                │
                                                ▼
                                          ┌─────────┐
                                          │ 客户端  │
                                          │实时显示 │
                                          └─────────┘
```

### 3.3 AI主动聊天流程

```
┌─────────────────────────────────────────────────────────────┐
│                      定时任务调度器                           │
│                  (每用户随机间隔5-30分钟)                      │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │ 检查用户在线状态 │
                    │    (Redis)      │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │  在线？         │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              │ 是           │              │ 否
              ▼              │              ▼
    ┌─────────────────┐      │    ┌─────────────────┐
    │ 读取记忆摘要    │      │    │  跳过本次触发   │
    │  (MySQL+Milvus) │      │    └─────────────────┘
    └────────┬────────┘      │
             │               │
             ▼               │
    ┌─────────────────┐      │
    │ 生成主动聊天    │      │
    │  提示词         │      │
    └────────┬────────┘      │
             │               │
             ▼               │
    ┌─────────────────┐      │
    │  调用AI生成消息 │      │
    └────────┬────────┘      │
             │               │
             ▼               │
    ┌─────────────────┐      │
    │ WebSocket推送   │      │
    │  给用户         │      │
    └─────────────────┘      │
```

---

## 四、技术选型详解

### 4.1 客户端技术

| 技术 | 版本 | 用途 |
|------|------|------|
| JavaFX | 21+ | UI框架 |
| FXML | - | 布局文件 |
| DJL | 0.25+ | 本地模型推理 |
| Java-WebSocket | 1.5+ | WebSocket客户端 |
| Jackson | 2.15+ | JSON处理 |

### 4.2 服务端技术

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.2+ | 主框架 |
| Spring Security | 6.2+ | 安全认证 |
| MyBatis-Plus | 3.5+ | ORM |
| Spring WebSocket | 6.1+ | WebSocket服务端 |
| JJWT | 0.12+ | JWT处理 |

### 4.3 存储技术

| 技术 | 版本 | 用途 |
|------|------|------|
| MySQL | 8.0+ | 关系型数据 |
| Redis | 7.0+ | 缓存/状态 |
| MinIO | - | 对象存储 |
| Milvus | 2.3+ | 向量数据库 |

### 4.4 消息队列

| 技术 | 版本 | 用途 |
|------|------|------|
| RabbitMQ | 3.12+ | 异步消息/延迟队列 |

---

## 五、安全设计

### 5.1 认证流程

```
┌─────────┐    ┌─────────┐    ┌─────────┐
│ 客户端  │───▶│ 服务端  │───▶│  MySQL  │
│ 登录    │    │ 验证    │    │ 查询    │
└─────────┘    └────┬────┘    └─────────┘
                    │
                    ▼
              ┌─────────┐
              │ 生成JWT │
              │ Token   │
              └────┬────┘
                   │
                   ▼
              ┌─────────┐
              │ 返回Token│
              └────┬────┘
                   │
                   ▼
              ┌─────────┐
              │ 客户端  │
              │ 存储Token│
              └─────────┘
```

### 5.2 API Key 加密

```
用户输入API Key
      │
      ▼
┌─────────────┐
│ PBKDF2      │
│ 派生密钥    │
│ (用户密码)  │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ AES-256-GCM │
│ 加密API Key │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 存储到数据库 │
└─────────────┘
```

---

## 六、性能优化

### 6.1 客户端优化
- 聊天列表虚拟滚动
- 图片异步加载
- 消息本地缓存
- WebSocket连接池

### 6.2 服务端优化
- Redis缓存热点数据
- 数据库连接池
- 消息批量插入
- 异步处理耗时操作

### 6.3 网络优化
- WebSocket消息压缩
- 图片压缩和缩略图
- 分页加载
- 懒加载

---

## 七、监控与运维

### 7.1 日志规范
- 使用 SLF4J + Logback
- 日志级别：ERROR > WARN > INFO > DEBUG
- 敏感信息不记录到日志

### 7.2 监控指标
- 接口响应时间
- 在线用户数
- 消息吞吐量
- AI调用成功率
- 系统资源使用率

### 7.3 告警规则
- 接口响应时间 > 1s
- 错误率 > 1%
- 在线用户数异常波动
- 磁盘使用率 > 80%
