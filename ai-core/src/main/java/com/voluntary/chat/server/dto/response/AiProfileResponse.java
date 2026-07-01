package com.voluntary.chat.server.dto.response;

import com.voluntary.chat.server.entity.AiProfile;
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
    private String systemPrompt;
    private String modelProvider;
    private String model;
    private String baseUrl;
    private Boolean isGroup;
    private Double temperature;
    private Integer maxTokens;

    /**
     * 从实体构造响应
     */
    public static AiProfileResponse fromEntity(final AiProfile profile) {
        return AiProfileResponse.builder()
                .aiId(profile.getId())
                .name(profile.getName())
                .avatar(profile.getAvatar())
                .persona(profile.getPersona())
                .systemPrompt(profile.getSystemPrompt())
                .modelProvider(profile.getModelProvider())
                .model(profile.getModel())
                .baseUrl(profile.getBaseUrl())
                .isGroup(profile.getIsGroup())
                .temperature(profile.getTemperature())
                .maxTokens(profile.getMaxTokens())
                .build();
    }
}
