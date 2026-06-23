package com.voluntary.chat.server.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改 AI 角色请求
 */
@Data
public class UpdateAiProfileRequest {

    @Size(max = 50, message = "AI 名称最长 50 字符")
    private String name;

    private String avatar;

    @Size(max = 2000, message = "AI 人设最长 2000 字符")
    private String persona;

    private String systemPrompt;

    private String model;

    private String apiKey;

    private Boolean isGroup;

    private Double temperature;

    private Integer maxTokens;
}