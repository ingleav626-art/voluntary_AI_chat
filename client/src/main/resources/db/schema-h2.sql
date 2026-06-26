-- H2 兼容建表脚本（客户包专用）
-- 只包含 AI 模块需要的表，不含真人聊天模块
-- 语法：去掉 MySQL 特有的反引号、ENGINE、ON UPDATE 等

CREATE TABLE IF NOT EXISTS user (
    id BIGINT NOT NULL COMMENT '用户ID（雪花算法）',
    phone VARCHAR(20) NOT NULL COMMENT '手机号',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    password_hash VARCHAR(100) NOT NULL COMMENT '密码哈希',
    salt VARCHAR(50) NOT NULL COMMENT '密码盐值',
    avatar VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    bio VARCHAR(500) DEFAULT NULL COMMENT '个性签名',
    gender TINYINT DEFAULT 0 COMMENT '性别：0-未知，1-男，2-女',
    age INT DEFAULT NULL COMMENT '年龄',
    birthday DATE DEFAULT NULL COMMENT '生日',
    detail_bio TEXT DEFAULT NULL COMMENT '个人详细说明',
    status TINYINT DEFAULT 0 COMMENT '状态：0-正常，1-禁用',
    last_login_time TIMESTAMP DEFAULT NULL COMMENT '最后登录时间',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    PRIMARY KEY (id),
    UNIQUE (phone),
    UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS user_token (
    id BIGINT NOT NULL COMMENT 'ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    access_token VARCHAR(500) NOT NULL COMMENT '访问Token',
    refresh_token VARCHAR(500) NOT NULL COMMENT '刷新Token',
    access_expires TIMESTAMP NOT NULL COMMENT '访问Token过期时间',
    refresh_expires TIMESTAMP NOT NULL COMMENT '刷新Token过期时间',
    device_info VARCHAR(200) DEFAULT NULL COMMENT '设备信息',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS message (
    id BIGINT NOT NULL COMMENT '消息ID（雪花算法）',
    session_id VARCHAR(100) NOT NULL COMMENT '会话ID',
    sender_id BIGINT NOT NULL COMMENT '发送者ID',
    sender_type TINYINT DEFAULT 0 COMMENT '发送者类型：0-用户，1-AI',
    target_id BIGINT NOT NULL COMMENT '目标用户/群组ID',
    target_type TINYINT DEFAULT 0 COMMENT '目标类型：0-用户，1-群组',
    type TINYINT DEFAULT 0 COMMENT '消息类型：0-文本，1-图片，2-AI，3-撤回，4-转发',
    content TEXT NOT NULL COMMENT '消息内容',
    extra TEXT DEFAULT NULL COMMENT '额外信息（H2 不支持 JSON，用 TEXT 代替）',
    recall_time TIMESTAMP DEFAULT NULL COMMENT '撤回时间',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS message_read (
    id BIGINT NOT NULL COMMENT 'ID',
    message_id BIGINT NOT NULL COMMENT '消息ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    session_id VARCHAR(100) NOT NULL COMMENT '会话ID',
    read_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '阅读时间',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE (message_id, user_id)
);

CREATE TABLE IF NOT EXISTS ai_profile (
    id BIGINT NOT NULL COMMENT 'AI ID（雪花算法）',
    user_id BIGINT NOT NULL COMMENT '用户ID（所有者）',
    name VARCHAR(50) NOT NULL COMMENT 'AI 名称',
    avatar VARCHAR(500) DEFAULT NULL COMMENT 'AI 头像',
    persona VARCHAR(2000) DEFAULT NULL COMMENT 'AI 人设/性格描述',
    system_prompt TEXT DEFAULT NULL COMMENT '系统提示词',
    model_provider VARCHAR(20) NOT NULL COMMENT '模型提供商',
    model VARCHAR(50) NOT NULL COMMENT '模型名称',
    api_key_enc VARCHAR(500) NOT NULL COMMENT 'API Key（AES-256-GCM 加密）',
    is_group TINYINT DEFAULT 0 COMMENT '是否可用于群聊：0-否，1-是',
    temperature DECIMAL(3,2) DEFAULT 0.70 COMMENT 'AI 回复创造性参数',
    max_tokens INT DEFAULT 2048 COMMENT 'AI 回复最大长度',
    status TINYINT DEFAULT 0 COMMENT '状态：0-正常，1-禁用',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS ai_group_config (
    id BIGINT NOT NULL COMMENT '配置ID（雪花算法）',
    group_id BIGINT NOT NULL COMMENT '群组ID',
    ai_id BIGINT NOT NULL COMMENT 'AI 角色ID',
    trigger_keywords VARCHAR(200) DEFAULT NULL COMMENT '触发关键词',
    trigger_probability DECIMAL(3,2) DEFAULT 0.00 COMMENT '触发概率',
    is_enabled TINYINT DEFAULT 1 COMMENT '是否启用：0-否，1-是',
    cooldown_seconds INT DEFAULT 30 COMMENT 'AI 回复冷却时间（秒）',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    PRIMARY KEY (id),
    UNIQUE (group_id, ai_id)
);

CREATE TABLE IF NOT EXISTS ai_memory (
    id BIGINT NOT NULL COMMENT '记忆ID（雪花算法）',
    ai_id BIGINT NOT NULL COMMENT 'AI 角色ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    session_id VARCHAR(100) DEFAULT NULL COMMENT '会话ID',
    summary VARCHAR(500) NOT NULL COMMENT '记忆摘要',
    keywords VARCHAR(200) DEFAULT NULL COMMENT '关键词',
    importance DECIMAL(3,2) DEFAULT 0.50 COMMENT '记忆重要度评分',
    vector_id VARCHAR(100) DEFAULT NULL COMMENT '向量ID',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    PRIMARY KEY (id)
);
