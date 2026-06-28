package com.voluntary.chat.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voluntary.chat.common.dto.PageResult;
import com.voluntary.chat.server.client.EmbeddingClient;
import com.voluntary.chat.server.client.OpenAiClient;
import com.voluntary.chat.server.client.VectorStoreClient;
import com.voluntary.chat.server.config.AiConfig;
import com.voluntary.chat.server.config.CacheProperties;
import com.voluntary.chat.server.entity.AiMemory;
import com.voluntary.chat.server.entity.AiProfile;
import com.voluntary.chat.server.entity.Message;
import com.voluntary.chat.server.mapper.AiMemoryMapper;
import com.voluntary.chat.server.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 记忆服务
 * 实现长期记忆的存储、检索和摘要生成
 */
@Slf4j
@RequiredArgsConstructor
public class AiMemoryService {

    // 常量定义
    private static final double SUMMARY_TEMPERATURE = 0.3;
    private static final int SUMMARY_MAX_TOKENS = 500;
    private static final int MIN_KEYWORD_LENGTH = 2;
    private static final int MAX_KEYWORD_COUNT = 5;
    private static final double DEFAULT_IMPORTANCE = 0.5;
    private static final double MEMORY_THRESHOLD_FACTOR = 0.5;

    private final AiMemoryMapper aiMemoryMapper;
    private final MessageMapper messageMapper;
    private final AiService aiService;
    private final EmbeddingClient embeddingClient;
    private final VectorStoreClient vectorStoreClient;
    private final OpenAiClient openAiClient;
    private final AiConfig aiConfig;
    private final StringRedisTemplate redisTemplate;
    private final CacheProperties cacheProperties;

    private static final String AI_MEMORY_PREFIX = "ai_memory:";

    /**
     * 获取 AI 记忆列表
     */
    public PageResult<AiMemory> getMemories(final Long aiId, final Long userId, final Integer page,
            final Integer size) {
        final LambdaQueryWrapper<AiMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiMemory::getAiId, aiId)
                .eq(AiMemory::getUserId, userId)
                .orderByDesc(AiMemory::getCreateTime);

        final long total = aiMemoryMapper.selectCount(wrapper);
        final int offset = (page - 1) * size;
        wrapper.last("LIMIT " + size + " OFFSET " + offset);

        final List<AiMemory> memories = aiMemoryMapper.selectList(wrapper);

        return PageResult.<AiMemory>builder()
                .list(memories)
                .total(total)
                .page(page)
                .size(size)
                .build();
    }

    /**
     * 检索相关记忆（向量检索 + 关键词降级）
     *
     * <p>
     * 优先查 Redis 缓存，命中则直接返回；未命中查库并回填。
     * </p>
     */
    public List<String> searchRelevantMemories(final Long aiId, final Long userId, final String query) {
        // 1. 先查缓存
        if (cacheProperties.isEnabled()) {
            try {
                final String cacheKey = aiMemoryKey(aiId, userId);
                final List<String> cached = redisTemplate.opsForList()
                        .range(cacheKey, 0, -1);
                if (!CollectionUtils.isEmpty(cached)) {
                    log.debug("AI 记忆缓存命中: aiId={}, userId={}", aiId, userId);
                    return cached;
                }
            } catch (Exception e) {
                log.warn("AI 记忆缓存查询失败，降级查库: aiId={}, userId={}", aiId, userId, e);
            }
        }

        // 2. 查库（向量检索 + 关键词降级）
        final AiProfile profile = aiService.getAiProfileById(aiId);
        final String apiKey = aiService.decryptApiKey(profile);

        final List<String> results = searchFromDatabase(aiId, userId, query, apiKey);

        // 3. 回填缓存
        if (!CollectionUtils.isEmpty(results) && cacheProperties.isEnabled()) {
            try {
                final String cacheKey = aiMemoryKey(aiId, userId);
                redisTemplate.delete(cacheKey);
                for (final String summary : results) {
                    redisTemplate.opsForList().rightPush(cacheKey, summary);
                }
                final long ttl = cacheProperties.getAiMemory().getTtl();
                if (ttl > 0) {
                    redisTemplate.expire(cacheKey, ttl, java.util.concurrent.TimeUnit.SECONDS);
                }
            } catch (Exception e) {
                log.warn("AI 记忆缓存回填失败: aiId={}, userId={}", aiId, userId, e);
            }
        }

        return results;
    }

    /**
     * 从数据库检索相关记忆（向量检索优先，关键词降级）
     */
    private List<String> searchFromDatabase(final Long aiId, final Long userId, final String query,
            final String apiKey) {

        // 1. 尝试向量检索
        final List<Double> queryVector = embeddingClient.createEmbedding(query, apiKey);
        if (queryVector != null && !queryVector.isEmpty()) {
            final AiConfig.VectorConfig vectorConfig = aiConfig.getVector();
            if (vectorConfig.getEnabled()) {
                final VectorStoreClient.VectorSearchConfig searchConfig = new VectorStoreClient.VectorSearchConfig(
                        queryVector,
                        aiId,
                        userId,
                        aiConfig.getContext().getMaxMemoryCount(),
                        aiConfig.getContext().getMemorySimilarityThreshold());
                final List<Map<String, Object>> results = vectorStoreClient.searchSimilar(searchConfig);

                if (!results.isEmpty()) {
                    log.debug("向量检索找到 {} 条相关记忆", results.size());
                    return results.stream()
                            .map(r -> (String) r.get("summary"))
                            .collect(Collectors.toList());
                }
            }
        }

        // 2. 降级为关键词匹配
        log.debug("向量检索无结果，降级为关键词匹配");
        return searchByKeywords(aiId, userId, query);
    }

    /**
     * 关键词匹配检索（降级方案）
     */
    private List<String> searchByKeywords(final Long aiId, final Long userId, final String query) {
        // 提取查询关键词（简单实现：按空格分割）
        final String[] keywords = query.split("\\s+");

        final LambdaQueryWrapper<AiMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiMemory::getAiId, aiId)
                .eq(AiMemory::getUserId, userId);

        // 关键词模糊匹配
        for (final String keyword : keywords) {
            if (keyword.length() > MIN_KEYWORD_LENGTH) {
                wrapper.or(w -> w.like(AiMemory::getKeywords, keyword)
                        .or()
                        .like(AiMemory::getSummary, keyword));
            }
        }

        wrapper.orderByDesc(AiMemory::getCreateTime)
                .last("LIMIT " + aiConfig.getContext().getMaxMemoryCount());

        final List<AiMemory> memories = aiMemoryMapper.selectList(wrapper);

        return memories.stream()
                .map(AiMemory::getSummary)
                .collect(Collectors.toList());
    }

    /**
     * 检查并生成记忆摘要
     *
     * <p>
     * 生成新摘要后，清除该 AI+用户的缓存。
     * </p>
     */
    @Transactional
    public void summarizeIfNeeded(final Long aiId, final Long userId, final String sessionId) {
        // 查询当前对话轮数
        final LambdaQueryWrapper<Message> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(Message::getSessionId, sessionId)
                .eq(Message::getSenderType, 0) // 用户消息
                .isNull(Message::getRecallTime);

        final long userMessageCount = messageMapper.selectCount(countWrapper);
        final int threshold = aiConfig.getMemory().getSummarizeThreshold();

        // 检查是否达到摘要阈值
        final long memoryCount = countMemories(aiId, userId);
        final long expectedMemories = userMessageCount / threshold;

        if (memoryCount >= expectedMemories) {
            log.debug("记忆摘要已足够: aiId={}, userId={}, memoryCount={}", aiId, userId, memoryCount);
            return;
        }

        // 获取最近 N 轮对话用于摘要（使用独立 wrapper 避免条件污染）
        final int startOffset = (int) (memoryCount * threshold);
        final LambdaQueryWrapper<Message> listWrapper = new LambdaQueryWrapper<>();
        listWrapper.eq(Message::getSessionId, sessionId)
                .isNull(Message::getRecallTime)
                .orderByAsc(Message::getCreateTime)
                .last("LIMIT " + threshold + " OFFSET " + startOffset);
        final List<Message> messages = messageMapper.selectList(listWrapper);

        if (messages.isEmpty()) {
            return;
        }

        // 构建对话文本
        final StringBuilder conversationText = new StringBuilder();
        for (final Message msg : messages) {
            conversationText.append(msg.getSenderType() == 0 ? "用户：" : "AI：");
            conversationText.append(msg.getContent());
            conversationText.append("\n");
        }

        // 调用 AI 生成摘要
        final AiProfile profile = aiService.getAiProfileById(aiId);
        final String apiKey = aiService.decryptApiKey(profile);
        final String baseUrl = openAiClient.getBaseUrl(profile.getModelProvider());

        final String summary = generateSummary(baseUrl, apiKey, profile.getModel(), conversationText.toString());

        if (summary == null || summary.isEmpty()) {
            log.warn("摘要生成失败: aiId={}, userId={}", aiId, userId);
            return;
        }

        // 提取关键词
        final String keywords = extractKeywords(summary);

        // 生成 Embedding
        final List<Double> vector = embeddingClient.createEmbedding(summary, apiKey);

        // 存储记忆
        final AiMemory memory = new AiMemory();
        memory.setAiId(aiId);
        memory.setUserId(userId);
        memory.setSummary(summary);
        memory.setKeywords(keywords);
        memory.setImportance(DEFAULT_IMPORTANCE);
        memory.setIsDeleted(0);

        aiMemoryMapper.insert(memory);

        // 存储向量
        if (vector != null && !vector.isEmpty()) {
            final VectorStoreClient.VectorStoreConfig storeConfig = new VectorStoreClient.VectorStoreConfig(
                    memory.getId(),
                    vector,
                    summary,
                    aiId,
                    userId);
            vectorStoreClient.storeVector(storeConfig);
        }

        log.info("记忆摘要生成成功: aiId={}, userId={}, memoryId={}", aiId, userId, memory.getId());

        // 清除缓存，下次检索将重新加载最新记忆
        invalidateMemoryCache(aiId, userId);
    }

    /**
     * 清除 AI 记忆缓存
     */
    private void invalidateMemoryCache(final Long aiId, final Long userId) {
        if (!cacheProperties.isEnabled()) {
            return;
        }
        try {
            redisTemplate.delete(aiMemoryKey(aiId, userId));
        } catch (Exception e) {
            log.warn("AI 记忆缓存清除失败: aiId={}, userId={}", aiId, userId, e);
        }
    }

    private String aiMemoryKey(final Long aiId, final Long userId) {
        return AI_MEMORY_PREFIX + aiId + ":" + userId;
    }

    /**
     * 调用 AI 生成摘要
     */
    private String generateSummary(
            final String baseUrl,
            final String apiKey,
            final String model,
            final String conversationText) {

        final List<Map<String, String>> messages = new ArrayList<>();

        final Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content",
                "请总结以下对话的关键信息，提取用户偏好、兴趣或重要事实。输出格式：一句话总结，不超过" + aiConfig.getMemory().getMaxSummaryLength() + "字。");
        messages.add(systemMessage);

        final Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", conversationText);
        messages.add(userMessage);

        try {
            final OpenAiClient.ChatConfig chatConfig = new OpenAiClient.ChatConfig(
                    baseUrl,
                    apiKey,
                    model,
                    messages,
                    SUMMARY_TEMPERATURE,
                    SUMMARY_MAX_TOKENS);
            return openAiClient.chatCompletion(chatConfig);
        } catch (final RuntimeException e) {
            log.error("摘要生成失败", e);
            return null;
        }
    }

    /**
     * 提取关键词（简单实现：取前5个重要词）
     */
    private String extractKeywords(final String summary) {
        // 简单实现：按空格和标点分割，取前5个词
        final String[] words = summary.split("[\\s,，。.!！?？;；：:\"\"''()（）]+");
        final List<String> keywords = new ArrayList<>();
        for (final String word : words) {
            if (word.length() > MIN_KEYWORD_LENGTH && keywords.size() < MAX_KEYWORD_COUNT) {
                keywords.add(word);
            }
        }
        return keywords.stream().collect(Collectors.joining(","));
    }

    /**
     * 统计记忆数量
     */
    private long countMemories(final Long aiId, final Long userId) {
        final LambdaQueryWrapper<AiMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiMemory::getAiId, aiId)
                .eq(AiMemory::getUserId, userId);
        return aiMemoryMapper.selectCount(wrapper);
    }

    /**
     * 删除记忆
     */
    @Transactional
    public void deleteMemory(final Long memoryId, final Long userId) {
        final AiMemory memory = aiMemoryMapper.selectById(memoryId);
        if (memory == null) {
            return;
        }

        // 验证权限
        if (!memory.getUserId().equals(userId)) {
            log.warn("无权删除记忆: memoryId={}, userId={}", memoryId, userId);
            return;
        }

        // 删除向量
        vectorStoreClient.deleteVector(memoryId);

        // 删除数据库记录
        aiMemoryMapper.deleteById(memoryId);

        log.info("记忆删除成功: memoryId={}", memoryId);

        // 清除缓存
        invalidateMemoryCache(memory.getAiId(), userId);
    }
}