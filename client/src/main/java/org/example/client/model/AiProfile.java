package org.example.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 角色模型
 *
 * <p>对应后端 AiProfileResponse，用于 AI 列表展示和编辑。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiProfile {

    /** AI 角色ID */
    private Long aiId;

    /** AI 名称 */
    private String name;

    /** AI 头像URL */
    private String avatar;

    /** 开场白（创建后自动发送的首条消息） */
    private String openingMessage;

    /** AI 人设/性格描述 */
    private String persona;

    /** 模型提供商：openai, deepseek, qwen, zhipu, custom */
    private String modelProvider;

    /** 模型名称 */
    private String model;

    /** 是否可用于群聊 */
    private Boolean isGroup;

    /** 系统提示词（编辑用） */
    private String systemPrompt;

    /** API Key（创建/编辑时使用） */
    private String apiKey;

    /** API 基准地址（可选，用于自定义 API endpoint） */
    private String baseUrl;

    /** 温度参数 */
    private Double temperature;

    /** 最大 token 数 */
    private Integer maxTokens;
}
