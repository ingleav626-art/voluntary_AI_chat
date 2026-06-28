package com.voluntary.chat.server.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 群配置实体
 */
@Data
@TableName("ai_group_config")
public class AiGroupConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 群组ID */
    private Long groupId;

    /** AI 角色ID */
    private Long aiId;

    /** 触发关键词（逗号分隔） */
    private String triggerKeywords;

    /** 触发概率（0.0-1.0） */
    private Double triggerProbability;

    /** 是否启用 */
    private Boolean isEnabled;

    /** AI 回复冷却时间（秒） */
    private Integer cooldownSeconds;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}