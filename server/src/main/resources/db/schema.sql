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

CREATE TABLE IF NOT EXISTS `group` (
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
