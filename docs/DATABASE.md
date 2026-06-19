# 数据库设计文档

> MySQL 数据库表结构设计

---

## 数据库规范

### 命名规范
- 表名：小写下划线分隔，如 `user`、`chat_group`
- 字段名：小写下划线分隔，如 `user_id`、`create_time`
- 主键：`id` 或 `{表名}_id`
- 外键：`{关联表名}_id`
- 时间字段：`create_time`、`update_time`
- 状态字段：`status`、`is_deleted`

### 数据类型规范
- 主键：`BIGINT`（雪花算法生成）
- 时间：`DATETIME`
- 金额：`DECIMAL(10,2)`
- 状态：`TINYINT`（0-正常，1-禁用，2-删除）
- 文本：`VARCHAR`（按实际长度设置）
- 长文本：`TEXT` 或 `LONGTEXT`

---

## 一、用户相关表

### 1.1 用户表（user）
```sql
CREATE TABLE `user` (
    `id` BIGINT NOT NULL COMMENT '用户ID（雪花算法）',
    `phone` VARCHAR(20) NOT NULL COMMENT '手机号',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password_hash` VARCHAR(100) NOT NULL COMMENT '密码哈希',
    `salt` VARCHAR(50) NOT NULL COMMENT '密码盐值',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `bio` VARCHAR(500) DEFAULT NULL COMMENT '个性签名',
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
CREATE TABLE `user_token` (
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

### 2.1 好友关系表（friend_relation）
```sql
CREATE TABLE `friend_relation` (
    `id` BIGINT NOT NULL COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `friend_id` BIGINT NOT NULL COMMENT '好友ID',
    `remark` VARCHAR(50) DEFAULT NULL COMMENT '好友备注',
    `status` TINYINT DEFAULT 0 COMMENT '状态：0-正常，1-删除',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_friend` (`user_id`, `friend_id`),
    KEY `idx_friend_id` (`friend_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友关系表';
```

### 2.2 好友申请表（friend_apply）
```sql
CREATE TABLE `friend_apply` (
    `id` BIGINT NOT NULL COMMENT '申请ID',
    `applicant_id` BIGINT NOT NULL COMMENT '申请人ID',
    `target_id` BIGINT NOT NULL COMMENT '目标用户ID',
    `message` VARCHAR(200) DEFAULT NULL COMMENT '申请消息',
    `status` TINYINT DEFAULT 0 COMMENT '状态：0-待处理，1-已同意，2-已拒绝',
    `handle_time` DATETIME DEFAULT NULL COMMENT '处理时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_applicant_id` (`applicant_id`),
    KEY `idx_target_id` (`target_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友申请表';
```

---

## 三、群组相关表

### 3.1 群组表（chat_group）
```sql
CREATE TABLE `chat_group` (
    `id` BIGINT NOT NULL COMMENT '群组ID',
    `name` VARCHAR(50) NOT NULL COMMENT '群名称',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT '群头像',
    `owner_id` BIGINT NOT NULL COMMENT '群主ID',
    `announcement` TEXT COMMENT '群公告',
    `announcement_pinned` TINYINT DEFAULT 0 COMMENT '公告是否置顶',
    `max_member_count` INT DEFAULT 500 COMMENT '最大成员数',
    `status` TINYINT DEFAULT 0 COMMENT '状态：0-正常，1-解散',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    PRIMARY KEY (`id`),
    KEY `idx_owner_id` (`owner_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群组表';
```

### 3.2 群成员表（group_member）
```sql
CREATE TABLE `group_member` (
    `id` BIGINT NOT NULL COMMENT 'ID',
    `group_id` BIGINT NOT NULL COMMENT '群组ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role` TINYINT DEFAULT 0 COMMENT '角色：0-普通成员，1-管理员，2-群主',
    `nickname` VARCHAR(50) DEFAULT NULL COMMENT '群内昵称',
    `join_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_user` (`group_id`, `user_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群成员表';
```

---

## 四、消息相关表

### 4.1 消息表（message）
```sql
CREATE TABLE `message` (
    `id` BIGINT NOT NULL COMMENT '消息ID',
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

### 4.2 消息已读表（message_read）
```sql
CREATE TABLE `message_read` (
    `id` BIGINT NOT NULL COMMENT 'ID',
    `message_id` BIGINT NOT NULL COMMENT '消息ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `read_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '阅读时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_user` (`message_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息已读表';
```

---

## 五、AI相关表

### 5.1 AI角色表（ai_profile）
```sql
CREATE TABLE `ai_profile` (
    `id` BIGINT NOT NULL COMMENT 'AI ID',
    `owner_id` BIGINT NOT NULL COMMENT '所有者ID',
    `name` VARCHAR(50) NOT NULL COMMENT 'AI名称',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT 'AI头像',
    `persona` TEXT NOT NULL COMMENT '人设描述',
    `model_provider` VARCHAR(50) NOT NULL COMMENT '模型提供商',
    `model` VARCHAR(100) NOT NULL COMMENT '模型名称',
    `api_key_enc` VARCHAR(500) DEFAULT NULL COMMENT 'API Key（加密）',
    `is_group` TINYINT DEFAULT 0 COMMENT '是否群聊AI：0-否，1-是',
    `status` TINYINT DEFAULT 0 COMMENT '状态：0-正常，1-禁用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    PRIMARY KEY (`id`),
    KEY `idx_owner_id` (`owner_id`),
    KEY `idx_is_group` (`is_group`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI角色表';
```

### 5.2 AI群配置表（ai_group_config）
```sql
CREATE TABLE `ai_group_config` (
    `id` BIGINT NOT NULL COMMENT 'ID',
    `group_id` BIGINT NOT NULL COMMENT '群组ID',
    `ai_id` BIGINT NOT NULL COMMENT 'AI ID',
    `trigger_keywords` VARCHAR(500) DEFAULT NULL COMMENT '触发关键词（逗号分隔）',
    `trigger_probability` DECIMAL(3,2) DEFAULT 0.10 COMMENT '触发概率',
    `is_enabled` TINYINT DEFAULT 1 COMMENT '是否启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_ai` (`group_id`, `ai_id`),
    KEY `idx_ai_id` (`ai_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI群配置表';
```

### 5.3 AI记忆表（ai_memory）
```sql
CREATE TABLE `ai_memory` (
    `id` BIGINT NOT NULL COMMENT '记忆ID',
    `ai_id` BIGINT NOT NULL COMMENT 'AI ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `summary` TEXT NOT NULL COMMENT '记忆摘要',
    `keywords` VARCHAR(500) DEFAULT NULL COMMENT '关键词（逗号分隔）',
    `vector_id` VARCHAR(100) DEFAULT NULL COMMENT '向量ID（Milvus）',
    `message_count` INT DEFAULT 0 COMMENT '关联消息数量',
    `status` TINYINT DEFAULT 0 COMMENT '状态：0-有效，1-过期',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_ai_user` (`ai_id`, `user_id`),
    KEY `idx_vector_id` (`vector_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI记忆表';
```

---

## 六、文件相关表

### 6.1 文件上传表（file_upload）
```sql
CREATE TABLE `file_upload` (
    `id` BIGINT NOT NULL COMMENT '文件ID',
    `user_id` BIGINT NOT NULL COMMENT '上传用户ID',
    `original_name` VARCHAR(200) NOT NULL COMMENT '原始文件名',
    `storage_path` VARCHAR(500) NOT NULL COMMENT '存储路径',
    `file_size` BIGINT NOT NULL COMMENT '文件大小（字节）',
    `file_type` VARCHAR(50) NOT NULL COMMENT '文件类型',
    `thumbnail_path` VARCHAR(500) DEFAULT NULL COMMENT '缩略图路径',
    `width` INT DEFAULT NULL COMMENT '图片宽度',
    `height` INT DEFAULT NULL COMMENT '图片高度',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_file_type` (`file_type`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件上传表';
```

---

## 七、Redis 缓存设计

### 7.1 用户在线状态
```
Key: user:online:{userId}
Type: String
Value: websocket_session_id
TTL: 无（主动删除）
```

### 7.2 用户Token缓存
```
Key: user:token:{userId}
Type: String
Value: jwt_token
TTL: 7200秒（2小时）
```

### 7.3 消息未读数
```
Key: message:unread:{userId}:{targetId}
Type: String
Value: 未读消息数量
TTL: 无
```

### 7.4 群成员列表缓存
```
Key: group:members:{groupId}
Type: Set
Value: userId集合
TTL: 3600秒（1小时）
```

### 7.5 验证码缓存
```
Key: sms:code:{phone}
Type: String
Value: 验证码
TTL: 300秒（5分钟）
```

---

## 八、Milvus 向量数据库设计

### 8.1 AI记忆向量集合
```python
# 集合名称: ai_memory_vectors
# 维度: 1536 (OpenAI Embedding)
# 索引类型: IVF_FLAT

collection = Collection("ai_memory_vectors")

schema = {
    "fields": [
        {"name": "id", "dtype": DataType.INT64, "is_primary": True},
        {"name": "ai_id", "dtype": DataType.INT64},
        {"name": "user_id", "dtype": DataType.INT64},
        {"name": "memory_id", "dtype": DataType.INT64},
        {"name": "embedding", "dtype": DataType.FLOAT_VECTOR, "dim": 1536},
        {"name": "create_time", "dtype": DataType.INT64}
    ]
}
```
