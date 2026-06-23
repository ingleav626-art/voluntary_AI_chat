package com.voluntary.chat.server.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * AI 对话请求（REST 同步模式）
 */
@Data
public class AiChatRequest {

    @NotNull(message = "AI ID 不能为空")
    private Long aiId;

    @NotBlank(message = "对话内容不能为空")
    private String content;

    private String conversationId;
}