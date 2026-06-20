-- Voluntary AI Chat 数据库初始化脚本
-- 使用 MySQL 8.0+

CREATE DATABASE IF NOT EXISTS `voluntary_ai_chat` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `voluntary_ai_chat`;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
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

-- 用户Token表
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
