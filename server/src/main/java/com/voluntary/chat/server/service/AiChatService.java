package com.voluntary.chat.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voluntary.chat.common.enums.SenderType;
import com.voluntary.chat.common.enums.TargetType;
import com.voluntary.chat.server.client.OpenAiClient;
import com.voluntary.chat.server.config.AiConfig;
import com.voluntary.chat.server.entity.AiProfile;
import com.voluntary.chat.server.entity.Message;
import com.voluntary.chat.server.entity.User;
import com.voluntary.chat.server.mapper.MessageMapper;
import com.voluntary.chat.server.websocket.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * AI 对话服务
 *
 * <p>
 * 负责 AI 单聊/群聊的流式处理、上下文构建、记忆检索与摘要生成。
 * </p>
 *
 * <p>
 * <b>线程模型说明：</b>
 * <ul>
 * <li>handleAiChat / handleGroupAiChat 由 WebSocket 线程调用，立即返回，避免阻塞</li>
 * <li>AI 调用与消息保存通过 AsyncTaskExecutor 异步执行</li>
 * <li>用户消息保存在独立事务中，AI 调用不在事务内</li>
 * <li>群聊 AI 调用按 (groupId, aiId) 串行化排队，避免上下文互相干扰</li>
 * </ul>
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class AiChatService {

    private final AiService aiService;
    private final OpenAiClient openAiClient;
    private final AiConfig aiConfig;
    private final MessageMapper messageMapper;
    private final AiMemoryService aiMemoryService;
    private final UserService userService;

    /** 群聊 AI 任务队列：key = groupId:aiId，保证同一组的同一 AI 串行执行 */
    private final ConcurrentHashMap<String, Queue<Runnable>> groupAiQueues = new ConcurrentHashMap<>();

    /** 群聊 AI 队列处理状态：key = groupId:aiId */
    private final ConcurrentHashMap<String, AtomicBoolean> groupAiProcessing = new ConcurrentHashMap<>();

    @Autowired
    @Lazy
    private ChatWebSocketHandler webSocketHandler;

    /**
     * 设置 WebSocket 处理器（用于测试注入 mock）
     *
     * @param webSocketHandler WebSocket 处理器
     */
    void setWebSocketHandler(final ChatWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Autowired
    @Qualifier("aiTaskExecutor")
    private TaskExecutor aiTaskExecutor;

    /**
     * 设置 AI 任务执行器（用于测试注入同步执行器）
     *
     * @param aiTaskExecutor 任务执行器
     */
    void setAiTaskExecutor(final TaskExecutor aiTaskExecutor) {
        this.aiTaskExecutor = aiTaskExecutor;
    }

    /**
     * 处理 AI 单聊（流式）
     *
     * <p>
     * 异步执行，立即返回，避免阻塞 WebSocket 线程。
     * </p>
     *
     * @param userId    用户ID
     * @param aiId      AI 角色ID
     * @param sessionId 会话ID
     * @param content   用户消息内容
     * @param messageId 客户端消息ID（用于流式推送匹配）
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

        final String queueKey = groupId + ":" + aiId;
        final Queue<Runnable> queue = groupAiQueues.computeIfAbsent(
                queueKey, k -> new LinkedList<>());
        final AtomicBoolean processing = groupAiProcessing.computeIfAbsent(
                queueKey, k -> new AtomicBoolean(false));

        synchronized (queue) {
            queue.offer(() -> doHandleGroupAiChat(groupId, senderId, senderName, aiId, content, messageId));
            if (processing.compareAndSet(false, true)) {
                aiTaskExecutor.execute(() -> processGroupQueue(queueKey, queue, processing));
            }
        }
    }

    /**
     * 串行消费群聊 AI 队列
     */
    private void processGroupQueue(
            final String queueKey,
            final Queue<Runnable> queue,
            final AtomicBoolean processing) {

        while (true) {
            Runnable task;
            synchronized (queue) {
                task = queue.poll();
                if (task == null) {
                    processing.set(false);
                    // 双重检查：在设置 processing=false 后，可能有新任务入队但未启动处理器
                    if (!queue.isEmpty() && processing.compareAndSet(false, true)) {
                        continue;
                    }
                    return;
                }
            }
            try {
                task.run();
            } catch (final RuntimeException e) {
                log.error("群聊AI任务执行失败: key={}", queueKey, e);
            }
        }
    }

    /**
     * 实际处理 AI 对话（异步执行）
     */
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
                    chunk -> webSocketHandler.sendAiStream(userId, messageId, chunk, false),
                    completeContent -> {
                        fullResponse.append(completeContent);
                        final Long aiMessageId = saveAiMessage(profile.getId(), sessionId, completeContent, userId);
                        webSocketHandler.sendAiStream(userId, messageId, completeContent, true, aiMessageId);
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
            webSocketHandler.sendAiStream(userId, messageId, "AI 回复失败，请稍后重试", true, null);
        }
    }

    /**
     * 实际处理群聊 AI 对话（异步执行）
     *
     * <p>
     * 与私聊的区别：
     * <ul>
     * <li>流式推送：仅推送给触发者（senderId），其他成员暂不看到流式内容</li>
     * <li>完成时：以 GROUP_MESSAGE 广播给群内所有成员</li>
     * <li>上下文：群聊历史消息格式化为 "用户「张三」说：消息内容"</li>
     * </ul>
     * </p>
     */
    private void doHandleGroupAiChat(
            final Long groupId,
            final Long senderId,
            final String senderName,
            final Long aiId,
            final String content,
            final String messageId) {

        final String sessionId = "g_" + groupId + "_a_" + aiId;

        try {
            final AiProfile profile = aiService.getAiProfileById(aiId);
            final String apiKey = aiService.decryptApiKey(profile);
            final String baseUrl = openAiClient.getBaseUrl(profile.getModelProvider());

            saveGroupUserMessage(senderId, sessionId, content, aiId, groupId);

            final List<Map<String, String>> messages = buildContext(profile, senderId, sessionId, content);

            final OpenAiClient.StreamConfig streamConfig = new OpenAiClient.StreamConfig(
                    baseUrl,
                    apiKey,
                    profile.getModel(),
                    messages,
                    profile.getTemperature(),
                    profile.getMaxTokens(),
                    chunk -> webSocketHandler.sendAiStream(senderId, messageId, chunk, false),
                    completeContent -> {
                        final Long aiMessageId = saveGroupAiMessage(
                                profile.getId(), sessionId, completeContent, senderId, groupId);
                        // 发送者：推送流式结束标记
                        webSocketHandler.sendAiStream(senderId, messageId, completeContent, true, aiMessageId);
                        // 群内所有成员：广播 GROUP_MESSAGE
                        broadcastGroupAiMessage(groupId, profile, completeContent, aiMessageId, sessionId);
                        log.info("群聊AI对话完成: groupId={}, aiId={}, senderId={}, messageId={}",
                                groupId, aiId, senderId, aiMessageId);

                        aiTaskExecutor.execute(() -> {
                            try {
                                aiMemoryService.summarizeIfNeeded(aiId, senderId, sessionId);
                            } catch (final RuntimeException e) {
                                log.warn("记忆摘要生成失败: aiId={}, senderId={}", aiId, senderId, e);
                            }
                        });
                    });

            openAiClient.streamChatCompletion(streamConfig);
        } catch (final RuntimeException e) {
            log.error("群聊AI对话处理失败: groupId={}, aiId={}, senderId={}", groupId, aiId, senderId, e);
            webSocketHandler.sendAiStream(senderId, messageId, "AI 回复失败，请稍后重试", true, null);
        }
    }

    /**
     * 广播群聊 AI 消息给群内所有成员
     */
    private void broadcastGroupAiMessage(
            final Long groupId,
            final AiProfile profile,
            final String content,
            final Long aiMessageId,
            final String sessionId) {

        final Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("messageId", aiMessageId);
        data.put("sessionId", sessionId);
        data.put("groupId", groupId);
        data.put("senderId", profile.getId());
        data.put("senderName", profile.getName());
        data.put("senderAvatar", profile.getAvatar() != null ? profile.getAvatar() : "");
        data.put("senderType", SenderType.AI.name());
        data.put("msgType", "TEXT");
        data.put("content", content);
        data.put("createTime", java.time.LocalDateTime.now().toString());

        final com.voluntary.chat.common.model.WebSocketMessage groupMsg = com.voluntary.chat.common.model.WebSocketMessage
                .builder()
                .id(String.valueOf(aiMessageId))
                .type(com.voluntary.chat.common.constant.MessageTypes.GROUP_MESSAGE)
                .data(data)
                .build();

        webSocketHandler.broadcastToGroup("g_" + groupId, groupMsg);
    }

    /**
     * 构建对话上下文
     *
     * <p>
     * 上下文组成：System Prompt + 相关记忆（RAG）+ 历史消息 + 当前输入。
     * 群聊（sessionId 含 "g_"）时，历史消息格式化为 "用户「张三」说：消息内容"，
     * 让 AI 从上下文中区分不同发言者。
     * </p>
     */
    private List<Map<String, String>> buildContext(
            final AiProfile profile,
            final Long userId,
            final String sessionId,
            final String userContent) {

        final List<Map<String, String>> messages = new ArrayList<>();
        final boolean isGroupChat = sessionId.startsWith("g_");

        final String systemPrompt = profile.getSystemPrompt() != null
                ? profile.getSystemPrompt()
                : profile.getPersona();

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            final Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messages.add(systemMessage);
        }

        // 群聊场景下追加群聊上下文说明
        if (isGroupChat) {
            final Map<String, String> groupContextMessage = new HashMap<>();
            groupContextMessage.put("role", "system");
            groupContextMessage.put("content", "当前是群聊场景，多条消息可能来自不同用户，"
                    + "消息格式为「用户名」说：消息内容，请注意区分不同人的发言。");
            messages.add(groupContextMessage);
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

        if (isGroupChat) {
            // 群聊：批量加载发送者名称，格式化消息内容
            final Map<Long, String> senderNames = batchLoadSenderNames(historyMessages, profile);
            for (final Message msg : historyMessages) {
                final Map<String, String> historyMessage = new HashMap<>();
                if (msg.getSenderType() == SenderType.AI.ordinal()) {
                    historyMessage.put("role", "assistant");
                    final String aiName = senderNames.getOrDefault(msg.getSenderId(), "AI");
                    historyMessage.put("content", "「" + aiName + "」说：" + msg.getContent());
                } else {
                    historyMessage.put("role", "user");
                    final String userName = senderNames.getOrDefault(msg.getSenderId(), "用户");
                    historyMessage.put("content", "「" + userName + "」说：" + msg.getContent());
                }
                messages.add(historyMessage);
            }
        } else {
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
        }

        final Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", userContent);
        messages.add(userMessage);

        return messages;
    }

    /**
     * 批量加载历史消息发送者名称
     *
     * <p>
     * 用户消息通过 UserService 批量查询，AI 消息通过 AiService 逐个查询（AI 数量通常很少）。
     * </p>
     */
    private Map<Long, String> batchLoadSenderNames(final List<Message> historyMessages, final AiProfile profile) {
        final Map<Long, String> senderNames = new HashMap<>();

        // 收集用户消息的 senderId
        final Set<Long> userIds = historyMessages.stream()
                .filter(msg -> msg.getSenderType() == SenderType.USER.ordinal())
                .map(Message::getSenderId)
                .collect(Collectors.toSet());

        // 批量查询用户名
        if (!userIds.isEmpty()) {
            try {
                final Map<Long, User> users = userService.findByIds(userIds);
                users.forEach((id, user) -> senderNames.put(id, user.getUsername()));
            } catch (final RuntimeException e) {
                log.warn("批量查询用户名失败，降级为默认名称", e);
            }
        }

        // AI 名称（默认使用当前 profile，同群其他 AI 逐个查询）
        senderNames.put(profile.getId(), profile.getName());
        final Set<Long> aiIds = historyMessages.stream()
                .filter(msg -> msg.getSenderType() == SenderType.AI.ordinal())
                .map(Message::getSenderId)
                .filter(id -> !id.equals(profile.getId()))
                .collect(Collectors.toSet());

        for (final Long aiId : aiIds) {
            try {
                final AiProfile otherProfile = aiService.getAiProfileById(aiId);
                senderNames.put(aiId, otherProfile.getName());
            } catch (final RuntimeException e) {
                log.warn("查询AI名称失败: aiId={}", aiId, e);
            }
        }

        return senderNames;
    }

    /**
     * 安全检索相关记忆（异常时降级为空列表，不影响主流程）
     */
    private List<String> searchMemoriesSafely(final AiProfile profile, final Long userId, final String query) {
        try {
            final List<String> memories = aiMemoryService.searchRelevantMemories(profile.getId(), userId, query);
            return memories != null ? memories : Collections.emptyList();
        } catch (final RuntimeException e) {
            log.warn("记忆检索失败，降级为无记忆上下文: aiId={}", profile.getId(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取历史消息
     */
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

    /**
     * 保存用户消息（独立短事务）
     */
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

    /**
     * 保存 AI 消息（独立短事务）
     */
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

    /**
     * 保存群聊用户消息（独立短事务）
     *
     * <p>
     * 与私聊 saveUserMessage 的区别：targetType 为 GROUP
     * </p>
     */
    @Transactional
    public void saveGroupUserMessage(final Long userId, final String sessionId,
            final String content, final Long aiId, final Long groupId) {
        final Message message = new Message();
        message.setSessionId(sessionId);
        message.setSenderId(userId);
        message.setSenderType(SenderType.USER.ordinal());
        message.setTargetId(groupId);
        message.setTargetType(TargetType.GROUP.ordinal());
        message.setType(0);
        message.setContent(content);
        message.setIsDeleted(0);

        messageMapper.insert(message);
        log.debug("群聊用户AI消息已保存: userId={}, aiId={}, groupId={}, sessionId={}",
                userId, aiId, groupId, sessionId);
    }

    /**
     * 保存群聊 AI 消息（独立短事务）
     *
     * <p>
     * 与私聊 saveAiMessage 的区别：targetType 为 GROUP，targetId 为 groupId
     * </p>
     */
    @Transactional
    public Long saveGroupAiMessage(final Long aiId, final String sessionId,
            final String content, final Long userId, final Long groupId) {
        final Message message = new Message();
        message.setSessionId(sessionId);
        message.setSenderId(aiId);
        message.setSenderType(SenderType.AI.ordinal());
        message.setTargetId(groupId);
        message.setTargetType(TargetType.GROUP.ordinal());
        message.setType(0);
        message.setContent(content);
        message.setIsDeleted(0);

        messageMapper.insert(message);
        return message.getId();
    }
}
