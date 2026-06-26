package com.voluntary.chat.server.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 角色响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiProfileResponse {

    private Long aiId;
    private String name;
    private String avatar;
    private String persona;
    private String modelProvider;
    private String model;
    private Boolean isGroup;
}
