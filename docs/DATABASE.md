# 数据库设计文档

> MySQL / H2 数据库表结构设计（基于实际 schema.sql）

---

## 数据库规范

### 命名规范
- 表名：小写下划线分隔，如 `user`、`chat_group`
- 字段名：小写下划线分隔，如 `user_id`、`create_time`
- 主键：`id`（BIGINT，雪花算法生成）
- 时间字段：`create_time`、`update_time`
- 逻辑删除：`is_deleted`（0-否，1-是）

### 数据类型规范
- 主键：`BIGINT`（雪花算法生成）
- 时间：`DATETIME`
- 状态：`TINYINT`
- 文本：`VARCHAR`（按实际长度设置）
- 长文本：`TEXT`
- JSON：`JSON`（MySQL 5.7+）

### 数据库迁移

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

---

## 一、用户相关表

### 1.1 用户表（user）

```sql
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL COMMENT '用户ID（雪花算法）',
    `phone` VARCHAR(20) NOT NULL COMMENT '手机号',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password_hash` VARCHAR(100) NOT NULL COMMENT '密码哈希',
    `salt` VARCHAR(50) NOT NULL COMMENT '密码盐值',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `bio` VARCHAR(500) DEFAULT NULL COMMENT '个性签名',
    `gender` TINYINT DEFAULT 0 COMMENT '性别：0-未知，1-男，2-女',
    `age` INT DEFAULT NULL COMMENT '年龄',
    `birthday` DATE DEFAULT NULL COMMENT '生日',
    `detail_bio` TEXT DEFAULT NULL COMMENT '个人详细说明',
    `status` TINYINT DEFAULT 0 COMMENT '状态：0-正常，1-禁用',
    `last_login_time` DATETIME DEFAULT NULL COMMENT '最后登录时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

### 1.2 用户Token表（user_token）

```sql
CREATE TABLE IF NOT EXISTS `user_token` (
    `id` BIGINT NOT NULL COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `access_token` VARCHAR(500) NOT NULL COMMENT '访问Token',
    `refresh_token` VARCHAR(500) NOT NULL COMMENT '刷新Token',
    `access_expires` DATETIME NOT NULL COMMENT '访问Token过期时间',
    `refresh_expires` DATETIME NOT NULL COMMENT '刷新Token过期时间',
    `device_info` VARCHAR(200) DEFAULT NULL COMMENT '设备信息',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_access_token` (`access_token`(100)),
    KEY `idx_refresh_token` (`refresh_token`(100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户Token表';
```

---

## 二、好友相关表

### 2.1 好友关系表（friend）

```sql
CREATE TABLE IF NOT EXISTS `friend` (
    `id` BIGINT NOT NULL COMMENT 'ID（雪花算法）',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `friend_id` BIGINT NOT NULL COMMENT '好友ID',
    `remark` VARCHAR(50) DEFAULT NULL COMMENT '备注名',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_friend` (`user_id`, `friend_id`),
    KEY `idx_friend_id` (`friend_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友关系表';
```

### 2.2 好友申请表（friend_apply）

```sql
CREATE TABLE IF NOT EXISTS `friend_apply` (
    `id` BIGINT NOT NULL COMMENT '申请ID（雪花算法）',
    `user_id` BIGINT NOT NULL COMMENT '申请人ID',
    `target_user_id` BIGINT NOT NULL COMMENT '目标用户ID',
    `message` VARCHAR(200) DEFAULT NULL COMMENT '申请留言',
    `status` TINYINT DEFAULT 0 COMMENT '状态：0-待处理，1-已同意，2-已拒绝',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_target_user_id` (`target_user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友申请表';
```

---

## 三、群组相关表

### 3.1 群组表（chat_group）

```sql
CREATE TABLE IF NOT EXISTS `chat_group` (
    `id` BIGINT NOT NULL COMMENT '群组ID（雪花算法）',
    `name` VARCHAR(50) NOT NULL COMMENT '群组名称',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT '群头像URL',
    `announcement` VARCHAR(500) DEFAULT NULL COMMENT '群公告',
    `announcement_pinned` TINYINT DEFAULT 0 COMMENT '公告是否置顶：0-否，1-是',
    `owner_id` BIGINT NOT NULL COMMENT '群主ID',
    `max_member_count` INT DEFAULT 200 COMMENT '最大成员数',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    PRIMARY KEY (`id`),
    KEY `idx_owner_id` (`owner_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群组表';
```

### 3.2 群成员表（group_member）

```sql
CREATE TABLE IF NOT EXISTS `group_member` (
    `id` BIGINT NOT NULL COMMENT 'ID（雪花算法）',
    `group_id` BIGINT NOT NULL COMMENT '群组ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role` TINYINT DEFAULT 0 COMMENT '角色：0-普通成员，1-管理员，2-群主',
    `nickname` VARCHAR(50) DEFAULT NULL COMMENT '群昵称',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_user` (`group_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群成员表';
```

---

## 四、消息相关表

### 4.1 消息表（message）

```sql
CREATE TABLE IF NOT EXISTS `message` (
    `id` BIGINT NOT NULL COMMENT '消息ID（雪花算法）',
    `session_id` VARCHAR(100) NOT NULL COMMENT '会话ID',
    `sender_id` BIGINT NOT NULL COMMENT '发送者ID',
    `sender_type` TINYINT DEFAULT 0 COMMENT '发送者类型：0-用户，1-AI',
    `target_id` BIGINT NOT NULL COMMENT '目标用户/群组ID',
    `target_type` TINYINT DEFAULT 0 COMMENT '目标类型：0-用户，1-群组',
    `type` TINYINT DEFAULT 0 COMMENT '消息类型：0-文本，1-图片，2-AI，3-撤回，4-转发',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `extra` JSON DEFAULT NULL COMMENT '额外信息（图片尺寸、转发消息等）',
    `recall_time` DATETIME DEFAULT NULL COMMENT '撤回时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_sender_id` (`sender_id`),
    KEY `idx_target_id` (`target_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';
```

**消息类型（type）说明**：

| 值 | 类型 | 说明 |
|----|------|------|
| 0 | 文本 | 普通文本消息 |
| 1 | 图片 | 图片消息（extra 中存储图片 URL、尺寸等） |
| 2 | AI | AI 回复消息 |
| 3 | 撤回 | 消息已撤回（原消息内容替换为撤回提示） |
| 4 | 转发 | 转发消息（extra 中存储原始消息信息） |

**会话 ID 生成规则**：

| 场景 | 格式 | 示例 |
|------|------|------|
| 单聊 | `p_{min}_{max}` | `p_1001_1002`（较小 ID 在前） |
| 群聊 | `g_{groupId}` | `g_2001` |

### 4.2 消息已读表（message_read）

```sql
CREATE TABLE IF NOT EXISTS `message_read` (
    `id` BIGINT NOT NULL COMMENT 'ID',
    `message_id` BIGINT NOT NULL COMMENT '消息ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `session_id` VARCHAR(100) NOT NULL COMMENT '会话ID',
    `read_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '阅读时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_user` (`message_id`, `user_id`),
    KEY `idx_user_session` (`user_id`, `session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息已读表';
```

---

## 五、AI 相关表

### 5.1 AI 角色表（ai_profile）

```sql
CREATE TABLE IF NOT EXISTS `ai_profile` (
    `id` BIGINT NOT NULL COMMENT 'AI ID（雪花算法）',
    `user_id` BIGINT NOT NULL COMMENT '用户ID（所有者）',
    `name` VARCHAR(50) NOT NULL COMMENT 'AI 名称',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT 'AI 头像',
    `persona` VARCHAR(2000) DEFAULT NULL COMMENT 'AI 人设/性格描述',
    `system_prompt` TEXT DEFAULT NULL COMMENT '系统提示词',
    `model_provider` VARCHAR(20) NOT NULL COMMENT '模型提供商：openai, deepseek, qwen, zhipu, custom',
    `model` VARCHAR(50) NOT NULL COMMENT '模型名称',
    `api_key_enc` VARCHAR(500) NOT NULL COMMENT 'API Key（AES-256-GCM 加密）',
    `is_group` TINYINT DEFAULT 0 COMMENT '是否可用于群聊：0-否，1-是',
    `temperature` DECIMAL(3,2) DEFAULT 0.70 COMMENT 'AI 回复创造性参数（0.0-2.0）',
    `max_tokens` INT DEFAULT 2048 COMMENT 'AI 回复最大长度',
    `status` TINYINT DEFAULT 0 COMMENT '状态：0-正常，1-禁用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_model_provider` (`model_provider`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 角色表';
```

**支持的模型提供商（model_provider）**：

| 值 | 提供商 | 说明 |
|----|--------|------|
| `openai` | OpenAI | GPT 系列 |
| `deepseek` | DeepSeek | DeepSeek 系列 |
| `qwen` | 通义千问 | 阿里云 Qwen 系列 |
| `zhipu` | 智谱 | GLM 系列 |
| `custom` | 自定义 | 任何 OpenAI 兼容接口 |

### 5.2 AI 群配置表（ai_group_config）

```sql
CREATE TABLE IF NOT EXISTS `ai_group_config` (
    `id` BIGINT NOT NULL COMMENT '配置ID（雪花算法）',
    `group_id` BIGINT NOT NULL COMMENT '群组ID',
    `ai_id` BIGINT NOT NULL COMMENT 'AI 角色ID',
    `trigger_keywords` VARCHAR(200) DEFAULT NULL COMMENT '触发关键词（逗号分隔）',
    `trigger_probability` DECIMAL(3,2) DEFAULT 0.00 COMMENT '触发概率（0.0-1.0）',
    `is_enabled` TINYINT DEFAULT 1 COMMENT '是否启用：0-否，1-是',
    `cooldown_seconds` INT DEFAULT 30 COMMENT 'AI 回复冷却时间（秒）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_ai` (`group_id`, `ai_id`),
    KEY `idx_ai_id` (`ai_id`),
    KEY `idx_is_enabled` (`is_enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 群配置表';
```

**AI 群聊触发机制**：

| 触发方式 | 字段 | 说明 |
|----------|------|------|
| @提及 | - | 用户在群消息中 @AI 角色，直接触发回复 |
| 关键词触发 | `trigger_keywords` | 消息包含配置的关键词时触发 |
| 概率触发 | `trigger_probability` | 每条群消息按配置概率随机触发 |
| 冷却时间 | `cooldown_seconds` | AI 回复后进入冷却期，避免频繁触发 |

### 5.3 AI 记忆表（ai_memory）

```sql
CREATE TABLE IF NOT EXISTS `ai_memory` (
    `id` BIGINT NOT NULL COMMENT '记忆ID（雪花算法）',
    `ai_id` BIGINT NOT NULL COMMENT 'AI 角色ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `session_id` VARCHAR(100) DEFAULT NULL COMMENT '会话ID',
    `summary` VARCHAR(500) NOT NULL COMMENT '记忆摘要',
    `keywords` VARCHAR(200) DEFAULT NULL COMMENT '关键词（逗号分隔）',
    `importance` DECIMAL(3,2) DEFAULT 0.50 COMMENT '记忆重要度评分（0.0-1.0）',
    `vector_id` VARCHAR(100) DEFAULT NULL COMMENT '向量ID（Milvus/Qdrant，预留）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    PRIMARY KEY (`id`),
    KEY `idx_ai_user` (`ai_id`, `user_id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_vector_id` (`vector_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 记忆表';
```

> `vector_id` 字段为预留字段，用于后续接入向量数据库（Milvus/Qdrant）。当前记忆检索通过 MySQL `keywords` 字段匹配实现。

---

## 六、Redis 缓存设计（云端模式）

以下 Redis 缓存仅在云端部署时使用。本地/测试模式自动降级为内存实现。

### 6.1 用户在线状态

```
Key:   online:{userId}
Type:  String
Value: websocket_session_id
TTL:   5 分钟（心跳续期）
```

### 6.2 用户 Token 缓存

```
Key:   token:{accessToken}
Type:  String
Value: userId
TTL:   2 小时（与 access token 有效期一致）
```

### 6.3 消息未读数

```
Key:   unread:{userId}:{sessionId}
Type:  String
Value: 未读消息数量
TTL:   永久（消息已读后删除或更新）
```

### 6.4 群成员列表缓存

```
Key:   group:members:{groupId}
Type:  Set
Value: userId 集合
TTL:   10 分钟
```

### 6.5 短信验证码

```
Key:   sms:code:{phone}
Type:  String
Value: 验证码（6 位数字）
TTL:   5 分钟
```

> 无 Redis 时自动降级为 `MemorySmsCodeStorage`（内存实现），通过 `SmsCodeStorage` 接口切换。

---

## 七、ER 关系概览

```
user ──1:N──▶ user_token
user ──1:N──▶ friend (user_id)
user ──1:N──▶ friend (friend_id)
user ──1:N──▶ friend_apply (user_id)
user ──1:N──▶ friend_apply (target_user_id)
user ──1:N──▶ chat_group (owner_id)
user ──1:N──▶ group_member
user ──1:N──▶ message (sender_id)
user ──1:N──▶ ai_profile (user_id)
user ──1:N──▶ ai_memory (user_id)

chat_group ──1:N──▶ group_member
chat_group ──1:N──▶ ai_group_config

ai_profile ──1:N──▶ ai_group_config
ai_profile ──1:N──▶ ai_memory

message ──1:N──▶ message_read
```
