-- H2 兼容版数据库建表脚本
-- 去掉 MySQL 特有的 ENGINE=InnoDB、ON UPDATE CURRENT_TIMESTAMP、反引号、COMMENT 等

CREATE TABLE IF NOT EXISTS user (
    id BIGINT NOT NULL,
    phone VARCHAR(20) NOT NULL,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    salt VARCHAR(50) NOT NULL,
    avatar VARCHAR(500) DEFAULT NULL,
    bio VARCHAR(500) DEFAULT NULL,
    gender TINYINT DEFAULT 0,
    age INT DEFAULT NULL,
    birthday DATE DEFAULT NULL,
    detail_bio TEXT DEFAULT NULL,
    status TINYINT DEFAULT 0,
    last_login_time TIMESTAMP DEFAULT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE (phone),
    UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS user_token (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    access_token VARCHAR(500) NOT NULL,
    refresh_token VARCHAR(500) NOT NULL,
    access_expires TIMESTAMP NOT NULL,
    refresh_expires TIMESTAMP NOT NULL,
    device_info VARCHAR(200) DEFAULT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS message (
    id BIGINT NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    sender_id BIGINT NOT NULL,
    sender_type TINYINT DEFAULT 0,
    target_id BIGINT NOT NULL,
    target_type TINYINT DEFAULT 0,
    type TINYINT DEFAULT 0,
    content TEXT NOT NULL,
    extra TEXT DEFAULT NULL,
    recall_time TIMESTAMP DEFAULT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS message_read (
    id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    read_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (message_id, user_id)
);

CREATE TABLE IF NOT EXISTS friend_apply (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    message VARCHAR(200) DEFAULT NULL,
    status TINYINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS friend (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    friend_id BIGINT NOT NULL,
    remark VARCHAR(50) DEFAULT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE (user_id, friend_id)
);

CREATE TABLE IF NOT EXISTS chat_group (
    id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    avatar VARCHAR(500) DEFAULT NULL,
    announcement VARCHAR(500) DEFAULT NULL,
    announcement_pinned TINYINT DEFAULT 0,
    owner_id BIGINT NOT NULL,
    max_member_count INT DEFAULT 200,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS group_member (
    id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role TINYINT DEFAULT 0,
    nickname VARCHAR(50) DEFAULT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE (group_id, user_id)
);

CREATE TABLE IF NOT EXISTS ai_profile (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    avatar VARCHAR(500) DEFAULT NULL,
    persona VARCHAR(2000) DEFAULT NULL,
    system_prompt TEXT DEFAULT NULL,
    model_provider VARCHAR(20) NOT NULL,
    model VARCHAR(50) NOT NULL,
    api_key_enc VARCHAR(500) NOT NULL,
    is_group TINYINT DEFAULT 0,
    temperature DECIMAL(3,2) DEFAULT 0.70,
    max_tokens INT DEFAULT 2048,
    status TINYINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS ai_group_config (
    id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    ai_id BIGINT NOT NULL,
    trigger_keywords VARCHAR(200) DEFAULT NULL,
    trigger_probability DECIMAL(3,2) DEFAULT 0.00,
    is_enabled TINYINT DEFAULT 1,
    cooldown_seconds INT DEFAULT 30,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE (group_id, ai_id)
);

CREATE TABLE IF NOT EXISTS ai_memory (
    id BIGINT NOT NULL,
    ai_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(100) DEFAULT NULL,
    summary VARCHAR(500) NOT NULL,
    keywords VARCHAR(200) DEFAULT NULL,
    importance DECIMAL(3,2) DEFAULT 0.50,
    vector_id VARCHAR(100) DEFAULT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT DEFAULT 0,
    PRIMARY KEY (id)
);
