package com.voluntary.chat.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voluntary.chat.server.client.OpenAiClient;
import com.voluntary.chat.server.config.AiConfig;
import com.voluntary.chat.server.entity.AiProfile;
import com.voluntary.chat.server.entity.Message;
import com.voluntary.chat.server.mapper.MessageMapper;
import com.voluntary.chat.server.websocket.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * AI 对话服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final AiService aiService;
    private final OpenAiClient openAiClient;
    private final AiConfig aiConfig;
    private final MessageMapper messageMapper;
    private final ChatWebSocketHandler webSocketHandler;

    /**
     * 处理 AI 单聊（流式）
     */
    @Transactional
    public void handleAiChat(
            final Long userId,
            final Long aiId,
            final String sessionId,
            final String content,
            final String messageId) {

        // 获取 AI 角色信息
        final AiProfile profile = aiService.getAiProfileById(aiId);
        final String apiKey = aiService.decryptApiKey(profile);
        final String baseUrl = openAiClient.getBaseUrl(profile.getModelProvider());

        // 构建对话上下文
        final List<Map<String, String>> messages = buildContext(profile, userId, sessionId, content);

        // 流式调用 AI
        final StringBuilder fullResponse = new StringBuilder();

        final OpenAiClient.StreamConfig streamConfig = new OpenAiClient.StreamConfig(
                baseUrl,
                apiKey,
                profile.getModel(),
                messages,
                profile.getTemperature(),
                profile.getMaxTokens(),
                // onChunk: 推送增量内容
                chunk -> {
                    webSocketHandler.sendAiStream(userId, messageId, chunk, false);
                },
                // onComplete: 推送完成消息
                completeContent -> {
                    fullResponse.append(completeContent);
                    // 保存 AI 消息
                    final Long aiMessageId = saveAiMessage(profile.getId(), sessionId, completeContent, userId);
                    // 推送完成消息
                    webSocketHandler.sendAiStream(userId, messageId, completeContent, true, aiMessageId);
                    log.info("AI 对话完成: aiId={}, userId={}, messageId={}", aiId, userId, aiMessageId);
                }
        );

        openAiClient.streamChatCompletion(streamConfig);
    }

    /**
     * 构建对话上下文
     */
    private List<Map<String, String>> buildContext(
            final AiProfile profile,
            final Long userId,
            final String sessionId,
            final String userContent) {

        final List<Map<String, String>> messages = new ArrayList<>();

        // 1. System Prompt（AI 人设）
        final String systemPrompt = profile.getSystemPrompt() != null
                ? profile.getSystemPrompt()
                : profile.getPersona();

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            final Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messages.add(systemMessage);
        }

        // 2. 历史消息（最近 N 轮）
        final int maxHistoryRounds = aiConfig.getContext().getMaxHistoryRounds();
        final List<Message> historyMessages = getHistoryMessages(sessionId, maxHistoryRounds * 2);

        for (final Message msg : historyMessages) {
            final Map<String, String> historyMessage = new HashMap<>();
            if (msg.getSenderType() == 1) { // AI
                historyMessage.put("role", "assistant");
            } else { // User
                historyMessage.put("role", "user");
            }
            historyMessage.put("content", msg.getContent());
            messages.add(historyMessage);
        }

        // 3. 当前用户输入
        final Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", userContent);
        messages.add(userMessage);

        return messages;
    }

    /**
     * 获取历史消息
     */
    private List<Message> getHistoryMessages(final String sessionId, final int limit) {
        final LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getSessionId, sessionId)
               .eq(Message::getRecallTime, null) // 未撤回
               .orderByDesc(Message::getCreateTime)
               .last("LIMIT " + limit);

        final List<Message> messages = messageMapper.selectList(wrapper);
        // 反转顺序，使历史消息按时间正序排列
        final List<Message> result = new ArrayList<>(messages);
        java.util.Collections.reverse(result);
        return result;
    }

    /**
     * 保存 AI 消息
     */
    private Long saveAiMessage(final Long aiId, final String sessionId, final String content, final Long userId) {
        final Message message = new Message();
        message.setSessionId(sessionId);
        message.setSenderId(aiId);
        message.setSenderType(1); // AI
        message.setTargetId(userId);
        message.setTargetType(0); // User
        message.setType(0); // TEXT
        message.setContent(content);
        message.setIsDeleted(0);

        messageMapper.insert(message);
        return message.getId();
    }
}