package org.example.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 群配置模型
 *
 * <p>对应后端 AiGroupConfigResponse，用于群聊 AI 配置展示。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiGroupConfig {

    /** 配置ID */
    private Long configId;

    /** AI 角色ID */
    private Long aiId;

    /** AI 名称 */
    private String aiName;

    /** 触发关键词 */
    private String triggerKeywords;

    /** 触发概率 */
    private Double triggerProbability;

    /** 是否启用 */
    private Boolean isEnabled;
}
