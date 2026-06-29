package com.voluntary.chat.server.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 群配置响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiGroupConfigResponse {

    private Long configId;
    private Long aiId;
    private String aiName;
    private String triggerKeywords;
    private Double triggerProbability;
    private Boolean isEnabled;
    private Integer cooldownSeconds;
}
