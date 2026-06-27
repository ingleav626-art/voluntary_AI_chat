package com.voluntary.chat.server.service;

import com.voluntary.chat.server.entity.AiProfile;
import com.voluntary.chat.server.util.AesKeyUtil;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * AI 基础服务（ai-core 层，供同一模块的 AiChatService / AiMemoryService 调用）
 *
 * <p>
 * 提供基础的 getAiProfileById 和 decryptApiKey 方法。
 * server 模块的完整版 {@code AiService} 有独立的实现，类路径不冲突。
 * </p>
 */
@Slf4j
@Service
public class BaseAiService {

    /**
     * 获取 AI 角色
     *
     * @param aiId AI 角色 ID
     * @return AiProfile 实体
     */
    public AiProfile getAiProfileById(final Long aiId) {
        throw new UnsupportedOperationException(
                "ai-core 层的 getAiProfileById 由子类或 server 模块的完整版实现");
    }

    /**
     * 解密 API Key
     *
     * @param profile AI 角色
     * @return 解密后的 API Key
     */
    public String decryptApiKey(final AiProfile profile) {
        return AesKeyUtil.decrypt(profile.getApiKeyEnc(), getEncryptionKey());
    }

    /**
     * 获取加密密钥（环境变量 AI_ENCRYPTION_KEY）
     */
    protected String getEncryptionKey() {
        final String key = System.getenv("AI_ENCRYPTION_KEY");
        if (key == null || key.isEmpty()) {
            log.warn("AI 加密密钥未配置，请设置环境变量 AI_ENCRYPTION_KEY");
            return "default-key-placeholder-32bytes!";
        }
        return key;
    }
}
