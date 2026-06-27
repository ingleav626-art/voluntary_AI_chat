# AI 模块拆分实施计划

> 目标：将 server 中的 AI 模块拆分为独立 Maven 模块 `ai-core`，供 server 和未来 client 共享
> 日期：2026-06-25
> 状态：**已完成**（2026-06-27）
>
> ai-core 模块已成功从 server 模块提取。包名保持 `com.voluntary.chat.server`，server 模块导入无需修改。
> 当前 ai-core 包含 38 个源文件：实体、Mapper、LLM 客户端、安全组件、WebSocket 处理器、AES 加密工具等。

---

## 一、当前状态

### 1.1 已完成

- `ai-core/` 目录已创建，包含 pom.xml
- 父 `pom.xml` 已注册 `ai-core` 模块和依赖管理
- ai-core 内已有 10 个 Java 文件 + 4 个测试文件
- ai-core 单独编译和测试通过（36 个测试全通过）

### 1.2 当前问题

**ai-core 用的包名是 `com.voluntary.chat.ai`，而 server 用的是 `com.voluntary.chat.server.xxx`。**

这导致：
- server 的 `AiService.java` 里 `import com.voluntary.chat.server.entity.AiProfile` 找不到类（AiProfile 搬到 ai-core 后包名变了）
- 如果要适配，需要改 server 里**所有 AI 相关文件的 import**（150+ 处）
- 改不完就 150 个编译错误

### 1.3 server 侧未还原的格式变动

| 文件 | 变动 | 性质 |
|------|------|------|
| `AiConfig.java` | 缩进从 4 空格变 2 空格 | **无意义格式变动，应还原** |
| `AiGroupConfigService.java` | 一行语句合并 | **无意义格式变动，应还原** |

---

## 二、失败原因分析

### 上次为什么 150 个报错

```
ai-core 包名: com.voluntary.chat.ai.entity.AiProfile
server 期望:   com.voluntary.chat.server.entity.AiProfile

→ server 所有 import com.voluntary.chat.server.entity.AiProfile 全部断裂
→ AiService、AiChatService、AiMemoryService、AiGroupConfigService、AiController 全报错
→ 连锁反应：依赖这些 Service 的测试也全报错
```

### 根因

**包名不一致是致命错误**。Java 的 import 是全限定名，包名变了就意味着所有引用都要改。

---

## 三、正确方案：包名保持一致

### 3.1 核心原则

**ai-core 的包名必须与 server 完全一致，保持 `com.voluntary.chat.server.xxx`。**

```
ai-core/src/main/java/com/voluntary/chat/server/
├── config/
│   └── AiConfig.java              ← 包名: com.voluntary.chat.server.config
├── entity/
│   ├── AiProfile.java             ← 包名: com.voluntary.chat.server.entity
│   ├── AiGroupConfig.java
│   └── AiMemory.java
├── mapper/
│   ├── AiProfileMapper.java       ← 包名: com.voluntary.chat.server.mapper
│   ├── AiGroupConfigMapper.java
│   └── AiMemoryMapper.java
├── client/
│   ├── OpenAiClient.java          ← 包名: com.voluntary.chat.server.client
│   ├── EmbeddingClient.java
│   └── VectorStoreClient.java
└── util/
    └── AesKeyUtil.java            ← 包名: com.voluntary.chat.server.util
```

### 3.2 为什么包名可以一样

Maven 多模块中，**不同 JAR 可以有相同的包名**。这不是冲突：

- `ai-core.jar` 里有 `com.voluntary.chat.server.entity.AiProfile`
- `server.jar` 里**不再有**这个类（删掉了）
- server 依赖 ai-core 后，classpath 里 `com.voluntary.chat.server.entity.AiProfile` 由 ai-core 提供
- `@ComponentScan("com.voluntary.chat")` 照常扫描，无需任何改动

### 3.3 server import 一行都不用改

```java
// server 的 AiService.java（不用改任何一行）
import com.voluntary.chat.server.entity.AiProfile;       // ← ai-core 提供
import com.voluntary.chat.server.mapper.AiProfileMapper;  // ← ai-core 提供
import com.voluntary.chat.server.config.AiConfig;         // ← ai-core 提供
import com.voluntary.chat.server.util.AesKeyUtil;         // ← ai-core 提供
```

---

## 四、详细执行步骤

### 步骤 0：还原 server 格式变动（无风险）

```bash
git checkout -- server/src/main/java/com/voluntary/chat/server/config/AiConfig.java
git checkout -- server/src/main/java/com/voluntary/chat/server/service/AiGroupConfigService.java
```

还原后 server 侧代码回到干净状态。

### 步骤 1：重建 ai-core 包名（无风险）

删除 ai-core 现有的 `com/voluntary/chat/ai/` 目录，改为 `com/voluntary/chat/server/`。

需要改包名的文件（10 个源文件 + 4 个测试文件）：

| 文件 | 当前包名 | 目标包名 |
|------|---------|---------|
| `AiConfig.java` | `com.voluntary.chat.ai.config` | `com.voluntary.chat.server.config` |
| `AiProfile.java` | `com.voluntary.chat.ai.entity` | `com.voluntary.chat.server.entity` |
| `AiGroupConfig.java` | `com.voluntary.chat.ai.entity` | `com.voluntary.chat.server.entity` |
| `AiMemory.java` | `com.voluntary.chat.ai.entity` | `com.voluntary.chat.server.entity` |
| `AiProfileMapper.java` | `com.voluntary.chat.ai.mapper` | `com.voluntary.chat.server.mapper` |
| `AiGroupConfigMapper.java` | `com.voluntary.chat.ai.mapper` | `com.voluntary.chat.server.mapper` |
| `AiMemoryMapper.java` | `com.voluntary.chat.ai.mapper` | `com.voluntary.chat.server.mapper` |
| `OpenAiClient.java` | `com.voluntary.chat.ai.client` | `com.voluntary.chat.server.client` |
| `EmbeddingClient.java` | `com.voluntary.chat.ai.client` | `com.voluntary.chat.server.client` |
| `VectorStoreClient.java` | `com.voluntary.chat.ai.client` | `com.voluntary.chat.server.client` |
| `AesKeyUtil.java` | `com.voluntary.chat.ai.util` | `com.voluntary.chat.server.util` |
| `AesKeyUtilTest.java` | `com.voluntary.chat.ai.util` | `com.voluntary.chat.server.util` |
| `OpenAiClientTest.java` | `com.voluntary.chat.ai.client` | `com.voluntary.chat.server.client` |
| `AiConfigTest.java` | `com.voluntary.chat.ai.config` | `com.voluntary.chat.server.config` |
| `AiProfileTest.java` | `com.voluntary.chat.ai.entity` | `com.voluntary.chat.server.entity` |

同时修改每个文件内部的 `import com.voluntary.chat.ai.xxx` → `com.voluntary.chat.server.xxx`。

### 步骤 2：验证 ai-core 独立编译（无风险）

```bash
mvn compile -pl ai-core -Dcheckstyle.skip=true
mvn test -pl ai-core -Dcheckstyle.skip=true
```

预期：编译通过，36 个测试通过。

### 步骤 3：server 添加 ai-core 依赖（低风险）

在 `server/pom.xml` 的 `<dependencies>` 中添加：

```xml
<dependency>
    <groupId>com.voluntary</groupId>
    <artifactId>voluntary-ai-chat-ai-core</artifactId>
</dependency>
```

### 步骤 4：删除 server 重复文件（需用户同意）

以下 10 个文件已搬到 ai-core，需从 server 删除：

| # | 文件路径 | 说明 |
|---|---------|------|
| 1 | `server/.../config/AiConfig.java` | AI 配置 |
| 2 | `server/.../entity/AiProfile.java` | AI 角色实体 |
| 3 | `server/.../entity/AiGroupConfig.java` | AI 群配置实体 |
| 4 | `server/.../entity/AiMemory.java` | AI 记忆实体 |
| 5 | `server/.../mapper/AiProfileMapper.java` | Mapper |
| 6 | `server/.../mapper/AiGroupConfigMapper.java` | Mapper |
| 7 | `server/.../mapper/AiMemoryMapper.java` | Mapper |
| 8 | `server/.../client/OpenAiClient.java` | OpenAI 客户端 |
| 9 | `server/.../client/EmbeddingClient.java` | Embedding 客户端 |
| 10 | `server/.../client/VectorStoreClient.java` | 向量存储客户端 |
| 11 | `server/.../util/AesKeyUtil.java` | AES 加密工具 |

> **注意**：server 的测试文件 `AesKeyUtilTest.java` 和 `OpenAiClientTest.java` 也要删除或迁移到 ai-core（已在 ai-core 有副本）。

### 步骤 5：编译验证（无风险）

```bash
mvn compile -pl ai-core,server -Dcheckstyle.skip=true
```

预期：编译通过，因为包名一致，server 的 import 全部由 ai-core 提供。

### 步骤 6：全量测试（无风险）

```bash
mvn test -Dcheckstyle.skip=true
```

预期：所有现有测试通过。ai-core 的 36 个新测试也通过。

---

## 五、留在 server 的 AI 文件（不迁移）

以下文件依赖 server 专有类，**不迁移**到 ai-core：

| 文件 | 依赖的 server 专有类 | 原因 |
|------|---------------------|------|
| `AiService.java` | `AiProfileMapper`, `AiConfig`, DTO | 角色管理，依赖 server 的 DTO |
| `AiChatService.java` | `MessageMapper`, `ChatWebSocketHandler` | 对话服务，依赖真人消息模块 |
| `AiMemoryService.java` | `MessageMapper`, `EmbeddingClient`, `VectorStoreClient` | 记忆服务 |
| `AiGroupConfigService.java` | `StringRedisTemplate`, `AiGroupConfigMapper` | 群配置 |
| `AiController.java` | 上述所有 Service | 控制器 |
| `dto/request/*` | - | 请求 DTO |
| `dto/response/*` | - | 响应 DTO |

这些文件留在 server，通过 import 引用 ai-core 提供的基础类。

---

## 六、风险与回滚

### 6.1 风险点

| 风险 | 概率 | 影响 | 应对 |
|------|------|------|------|
| MyBatis Mapper 扫描不到 | 低 | Mapper 注入失败 | `@MapperScan` 或 `@Mapper` 注解仍在，包名没变 |
| Bean 重复注册 | 低 | 启动报错 | 确保步骤 4 删除干净 |
| 测试 classpath 缺失 | 低 | 测试编译失败 | ai-core 的 scope 是默认 compile |

### 6.2 回滚方案

如果出问题，一条命令回滚：

```bash
git checkout -- server/ pom.xml
# ai-core 目录保留，不影响现有代码
```

---

## 七、迁移后的依赖关系

```
┌──────────┐
│  common  │  (零依赖)
└────┬─────┘
     │
┌────▼──────┐
│ ai-core   │  (依赖 common, spring-boot, mybatis-plus, jackson)
└────┬──────┘
     │
┌────▼──────┐
│  server   │  (依赖 common + ai-core，保留真人模块 + AI Service/Controller/DTO)
└───────────┘
```

### 7.1 迁移后的文件分布

```
ai-core (共享基础)
├── config/AiConfig.java
├── entity/AiProfile.java, AiGroupConfig.java, AiMemory.java
├── mapper/AiProfileMapper.java, AiGroupConfigMapper.java, AiMemoryMapper.java
├── client/OpenAiClient.java, EmbeddingClient.java, VectorStoreClient.java
└── util/AesKeyUtil.java

server (保留)
├── service/AiService.java, AiChatService.java, AiMemoryService.java, AiGroupConfigService.java
├── controller/AiController.java
├── dto/request/CreateAiProfileRequest.java, UpdateAiProfileRequest.java, AiGroupConfigRequest.java, AiChatRequest.java
├── dto/response/AiProfileResponse.java, AiMemoryResponse.java, AiGroupConfigResponse.java, AiChatResponse.java
└── (所有真人聊天模块)
```

---

## 八、后续阶段（本次不做）

| 阶段 | 内容 | 依赖本次 |
|------|------|---------|
| 阶段二 | client 依赖 ai-core（不依赖 server） | ✅ |
| 阶段三 | DualWebSocketManager 集成 | ✅ |
| 阶段四 | 云端/客户包打包配置 | ✅ |

本次只做**阶段一：ai-core 拆分**，确保不破坏现有功能。
