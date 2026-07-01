package com.voluntary.chat.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voluntary.chat.common.enums.SenderType;
import com.voluntary.chat.common.enums.TargetType;
import com.voluntary.chat.server.client.OpenAiClient;
import com.voluntary.chat.server.config.AiConfig;
import com.voluntary.chat.server.entity.AiProfile;
import com.voluntary.chat.server.entity.Message;
import com.voluntary.chat.server.mapper.MessageMapper;
import com.voluntary.chat.server.websocket.AiStreamSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 对话服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final BaseAiService aiService;
    private final OpenAiClient openAiClient;
    private final AiConfig aiConfig;
    private final MessageMapper messageMapper;
    private final AiMemoryService aiMemoryService;

    private AiStreamSender streamSender;

    @Autowired
    @Qualifier("aiTaskExecutor")
    private TaskExecutor aiTaskExecutor;

    /**
     * 设置流式推送接口（用于测试注入 mock）
     */
    public void setStreamSender(final AiStreamSender streamSender) {
        this.streamSender = streamSender;
    }

    /**
     * 设置 AI 任务执行器（用于测试注入同步执行器）
     */
    void setAiTaskExecutor(final TaskExecutor aiTaskExecutor) {
        this.aiTaskExecutor = aiTaskExecutor;
    }

    /**
     * 处理 AI 单聊（流式）
     */
    public void handleAiChat(
            final Long userId,
            final Long aiId,
            final String sessionId,
            final String content,
            final String messageId) {

        aiTaskExecutor.execute(() -> doHandleAiChat(userId, aiId, sessionId, content, messageId));
    }

    /**
     * 处理群聊 AI 对话（流式 + 广播）
     *
     * <p>
     * 与私聊不同：完成后以 GROUP_MESSAGE 类型广播给群内所有成员。
     * 同一 (groupId, aiId) 的请求串行排队，避免上下文互相干扰。
     * </p>
     *
     * @param groupId    群ID
     * @param senderId   发送者用户ID
     * @param senderName 发送者用户名（用于上下文区分消息来源）
     * @param aiId       AI 角色ID
     * @param content    用户消息内容
     * @param messageId  消息ID
     */
    public void handleGroupAiChat(
            final Long groupId,
            final Long senderId,
            final String senderName,
            final Long aiId,
            final String content,
            final String messageId) {

        final String sessionId = "g_" + groupId + "_a_" + aiId;
        // ai-core 基础版直接复用私聊逻辑，server 模块会覆盖此方法实现群聊广播
        handleAiChat(senderId, aiId, sessionId, content, messageId);
    }

    private void doHandleAiChat(
            final Long userId,
            final Long aiId,
            final String sessionId,
            final String content,
            final String messageId) {

        try {
            final AiProfile profile = aiService.getAiProfileById(aiId);
            final String apiKey = aiService.decryptApiKey(profile);
            final String baseUrl = openAiClient.getBaseUrl(profile.getModelProvider());

            saveUserMessage(userId, sessionId, content, aiId);

            final List<Map<String, String>> messages = buildContext(profile, userId, sessionId, content);

            final StringBuilder fullResponse = new StringBuilder();

            final OpenAiClient.StreamConfig streamConfig = new OpenAiClient.StreamConfig(
                    baseUrl,
                    apiKey,
                    profile.getModel(),
                    messages,
                    profile.getTemperature(),
                    profile.getMaxTokens(),
                    chunk -> streamSender.sendAiStream(userId, messageId, chunk, false),
                    completeContent -> {
                        fullResponse.append(completeContent);
                        final Long aiMessageId = saveAiMessage(profile.getId(), sessionId, completeContent, userId);
                        streamSender.sendAiStream(userId, messageId, completeContent, true, aiMessageId);
                        log.info("AI 对话完成: aiId={}, userId={}, messageId={}", aiId, userId, aiMessageId);

                        aiTaskExecutor.execute(() -> {
                            try {
                                aiMemoryService.summarizeIfNeeded(aiId, userId, sessionId);
                            } catch (final RuntimeException e) {
                                log.warn("记忆摘要生成失败: aiId={}, userId={}", aiId, userId, e);
                            }
                        });
                    });

            openAiClient.streamChatCompletion(streamConfig);
        } catch (final RuntimeException e) {
            log.error("AI 对话处理失败: aiId={}, userId={}, messageId={}", aiId, userId, messageId, e);
            if (streamSender != null) {
                streamSender.sendAiStream(userId, messageId, "AI 回复失败，请稍后重试", true, null);
            }
        }
    }

    private List<Map<String, String>> buildContext(
            final AiProfile profile,
            final Long userId,
            final String sessionId,
            final String userContent) {

        final List<Map<String, String>> messages = new ArrayList<>();

        final String systemPrompt = profile.getSystemPrompt() != null
                ? profile.getSystemPrompt()
                : profile.getPersona();

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            final Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messages.add(systemMessage);
        }

        final List<String> relevantMemories = searchMemoriesSafely(profile, userId, userContent);
        if (!relevantMemories.isEmpty()) {
            final StringBuilder memoryContext = new StringBuilder("以下是关于该用户的长期记忆，请参考：\n");
            for (int i = 0; i < relevantMemories.size(); i++) {
                memoryContext.append(i + 1).append(". ").append(relevantMemories.get(i)).append("\n");
            }
            final Map<String, String> memoryMessage = new HashMap<>();
            memoryMessage.put("role", "system");
            memoryMessage.put("content", memoryContext.toString());
            messages.add(memoryMessage);
        }

        final int maxHistoryRounds = aiConfig.getContext().getMaxHistoryRounds();
        final List<Message> historyMessages = getHistoryMessages(sessionId, maxHistoryRounds * 2);

        for (final Message msg : historyMessages) {
            final Map<String, String> historyMessage = new HashMap<>();
            if (msg.getSenderType() == SenderType.AI.ordinal()) {
                historyMessage.put("role", "assistant");
            } else {
                historyMessage.put("role", "user");
            }
            historyMessage.put("content", msg.getContent());
            messages.add(historyMessage);
        }

        final Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", userContent);
        messages.add(userMessage);

        return messages;
    }

    private List<String> searchMemoriesSafely(final AiProfile profile, final Long userId, final String query) {
        try {
            final List<String> memories = aiMemoryService.searchRelevantMemories(profile.getId(), userId, query);
            return memories != null ? memories : Collections.emptyList();
        } catch (final RuntimeException e) {
            log.warn("记忆检索失败，降级为无记忆上下文: aiId={}", profile.getId(), e);
            return Collections.emptyList();
        }
    }

    private List<Message> getHistoryMessages(final String sessionId, final int limit) {
        final LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getSessionId, sessionId)
                .isNull(Message::getRecallTime)
                .orderByDesc(Message::getCreateTime)
                .last("LIMIT " + limit);

        final List<Message> messages = messageMapper.selectList(wrapper);
        final List<Message> result = new ArrayList<>(messages);
        Collections.reverse(result);
        return result;
    }

    @Transactional
    public void saveUserMessage(final Long userId, final String sessionId,
            final String content, final Long aiId) {
        final Message message = new Message();
        message.setSessionId(sessionId);
        message.setSenderId(userId);
        message.setSenderType(SenderType.USER.ordinal());
        message.setTargetId(aiId);
        message.setTargetType(TargetType.AI.ordinal());
        message.setType(0);
        message.setContent(content);
        message.setIsDeleted(0);

        messageMapper.insert(message);
        log.debug("用户AI消息已保存: userId={}, aiId={}, sessionId={}", userId, aiId, sessionId);
    }

    @Transactional
    public Long saveAiMessage(final Long aiId, final String sessionId, final String content, final Long userId) {
        final Message message = new Message();
        message.setSessionId(sessionId);
        message.setSenderId(aiId);
        message.setSenderType(SenderType.AI.ordinal());
        message.setTargetId(userId);
        message.setTargetType(TargetType.USER.ordinal());
        message.setType(0);
        message.setContent(content);
        message.setIsDeleted(0);

        messageMapper.insert(message);
        return message.getId();
    }
}
