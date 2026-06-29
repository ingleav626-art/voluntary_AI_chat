# Voluntary AI Chat

一个支持主动式 AI 对话的 Java 桌面聊天软件。

## 项目简介

Voluntary AI Chat 是一款 JavaFX 桌面聊天应用，支持人人聊天、AI 单聊（流式输出）、多 AI 群聊、AI 主动找用户聊天、RAG 记忆管理等特色功能。项目采用前后端分离架构，客户端与服务端通过 REST + WebSocket 通信，AI 数据本地加密存储保障用户隐私。

## 技术栈

| 层级 | 技术 |
|------|------|
| 客户端 | JavaFX 17 + FXML + CSS + WebSocket 客户端 + H2 本地数据库 |
| 服务端 | Spring Boot 3.5.15 + Spring Security + JWT + WebSocket |
| ORM | MyBatis-Plus 3.5.15 |
| 数据库 | MySQL 8（云端）/ H2（本地嵌入式） |
| 缓存 | Redis（可选，云端模式使用） |
| AI | OpenAI 兼容协议代理 + RAG 记忆检索 + AES-256-GCM API Key 加密 |
| 数据库迁移 | Flyway |
| 构建 | Maven + JDK 17 |
| 代码质量 | Checkstyle 10.14.2 + SpotBugs 4.8.3.1 + JaCoCo 0.8.11 |
| 部署 | jpackage 桌面打包 / Spring Boot Fat JAR + Nginx 反向代理 |

## 项目结构

```
voluntary_AI_chat/
├── common/                # 公共模块（DTO、枚举、常量、异常）
├── ai-core/               # AI 核心模块（实体、Mapper、LLM 客户端、安全组件）
├── server/                # Spring Boot 服务端（REST API + WebSocket + 安全认证）
├── client/                # JavaFX 桌面客户端（UI + 本地 AI 引擎 + 嵌入式服务器）
├── docs/                  # 项目文档
│   ├── sql/               # 数据库 Schema
│   └── *.md               # 各类设计文档
├── dist/                  # 分发包
│   ├── client/            # 纯客户端包（jpackage 安装包）
│   ├── test/              # 测试包（含嵌入式 Spring Boot）
│   └── cloud/             # 云端部署包（Spring Boot Fat JAR）
├── tools/                 # 构建工具（Inno Setup 安装包制作）
└── pom.xml                # 根 POM
```

### 模块职责

| 模块 | 包名 | 职责 |
|------|------|------|
| **common** | `com.voluntary.chat.common` | 零依赖公共层：消息类型枚举、分页结果、业务异常、WebSocket 消息模型 |
| **ai-core** | `com.voluntary.chat.server` | AI 核心层：AI 角色/记忆/群配置实体与 Mapper、OpenAI/Embedding/VectorStore HTTP 客户端、JWT 认证组件、AES 加密工具、WebSocket AI 流式推送。Spring 依赖均为 optional，支持纯 POJO 模式运行 |
| **server** | `com.voluntary.chat.server` | 服务端：用户认证、好友管理、群组管理、消息收发、会话管理的 REST API 与 WebSocket 处理，依赖 ai-core 提供 AI 能力 |
| **client** | `org.example.client` | 客户端：JavaFX UI（13 个 FXML 视图 + 9 个 CSS 样式表）、MVVM ViewModel、REST/WebSocket 通信服务、LocalAiEngine 本地 AI 引擎、嵌入式服务器启动器 |

## 三种分发包

项目通过 Maven Profile 构建三种分发包，适配不同使用场景：

| 包 | Maven Profile | 内容 | 大小 | 用途 |
|----|--------------|------|------|------|
| **客户端包** | `client`（默认） | 纯 JavaFX + LocalAiEngine POJO + H2 | ~44MB | 最终用户使用，仅 AI 功能 |
| **测试包** | `test` | JavaFX + 完整嵌入式 Spring Boot + H2 | ~84MB | 开发调试，含完整后端 |
| **云端包** | `cloud` | Spring Boot Fat JAR | ~49MB | 服务器部署，人人+AI 全功能 |

### 客户端包（client）

- 使用 `LocalAiEngine`（纯 POJO，非 Spring），通过 JDBC 直连本地 H2 数据库
- 启动速度 <1 秒，无需安装数据库
- 仅支持 AI 单聊功能（人人聊天需连接云端服务器）
- AI 数据本地加密存储，API Key 使用 AES-256-GCM 加密

### 测试包（test）

- 内嵌完整 Spring Boot 后端，启动时自动初始化 H2 数据库
- 启动速度约 5-7 秒
- 支持人人聊天 + AI 聊天全部功能
- 适合开发阶段本地测试

### 云端包（cloud）

- Spring Boot Fat JAR，通过 `java -jar` 部署到服务器
- 连接 MySQL + Redis，支持多用户并发
- 需配合 Nginx 反向代理 + HTTPS

## 客户端启动模式

客户端支持三种启动模式，由 `ServerMode` 枚举定义：

| 模式 | 说明 | 后端来源 |
|------|------|----------|
| **LOCAL** | 本地模式（默认） | 检测 classpath 是否有 server 模块：有则启动嵌入式 Spring Boot，无则使用 LocalAiEngine POJO |
| **HOTSPOT** | 热点测试模式 | 连接局域网内的测试服务器，支持 UDP 自动发现 |
| **CLOUD** | 云端模式 | 连接公网部署的服务器 |

启动流程：`Launcher` 检测单例（端口 59999）→ 根据模式决定后端策略 → 初始化 JavaFX UI → `App` 加载 FXML 视图。

## 数据库设计

项目使用 12 张数据库表，通过 Flyway 管理迁移：

| 表名 | 说明 |
|------|------|
| `user` | 用户表（雪花算法 ID、手机号、用户名、密码哈希+盐值、头像、个人资料） |
| `user_token` | 用户 Token 表（JWT 访问令牌 + 刷新令牌） |
| `message` | 消息表（文本/图片/AI/撤回/转发，支持用户和群组目标） |
| `message_read` | 消息已读表（记录用户对每条消息的阅读时间） |
| `friend_apply` | 好友申请表（申请-同意/拒绝流程） |
| `friend` | 好友关系表（双向好友 + 备注名） |
| `chat_group` | 群组表（群名、头像、公告、群主、最大成员数） |
| `group_member` | 群成员表（群主/管理员/普通成员角色 + 群昵称） |
| `ai_profile` | AI 角色表（名称、人设、系统提示词、模型提供商、加密 API Key、温度、最大 Token） |
| `ai_group_config` | AI 群配置表（触发关键词、触发概率、冷却时间、启用状态） |
| `ai_memory` | AI 记忆表（摘要、关键词、重要度评分、向量 ID） |

所有表均包含 `is_deleted` 逻辑删除字段和 `create_time`/`update_time` 时间戳。

## 核心功能

### 用户系统
- 手机号注册（短信验证码）、JWT 登录认证（访问令牌 2 小时 + 刷新令牌 7 天）
- 个人资料管理（头像、昵称、签名、性别、年龄、生日、详细说明）
- 密码修改、手机号换绑、忘记密码

### 好友系统
- 好友申请 → 同意/拒绝流程
- 好友列表、好友备注、删除好友

### 单人聊天
- 文本消息、图片消息（上传、压缩、缩略图）
- 消息撤回（2 分钟内）、消息转发
- 消息已读回执、历史消息加载

### 群聊
- 创建群组、邀请/移除成员、退出群组
- 群主转让、解散群组、设置/取消管理员
- 群昵称、群头像、群公告（支持置顶）
- 群消息广播、@提及

### AI 功能
- **AI 单聊**：流式输出（SSE/WebSocket），支持 OpenAI/DeepSeek/通义/智谱等兼容接口
- **多 AI 群聊**：群内可挂载多个 AI 角色，支持 @定向回复、关键词触发、概率触发
- **AI 记忆管理**：对话摘要存储、关键词提取、重要度评分
- **API Key 安全**：AES-256-GCM 加密存储，用户密码派生密钥
- **本地 AI 引擎**：`LocalAiEngine` POJO 实现，支持本地登录、AI 角色 CRUD、流式聊天、记忆查询

### 安全设计
- JWT 认证（访问令牌 + 刷新令牌双令牌机制）
- Spring Security 过滤器链（JwtAuthenticationFilter）
- API Key AES-256-GCM 加密（AesKeyUtil）
- 密码 PBKDF2 哈希 + 盐值
- 请求频率限制（RateLimitingFilter）

## 构建与运行

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+（云端模式）
- Redis（云端模式，可选）

### 构建命令

```bash
# 构建全部模块
mvn clean package -DskipTests

# 构建纯客户端包（默认 profile）
mvn clean package -P client -DskipTests

# 构建测试包（含嵌入式 Spring Boot）
mvn clean package -P test -DskipTests

# 构建云端部署包
mvn clean package -P cloud -DskipTests
```

### 本地运行

```bash
# 运行客户端（测试包模式，含嵌入式后端）
mvn exec:java -pl client

# 运行服务端（独立模式）
java -jar server/target/voluntary-ai-chat-server-1.0-SNAPSHOT-exec.jar

# 使用 H2 内嵌数据库运行服务端
java -jar server/target/voluntary-ai-chat-server-1.0-SNAPSHOT-exec.jar --spring.profiles.active=h2
```

### 云端部署

```bash
# 启动云端服务
java -jar voluntary-ai-chat-server-1.0-SNAPSHOT-exec.jar --spring.profiles.active=cloud

# 或使用提供的启动脚本
./start-cloud.sh    # Linux
start-cloud.bat     # Windows
```

详细部署指南见 [docs/DEPLOYMENT_STEP_BY_STEP.md](docs/DEPLOYMENT_STEP_BY_STEP.md) 和 [docs/SERVER_DEPLOYMENT.md](docs/SERVER_DEPLOYMENT.md)。

## 代码质量

项目配置了完整的代码质量工具链：

| 工具 | 用途 | 配置 |
|------|------|------|
| **Checkstyle** | 代码风格检查 | `checkstyle.xml`，构建时自动执行 |
| **SpotBugs** | 静态缺陷分析 | `findbugs-exclude.xml`，构建时自动执行 |
| **JaCoCo** | 测试覆盖率 | 行覆盖率 ≥70%，分支覆盖率 ≥60% |
| **Sonar** | 代码质量平台 | `mvn sonar:sonar -Dsonar` 激活 |

### 测试

```bash
# 运行全部测试
mvn test

# 运行测试并生成覆盖率报告
mvn test jacoco:report

# 查看覆盖率报告
# 各模块 target/site/jacoco/index.html
```

## 文档目录

| 文档 | 说明 |
|------|------|
| [AI_SPEC.md](AI_SPEC.md) | AI 开发规范文档（协作规范、代码质量、安全规范） |
| [AGENTS.md](AGENTS.md) | AI Agent 开发准则（精简版，供 AI 助手每次会话加载） |
| [CONTRIBUTING.md](CONTRIBUTING.md) | 贡献指南（两人协作分支策略与工作流） |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 系统架构设计（模块结构、核心流程、安全设计） |
| [docs/FINAL_ARCHITECTURE.md](docs/FINAL_ARCHITECTURE.md) | 三包架构最终方案（客户端/测试/云端分包设计） |
| [docs/API.md](docs/API.md) | API 接口文档（REST + WebSocket + LocalAiEngine POJO API） |
| [docs/DATABASE.md](docs/DATABASE.md) | 数据库设计文档（MySQL DDL + Redis 缓存设计） |
| [docs/FEATURES.md](docs/FEATURES.md) | 功能规格文档（各模块详细功能定义） |
| [docs/ROADMAP.md](docs/ROADMAP.md) | 开发路线图（P0-P5 阶段规划） |
| [docs/TASK_BOARD.md](docs/TASK_BOARD.md) | 任务看板（当前进度跟踪） |
| [docs/CHAT_PLAN.md](docs/CHAT_PLAN.md) | 聊天功能开发计划 |
| [docs/GROUP_CHAT_PLAN.md](docs/GROUP_CHAT_PLAN.md) | 群聊功能开发计划 |
| [docs/AI_MODULE_PLAN.md](docs/AI_MODULE_PLAN.md) | AI 模块实现计划 |
| [docs/AI_PRIVACY_ARCHITECTURE.md](docs/AI_PRIVACY_ARCHITECTURE.md) | AI API Key 隐私架构设计 |
| [docs/AI_CORE_MIGRATION_PLAN.md](docs/AI_CORE_MIGRATION_PLAN.md) | ai-core 模块拆分计划 |
| [docs/CLIENT_INDEPENDENCE_PLAN.md](docs/CLIENT_INDEPENDENCE_PLAN.md) | 客户端独立化计划 |
| [docs/THREE_PACKAGE_ANALYSIS.md](docs/THREE_PACKAGE_ANALYSIS.md) | 三包拆分分析文档 |
| [docs/IMAGE_MODULE_PLAN.md](docs/IMAGE_MODULE_PLAN.md) | 图片功能完成计划 |
| [docs/RECALL_FIX_PLAN.md](docs/RECALL_FIX_PLAN.md) | 消息撤回修复计划 |
| [docs/HOTSPOT_TEST.md](docs/HOTSPOT_TEST.md) | 局域网热点测试指南 |
| [docs/DEPLOYMENT_STEP_BY_STEP.md](docs/DEPLOYMENT_STEP_BY_STEP.md) | 部署教程（新手友好） |
| [docs/SERVER_DEPLOYMENT.md](docs/SERVER_DEPLOYMENT.md) | 云端部署完整指南 |
| [docs/UNINSTALLATION.md](docs/UNINSTALLATION.md) | 云端卸载教程 |
| [docs/ERROR_LOG.md](docs/ERROR_LOG.md) | 错误分析与修复记录 |

## 开发阶段

| 阶段 | 内容 | 状态 |
|------|------|------|
| P0 | 框架搭建、用户系统、单聊文字、WebSocket 通信 | 已完成 |
| P1 | 群聊、好友管理、图片消息 | 已完成 |
| P2 | AI 接入（单聊 AI、自定义人设、Key 管理） | 已完成 |
| P3 | 多 AI 群、AI 主动聊天、记忆管理（RAG） | 进行中 |
| P4 | 本地句子完整性模型、转发、背景图 | 计划中 |
| P5 | 优化与发布 | 计划中 |
