package com.voluntary.chat.server.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 群配置请求
 */
@Data
public class AiGroupConfigRequest {

    @NotNull(message = "AI ID 不能为空")
    private Long aiId;

    @Size(max = 200, message = "触发关键词最长 200 字符")
    private String triggerKeywords;

    private Double triggerProbability;

    private Boolean isEnabled;

    private Integer cooldownSeconds;
}