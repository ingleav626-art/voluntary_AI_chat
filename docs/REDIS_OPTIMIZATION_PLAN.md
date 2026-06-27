# Redis 性能优化计划书

> 日期：2026-06-27
> 状态：✅ 全部完成（410 测试通过）
> 范围：server 模块（云端包 + 测试包）
> 目标：充分发挥 Redis 价值，提升软件性能与用户体验

---

## 一、现状分析

### 1.1 Redis 当前使用情况

| 用途 | Key 模式 | TTL | 说明 |
|------|---------|-----|------|
| 短信验证码 | `sms:code:{phone}` | 5分钟 | 唯一用途，大材小用 |

### 1.2 性能瓶颈分析

| 场景 | 当前实现 | SQL 查询数 | 耗时 | 问题 |
|------|---------|-----------|------|------|
| 首页会话列表 | 循环查 message 表 | 22+ 条 | ~200ms | N+1 查询，无缓存 |
| 好友列表 | 查 friend 表 + user 表 | 2 条 | ~50ms | online 硬编码 false |
| 群消息广播 | 查 group_member 表 | 1 条/群 | ~20ms | 大群性能差 |
| 未读数 | JOIN message + message_read | 2 条/会话 | ~100ms | 每次查库 |
| 消息转发 | WebSocket 直发 | 0 条 | ~5ms | 离线丢消息 |
| AI 记忆检索 | 查 ai_memory 表 | 1 条 | ~50ms | 无缓存 |

### 1.3 数据量预估

| 指标 | 估值 |
|------|------|
| 单用户会话数 | 10-50 |
| 单群成员数 | 5-200 |
| 日均消息量 | 1000-10000 |
| 同时在线用户 | 50-500 |

---

## 二、优化目标

### 2.1 性能目标

| 指标 | 优化前 | 优化后 | 提升幅度 |
|------|--------|--------|---------|
| 首页会话列表加载 | 200ms | 10ms | **20x** |
| 好友列表加载 | 50ms | 5ms | **10x** |
| 未读数查询 | 100ms | 1ms | **100x** |
| 群消息广播 | 20ms | 1ms | **20x** |
| AI 记忆检索 | 50ms | 2ms | **25x** |

### 2.2 功能目标

| 功能 | 当前状态 | 目标状态 |
|------|---------|---------|
| 在线状态 | 硬编码 false | 实时显示 |
| 离线消息 | 丢失 | 自动补发 |
| 分布式限流 | 单机内存 | 多实例共享 |

---

## 三、技术方案

### 3.1 会话缓存（P0 - 最高优先级）

**问题**：`ConversationService.getConversations()` 每次加载首页要执行 22+ 条 SQL。

**方案**：

```
Key 设计：
  conv:last_msg:{sessionId}        → JSON {content, type, time, senderId}  TTL: 1天
  conv:unread:{userId}:{sessionId} → 数字                                       TTL: 1天
  conv:list:{userId}               → ZSet {sessionId: lastMsgTimestamp}       TTL: 1天
```

**缓存策略**：
- 读：先查 Redis，命中则返回；未命中查 MySQL 并回填
- 写：消息发送时更新 `conv:last_msg` + `conv:list` + `conv:unread`（INCR）
- 失效：已读时 `conv:unread` 归零；删除好友时删除相关 Key

**新增文件**：
- `server/src/main/java/.../service/ConversationCacheService.java`

**改动文件**：
- `ConversationService.java` — 接入缓存
- `MessageService.java` — 消息发送时更新缓存
- `MessageService.markRead()` — 已读时清零未读

**预估工作量**：中等

---

### 3.2 用户在线状态（P0 - 最高优先级）

**问题**：`FriendService.toFriendResponse()` 中 `online` 硬编码为 `false`。

**方案**：

```
Key 设计：
  online:user:{userId}  → 1  TTL: 30秒
```

**心跳机制**：
- WebSocket 连接建立时：`SET online:user:{userId} 1 EX 30`
- WebSocket 每 15 秒心跳：`EXPIRE online:user:{userId} 30`
- WebSocket 断开时：`DEL online:user:{userId}`

**新增文件**：
- `server/src/main/java/.../service/OnlineStatusService.java`

**改动文件**：
- `ChatWebSocketHandler.java` — 连接/断开时维护状态
- `FriendService.java` — 查好友列表时批量查在线状态
- `GroupService.java` — 查群成员时批量查在线状态

**预估工作量**：小

---

### 3.3 离线消息队列（P1 - 高优先级）

**问题**：WebSocket 断开期间消息丢失，断线重连无法补发。

**方案**：

```
Key 设计：
  offline:msg:{userId}  → List [JSON消息1, JSON消息2, ...]  无 TTL（上线后消费）
```

**流程**：
1. 消息转发时：检查 `online:user:{userId}`
2. 在线 → WebSocket 直发
3. 离线 → `LPUSH offline:msg:{userId} {消息JSON}`
4. 用户上线/重连 → `RPOP` 循环消费，通过 WebSocket 补发

**改动文件**：
- `ChatWebSocketHandler.java` — 消息转发逻辑 + 重连补发
- `MessageService.java` — 新增 `getOfflineMessagesFromCache()`

**预估工作量**：中等

---

### 3.4 未读计数（P1 - 高优先级）

**问题**：`MessageService.getUnreadCount()` 每次查两张表 COUNT。

**方案**：

```
Key 设计：
  conv:unread:{userId}:{sessionId} → 数字  TTL: 7天
```

**操作**：
- 消息到达：`INCR conv:unread:{userId}:{sessionId}`
- 标记已读：`SET conv:unread:{userId}:{sessionId} 0`
- 删除会话：`DEL conv:unread:{userId}:{sessionId}`

**改动文件**：
- `MessageService.java` — `getUnreadCount()` 改为读 Redis
- `MessageService.markRead()` — 清零未读
- `ChatWebSocketHandler.java` — 消息转发时 INCR

**预估工作量**：小

---

### 3.5 群成员缓存（P2 - 中优先级）

**问题**：群消息广播每次查 `group_member` 表。

**方案**：

```
Key 设计：
  group:members:{groupId}  → Set [userId1, userId2, ...]  无 TTL（成员变更时更新）
  group:member_count:{groupId} → 数字                      无 TTL
```

**缓存策略**：
- 读：`SMEMBERS group:members:{groupId}`
- 写：成员加入 `SADD`，成员退出 `SREM`
- 失效：群解散时 `DEL`

**新增文件**：
- `server/src/main/java/.../service/GroupCacheService.java`

**改动文件**：
- `GroupService.java` — 成员变更时更新缓存
- `ChatWebSocketHandler.java` — 广播时读缓存

**预估工作量**：小

---

### 3.6 好友关系缓存（P3 - 低优先级）

**问题**：`FriendService.isFriend()` 每次查库。

**方案**：

```
Key 设计：
  friend:list:{userId}  → Set [friendId1, friendId2, ...]  TTL: 1天
```

**改动文件**：
- `FriendService.java` — `isFriend()` 改为 `SISMEMBER`，增删好友时更新 Set

**预估工作量**：极小

---

### 3.7 分布式限流（P3 - 低优先级）

**问题**：`RateLimitingFilter` 用本地内存令牌桶，多实例不准。

**方案**：

```
Key 设计：
  rate_limit:{endpoint}:{ip}  → 计数器  TTL: 1秒（滑动窗口）
```

**改动文件**：
- `RateLimitingFilter.java` — 令牌桶改为 Redis 计数

**预估工作量**：小

---

### 3.8 AI 记忆缓存（P3 - 低优先级）

**问题**：`AiMemoryService.searchRelevantMemories()` 每次查库。

**方案**：

```
Key 设计：
  ai_memory:{aiId}:{userId}  → List [summary1, summary2, ...]  TTL: 30分钟
```

**改动文件**：
- `AiMemoryService.java` — 检索时先查缓存，摘要生成后更新缓存

**预估工作量**：小

---

## 四、实施计划

### 4.1 分阶段实施

```
阶段一（P0）：会话缓存 + 在线状态
  ├── 新建 ConversationCacheService
  ├── 新建 OnlineStatusService
  ├── 改造 ConversationService / FriendService
  ├── 改造 ChatWebSocketHandler（心跳）
  └── 编写单元测试 + 集成测试

阶段二（P1）：离线消息 + 未读计数
  ├── 改造 ChatWebSocketHandler（离线队列）
  ├── 改造 MessageService（未读计数）
  ├── 改造 MessageService.markRead()
  └── 编写测试

阶段三（P2）：群成员缓存
  ├── 新建 GroupCacheService
  ├── 改造 GroupService
  ├── 改造 ChatWebSocketHandler（广播）
  └── 编写测试

阶段四（P3）：好友关系 + 限流 + AI记忆
  ├── 改造 FriendService
  ├── 改造 RateLimitingFilter
  ├── 改造 AiMemoryService
  └── 编写测试
```

### 4.2 Redis 降级策略

所有缓存层必须支持 **Redis 不可用时自动降级**：

```java
public ConversationResponse getConversation(...) {
    try {
        // 1. 先查 Redis
        ConversationResponse cached = conversationCacheService.get(userId, sessionId);
        if (cached != null) return cached;
    } catch (Exception e) {
        log.warn("Redis 不可用，降级为直接查库", e);
    }

    // 2. 查 MySQL
    ConversationResponse result = queryFromDatabase(...);

    // 3. 回填 Redis（忽略异常）
    try {
        conversationCacheService.set(userId, sessionId, result);
    } catch (Exception e) {
        log.warn("Redis 回填失败，忽略", e);
    }

    return result;
}
```

---

## 五、测试计划

### 5.1 单元测试

| 测试类 | 覆盖内容 |
|--------|---------|
| `ConversationCacheServiceTest` | 缓存命中/未命中/过期/降级 |
| `OnlineStatusServiceTest` | 上线/下线/心跳超时/批量查询 |
| `GroupCacheServiceTest` | 成员加入/退出/解散/降级 |
| `MessageServiceCacheTest` | 未读数 INCR/归零/降级 |
| `OfflineMessageTest` | 离线存储/上线补发/消费完成 |

### 5.2 集成测试

| 场景 | 验证点 |
|------|--------|
| 用户登录→首页加载 | 会话缓存命中率 > 90% |
| 好友列表 | online 状态正确显示 |
| 发消息→对方离线 | 离线队列存储正确 |
| 对方上线 | 离线消息补发成功 |
| 群消息广播 | 群成员缓存命中 |
| Redis 断开 | 所有功能降级为查库，不崩溃 |

### 5.3 性能测试

| 指标 | 测试方法 | 目标 |
|------|---------|------|
| 首页加载 | 100 并发用户 | P99 < 50ms |
| 群广播 | 100 人群发消息 | P99 < 10ms |
| 未读数 | 50 会话同时查询 | P99 < 5ms |
| Redis 降级 | kill Redis 后压测 | 功能不中断 |

---

## 六、配置变更

### 6.1 application-cloud.yml 新增

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 3000
      connect-timeout: 3000
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 2

# Redis 缓存配置
app:
  cache:
    enabled: true
    conversation:
      ttl: 86400          # 会话缓存 1 天
    online-status:
      ttl: 30             # 在线状态 30 秒
    unread:
      ttl: 604800         # 未读数 7 天
    group-members:
      ttl: 0              # 永不过期（成员变更时更新）
    ai-memory:
      ttl: 1800           # AI 记忆 30 分钟
    offline-message:
      max-size: 1000      # 离线队列最大长度
```

### 6.2 .env 新增

```env
# Redis 缓存开关（true=启用，false=纯查库）
APP_CACHE_ENABLED=true
```

---

## 七、风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| Redis 宕机 | 缓存失效 | 自动降级为查库，功能不中断 |
| 缓存不一致 | 用户看到旧数据 | 消息发送/已读/删好友时主动更新缓存 |
| Redis 内存溢出 | 缓存被逐出 | 设置合理 TTL，监控 used_memory |
| 离线队列堆积 | Redis 内存增长 | 限制 max-size=1000，超限丢弃旧消息 |
| 心跳延迟 | 在线状态不准确 | TTL=30s，心跳=15s，容差 15 秒 |

---

## 八、验收标准

- [ ] 首页会话列表加载 P99 < 50ms
- [ ] 好友列表显示在线/离线状态
- [ ] 离线消息上线后自动补发
- [ ] 未读数实时更新
- [ ] 群消息广播 P99 < 10ms
- [ ] Redis 断开后所有功能正常（降级查库）
- [ ] 单元测试覆盖率 ≥ 80%
- [ ] 集成测试全部通过
- [ ] 903+ 原有测试 0 失败

---

## 九、交付物清单

| 文件 | 类型 | 说明 |
|------|------|------|
| `ConversationCacheService.java` | 新增 | 会话缓存服务 |
| `OnlineStatusService.java` | 新增 | 在线状态服务 |
| `GroupCacheService.java` | 新增 | 群成员缓存服务 |
| `ConversationService.java` | 修改 | 接入会话缓存 |
| `FriendService.java` | 修改 | 接入在线状态 + 好友关系缓存 |
| `MessageService.java` | 修改 | 接入未读数缓存 |
| `GroupService.java` | 修改 | 接入群成员缓存 |
| `ChatWebSocketHandler.java` | 修改 | 心跳 + 离线队列 + 缓存更新 |
| `RateLimitingFilter.java` | 修改 | Redis 限流 |
| `AiMemoryService.java` | 修改 | AI 记忆缓存 |
| `application-cloud.yml` | 修改 | Redis 缓存配置 |
| `*Test.java` | 新增 | 单元测试 + 集成测试 |
