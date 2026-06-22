package com.voluntary.chat.server.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 记忆响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMemoryResponse {

    /** 记忆ID */
    private Long memoryId;

    /** 摘要内容 */
    private String summary;

    /** 关键词（逗号分隔） */
    private String keywords;

    /** 重要度评分（0-1） */
    private Double importance;

    /** 创建时间 */
    private String createTime;
}