package com.voluntary.chat.server.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建 AI 角色请求
 */
@Data
public class CreateAiProfileRequest {

    @NotBlank(message = "AI 名称不能为空")
    @Size(max = 50, message = "AI 名称最长 50 字符")
    private String name;

    private String avatar;

    @Size(max = 2000, message = "AI 人设最长 2000 字符")
    private String persona;

    private String systemPrompt;

    @NotBlank(message = "模型提供商不能为空")
    private String modelProvider;

    @NotBlank(message = "模型名称不能为空")
    private String model;

    @NotBlank(message = "API Key 不能为空")
    private String apiKey;

    private Boolean isGroup;

    private Double temperature;

    private Integer maxTokens;
}
