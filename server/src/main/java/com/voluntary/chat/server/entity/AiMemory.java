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
 * AI 记忆实体
 */
@Data
@TableName("ai_memory")
public class AiMemory {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** AI 角色ID */
    private Long aiId;

    /** 用户ID */
    private Long userId;

    /** 会话ID */
    private String sessionId;

    /** 记忆摘要 */
    private String summary;

    /** 关键词（逗号分隔） */
    private String keywords;

    /** 记忆重要度评分（0.0-1.0） */
    private Double importance;

    /** 向量ID（Milvus/Qdrant） */
    private String vectorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}