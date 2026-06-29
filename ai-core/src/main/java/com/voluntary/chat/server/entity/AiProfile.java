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
 * AI 角色实体
 */
@Data
@TableName("ai_profile")
public class AiProfile {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户ID（所有者） */
    private Long userId;

    /** AI 名称 */
    private String name;

    /** AI 头像 */
    private String avatar;

    /** AI 人设/性格描述 */
    private String persona;

    /** 系统提示词 */
    private String systemPrompt;

    /** 模型提供商：openai, deepseek, qwen, zhipu, custom */
    private String modelProvider;

    /** 模型名称 */
    private String model;

    /** API Key（AES-256-GCM 加密） */
    private String apiKeyEnc;

    /** API 基准地址（可选，用于自定义 API endpoint） */
    private String baseUrl;

    /** 是否可用于群聊 */
    private Boolean isGroup;

    /** AI 回复创造性参数（0.0-2.0） */
    private Double temperature;

    /** AI 回复最大长度 */
    private Integer maxTokens;

    /** 状态：0-正常，1-禁用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}