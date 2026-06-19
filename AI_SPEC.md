# Voluntary AI Chat - AI开发规范文档

> 本项目为 Java 桌面 AI 聊天软件，采用 JavaFX（客户端）+ Spring Boot 3（服务端）+ WebSocket 通信架构。
> AI 辅助开发时必须严格遵守本文档规范。

---

## 一、多人协作规范

### 1.1 分支管理
- **主分支保护**：`main` 分支必须通过 Pull Request 合并，禁止直接推送
- **分支命名规范**：
  - `feature/<模块>/<功能描述>` - 新功能开发
  - `fix/<模块>/<问题描述>` - Bug修复
  - `docs/<描述>` - 文档更新
  - `refactor/<模块>/<描述>` - 代码重构
- **分支生命周期**：功能完成后及时删除，避免分支堆积
- **模块示例**：`user`、`chat`、`group`、`ai`、`memory`、`client`、`server`、`common`

### 1.2 冲突处理
- **拉取前同步**：开始工作前必须 `git pull --rebase origin main`
- **冲突解决原则**：
  - 优先理解冲突双方的意图，而非简单选择一方
  - 复杂冲突必须与相关开发者沟通确认
  - 解决冲突后必须完整测试，确保功能正常
- **小步提交**：频繁提交小改动，减少冲突概率
- **文件锁定**：修改以下配置文件时，先检查是否有人正在修改：
  - `pom.xml`（Maven 依赖）
  - `application.yml`（Spring Boot 配置）
  - `docker-compose.yml`
  - FXML 文件（布局变更影响多人）

### 1.3 代码审查
- 每个 PR 至少需要一位其他开发者审查
- 审查重点：代码逻辑、安全性、性能、可维护性
- **AI 生成的代码必须经过人工审查后才能合并**

---

## 二、项目结构规范

### 2.1 模块划分
```
voluntary_AI_chat/
├── client/                    # JavaFX 客户端
│   ├── src/main/java/
│   │   └── com/voluntary/chat/client/
│   │       ├── controller/    # FXML 控制器
│   │       ├── view/          # 视图模型（MVVM）
│   │       ├── service/       # 客户端业务逻辑
│   │       ├── websocket/     # WebSocket 客户端
│   │       ├── model/         # 本地数据模型
│   │       ├── util/          # 工具类
│   │       └── App.java       # 启动类
│   └── src/main/resources/
│       ├── fxml/              # FXML 布局文件
│       └── css/               # 样式文件
├── server/                    # Spring Boot 服务端
│   ├── src/main/java/
│   │   └── com/voluntary/chat/server/
│   │       ├── controller/    # REST + WebSocket 控制器
│   │       ├── service/       # 业务逻辑层
│   │       ├── mapper/        # MyBatis-Plus Mapper
│   │       ├── entity/        # 数据库实体
│   │       ├── dto/           # 数据传输对象
│   │       ├── config/        # 配置类
│   │       ├── security/      # JWT + Spring Security
│   │       ├── websocket/     # WebSocket 处理
│   │       ├── ai/            # AI 代理层
│   │       ├── task/          # 定时任务（AI 主动聊天）
│   │       └── App.java       # 启动类
│   └── src/main/resources/
│       ├── mapper/            # MyBatis XML
│       └── application.yml
├── common/                    # 公共模块
│   ├── src/main/java/
│   │   └── com/voluntary/chat/common/
│   │       ├── constant/      # 常量定义
│   │       ├── enums/         # 枚举类
│   │       ├── exception/     # 自定义异常
│   │       ├── util/          # 通用工具类
│   │       └── model/         # 共享数据模型
│   └── pom.xml
└── pom.xml                    # 父 POM
```

### 2.2 文件长度限制
| 文件类型 | 最大行数 | 说明 |
|---------|---------|------|
| Controller | 300行 | 超过应拆分为多个控制器 |
| Service | 400行 | 超过应按功能拆分为多个 Service |
| Entity/DTO | 150行 | 超过应检查字段是否过多 |
| Mapper/DAO | 200行 | 超过应拆分为多个 Mapper |
| 工具类 | 200行 | 超过应按功能拆分 |
| FXML 文件 | 300行 | 超过应拆分为子组件 |
| 测试类 | 500行 | 超过应按测试套件拆分 |
| 配置文件 | 100行 | 超过应拆分为多个配置 |

### 2.3 单一职责原则
- **每个类只做一件事**：Controller 只处理请求，Service 处理业务逻辑，Mapper 处理数据访问
- **类名即职责**：类名应准确反映其内容，如 `UserService`、`ChatWebSocketHandler`
- **包即模块**：相关类放在同一包下，包名反映功能模块

---

## 三、代码质量规范

### 3.1 注释规范
- **必须注释的情况**：
  - 复杂业务逻辑的解释（如 AI 主动聊天触发逻辑）
  - 非显而易见的设计决策（如为什么选择某种消息路由策略）
  - 临时解决方案及原因
  - 正则表达式的含义
  - 性能优化的理由
  - WebSocket 消息协议说明
- **禁止注释的情况**：
  - 显而易见的代码（如 `// 设置用户名`）
  - 已被版本控制记录的删除代码
  - 无意义的注释（如 `// TODO: 后续处理` 但无具体说明）

### 3.2 Git 提交规范
```
<type>(<scope>): <subject>

<body>

<footer>
```

**Type 类型**：
- `feat`: 新功能
- `fix`: Bug修复
- `docs`: 文档更新
- `style`: 代码格式调整（不影响逻辑）
- `refactor`: 代码重构
- `test`: 测试相关
- `chore`: 构建/工具链更新

**Scope 范围**（对应模块）：
- `client`: 客户端
- `server`: 服务端
- `common`: 公共模块
- `user`: 用户系统
- `chat`: 单聊
- `group`: 群聊
- `ai`: AI 功能
- `memory`: 记忆管理
- `ws`: WebSocket
- `auth`: 认证鉴权

**Subject 要求**：
- 使用中文，保持团队一致
- 不超过50个字符
- 使用祈使语气（如"添加"而非"添加了"）
- 不以句号结尾

**Body 要求**：
- 说明为什么做这个改动
- 描述改动的具体内容
- 如有必要，说明测试方法

**示例**：
```
feat(ai): 实现 AI 主动聊天定时任务

使用 RabbitMQ 延迟队列实现随机间隔触发：
- 每个用户独立的随机间隔（5-30分钟）
- 读取最近20条记忆摘要生成提示词
- 调用 AI 接口生成主动消息
- 在线用户通过 WebSocket 推送

测试覆盖：单元测试 + 集成测试
```

### 3.3 代码风格
- 使用项目配置的 Checkstyle 规则
- 变量命名：语义化命名，避免单字母变量（循环变量除外）
- 方法命名：动词开头，如 `getUserInfo`、`handleMessage`
- 常量命名：全大写下划线分隔
- **Java 特定规范**：
  - 遵循阿里巴巴 Java 开发手册
  - 使用 Lombok 减少样板代码（@Data、@Builder 等）
  - 异常处理：业务异常使用自定义异常类，避免吞异常

---

## 四、文件复用规范

### 4.1 通用工具类复用
- **工具类位置**：`common/src/main/java/com/voluntary/chat/common/util/`
- **避免重复实现**：使用前先搜索是否已有类似功能
- **命名规范**：`XxxUtil.java` 或 `XxxUtils.java`

### 4.2 Service 层复用
- **公共 Service**：放在 `common` 模块
- **业务 Service**：放在对应模块的 `service` 包下
- **依赖注入**：使用 Spring 的 `@Autowired` 或构造器注入

### 4.3 DTO/Entity 复用
- **共享 DTO**：放在 `common` 模块的 `model` 包下
- **模块私有 DTO**：放在对应模块的 `dto` 包下
- **Entity**：只放在 `server` 模块，客户端不直接使用数据库实体

---

## 五、测试规范

### 5.1 测试覆盖率要求
| 类型 | 最低覆盖率 | 说明 |
|------|-----------|------|
| 工具类 | 90% | 纯函数必须测试 |
| Service | 80% | 核心业务逻辑 |
| Controller | 75% | API 接口逻辑 |
| 整体项目 | 70% | 整体质量保障 |

### 5.2 测试类型
- **单元测试**：测试独立方法和类（使用 Mockito）
- **集成测试**：测试 Spring 上下文和数据库交互（使用 @SpringBootTest）
- **WebSocket 测试**：测试消息收发逻辑
- **端到端测试**：测试完整用户流程

### 5.3 测试编写原则
- **测试行为而非实现**：关注输入输出，不关注内部实现
- **每个测试方法一个断言**：保持测试的独立性和清晰性
- **测试边界条件**：正常流程、边界值、异常情况
- **Mock 外部依赖**：API 调用、数据库、第三方服务等

### 5.4 测试文件结构
```
server/
├── src/main/java/com/voluntary/chat/server/
│   └── service/
│       └── UserService.java
└── src/test/java/com/voluntary/chat/server/
    └── service/
        └── UserServiceTest.java
```

---

## 六、AI 开发特别注意事项

### 6.1 常见 AI 错误（针对本项目）
1. **过度抽象**：为可能不需要的扩展性创建复杂抽象（如过度使用设计模式）
2. **忽略上下文**：不理解现有代码结构，强行插入新代码
3. **忽略 JavaFX 线程模型**：WebSocket 回调不在 JavaFX UI 线程，必须用 `Platform.runLater()` 更新 UI
4. **硬编码**：将配置、常量直接写在代码中
5. **忽略错误处理**：不处理异步操作的异常情况
6. **类型不安全**：使用 `Object` 类型或忽略类型检查
7. **重复代码**：不检查已有实现，重复编写相同功能
8. **忽略性能**：创建不必要的对象或计算
9. **安全漏洞**：暴露 API Key、不验证用户输入
10. **文档缺失**：不更新相关文档
11. **忽略 WebSocket 消息协议**：不遵循既定的消息格式
12. **数据库 N+1 查询**：在循环中执行数据库查询
13. **忽略 Redis 缓存一致性**：更新数据后未同步更新缓存

### 6.2 AI 代码审查清单
- [ ] 是否理解现有代码结构和约定？
- [ ] 是否遵循项目的代码风格（阿里巴巴 Java 开发手册）？
- [ ] 是否有适当的异常处理？
- [ ] 是否有必要的类型定义和参数校验？
- [ ] 是否有重复代码可以复用？
- [ ] 是否考虑了性能影响（数据库查询、缓存使用）？
- [ ] 是否有安全漏洞（SQL 注入、XSS、API Key 泄露）？
- [ ] 是否需要更新测试？
- [ ] 是否需要更新文档？
- [ ] 是否遵循单一职责原则？
- [ ] WebSocket 消息是否遵循既定协议？
- [ ] JavaFX UI 更新是否在主线程执行？
- [ ] Redis 缓存是否正确更新？
- [ ] AI 接口调用是否有超时和重试机制？

### 6.3 AI 协作最佳实践
1. **明确需求**：提供清晰、具体的需求描述，说明涉及哪些模块
2. **分步实现**：复杂功能分步骤实现，每步验证
3. **代码审查**：AI 生成的代码必须经过人工审查
4. **测试验证**：确保 AI 代码有充分的测试覆盖
5. **文档同步**：及时更新相关文档
6. **遵循现有模式**：AI 生成的代码必须遵循项目现有的设计模式和代码风格

---

## 七、安全规范

### 7.1 敏感信息处理
- **禁止提交**：`.env`、`application-local.yml`、API 密钥、数据库密码等
- **环境变量**：敏感配置必须使用环境变量或配置中心
- **代码审查**：检查是否意外暴露敏感信息
- **API Key 存储**：用户 API Key 必须 AES 加密存储，使用用户密码派生密钥

### 7.2 输入验证
- **用户输入**：所有用户输入必须验证和清理
- **API 参数**：后端接口必须使用 `@Valid` 注解验证请求参数
- **XSS 防护**：对用户输入进行转义处理
- **SQL 注入**：使用 MyBatis-Plus 的参数化查询，禁止拼接 SQL

### 7.3 认证鉴权
- **JWT Token**：使用 Spring Security + JWT 实现认证
- **Token 刷新**：实现 Token 自动刷新机制
- **权限控制**：使用 RBAC 模型控制用户权限

---

## 八、性能规范

### 8.1 客户端性能（JavaFX）
- **UI 线程保护**：WebSocket 回调必须使用 `Platform.runLater()` 更新 UI
- **列表虚拟化**：聊天消息列表使用虚拟滚动
- **图片异步加载**：图片使用异步加载，避免阻塞 UI 线程
- **本地缓存**：常用数据使用本地缓存，减少网络请求

### 8.2 服务端性能
- **数据库查询**：避免 N+1 查询，使用 MyBatis-Plus 的批量操作
- **分页查询**：大数据集必须分页
- **Redis 缓存**：频繁访问的数据使用 Redis 缓存
- **连接池**：合理配置数据库和 Redis 连接池
- **异步处理**：耗时操作使用 RabbitMQ 异步处理

### 8.3 WebSocket 性能
- **消息压缩**：启用 WebSocket 消息压缩
- **心跳机制**：实现心跳检测，及时清理断开的连接
- **消息队列**：高并发场景使用消息队列削峰

---

## 九、文档规范

### 9.1 代码文档
- **JavaDoc**：复杂方法必须添加 JavaDoc 注释
- **类文档**：每个类必须说明其职责和使用方式
- **接口文档**：REST API 使用 Swagger/OpenAPI 文档

### 9.2 项目文档
- **README.md**：项目简介、技术架构、开发指南
- **CHANGELOG.md**：版本更新记录
- **CONTRIBUTING.md**：贡献指南
- **API 文档**：REST API 接口文档
- **数据库设计文档**：数据库表结构说明

### 9.3 架构文档
- **系统架构图**：整体架构说明
- **模块设计文档**：各模块职责和交互
- **消息协议文档**：WebSocket 消息格式说明

---

## 十、违规处理

### 10.1 自动化检查
- **CI/CD**：PR 必须通过所有自动化检查
- **代码格式**：Checkstyle 检查
- **测试覆盖**：必须达到最低覆盖率要求
- **编译检查**：Maven 编译必须通过
- **安全扫描**：依赖漏洞扫描

### 10.2 人工审查
- **代码质量**：审查代码逻辑和可维护性
- **安全审查**：检查潜在安全问题
- **性能审查**：评估性能影响
- **架构审查**：检查是否符合整体架构设计

---

## 附录

### A. 工具配置
- Maven 配置文件：`pom.xml`
- Checkstyle 配置文件：`checkstyle.xml`
- Spring Boot 配置文件：`application.yml`
- Lombok 配置：`lombok.config`

### B. 常用命令
```bash
# 安装依赖
mvn clean install

# 运行测试
mvn test

# 代码检查
mvn checkstyle:check

# 构建项目
mvn clean package

# 启动服务端
mvn spring-boot:run -pl server

# 启动客户端
mvn javafx:run -pl client
```

### C. 技术栈
- **客户端**：JavaFX + FXML + WebSocket + DJL（本地模型）
- **服务端**：Spring Boot 3 + MyBatis-Plus + Spring Security + JWT + WebSocket
- **存储**：MySQL 8 + Redis + Milvus/Qdrant（向量数据库）+ MinIO
- **消息队列**：RabbitMQ（延迟队列）
- **AI 能力**：OpenAI 兼容协议 + RAG（检索增强生成）
- **构建工具**：Maven + JDK 25
- **部署**：Docker + docker-compose

### D. 联系方式
- 项目负责人：[待填写]
- 技术负责人：[待填写]
- 问题反馈：GitHub Issues
