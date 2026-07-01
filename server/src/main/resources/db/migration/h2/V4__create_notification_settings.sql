-- Flyway V3: 创建通知设置表（H2 版本）

CREATE TABLE IF NOT EXISTS notification_settings (
    id BIGINT NOT NULL COMMENT '主键ID（雪花算法）',
    user_id BIGINT NOT NULL COMMENT '用户ID，一对一关系',
    message_notification TINYINT DEFAULT 1 COMMENT '新消息通知：0-关闭，1-开启',
    message_sound TINYINT DEFAULT 1 COMMENT '新消息声音：0-关闭，1-开启',
    ai_greeting_notification TINYINT DEFAULT 1 COMMENT 'AI主动问候通知：0-关闭，1-开启',
    ai_greeting_sound TINYINT DEFAULT 1 COMMENT 'AI问候声音：0-关闭，1-开启',
    todo_reminder TINYINT DEFAULT 1 COMMENT '待办提醒：0-关闭，1-开启（待办是强提醒，建议始终开启）',
    todo_sound TINYINT DEFAULT 1 COMMENT '待办声音：0-关闭，1-开启',
    do_not_disturb TINYINT DEFAULT 0 COMMENT '免打扰模式：0-关闭，1-开启',
    dnd_start_time TIME DEFAULT '22:00:00' COMMENT '免打扰开始时间',
    dnd_end_time TIME DEFAULT '09:00:00' COMMENT '免打扰结束时间',
    merge_window_seconds INT DEFAULT 5 COMMENT '通知合并窗口（秒），同一会话 N 秒内的消息合并为一条通知',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    PRIMARY KEY (id),
    UNIQUE (user_id)
);