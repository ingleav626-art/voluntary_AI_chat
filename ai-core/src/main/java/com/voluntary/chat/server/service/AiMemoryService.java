package com.voluntary.chat.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voluntary.chat.common.dto.PageResult;
import com.voluntary.chat.server.client.EmbeddingClient;
import com.voluntary.chat.server.client.OpenAiClient;
import com.voluntary.chat.server.client.VectorStoreClient;
import com.voluntary.chat.server.config.AiConfig;
import com.voluntary.chat.server.entity.AiMemory;
import com.voluntary.chat.server.entity.AiProfile;
import com.voluntary.chat.server.entity.Message;
import com.voluntary.chat.server.mapper.AiMemoryMapper;
import com.voluntary.chat.server.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 记忆服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiMemoryService {

  private static final double SUMMARY_TEMPERATURE = 0.3;
  private static final int SUMMARY_MAX_TOKENS = 500;
  private static final int MIN_KEYWORD_LENGTH = 2;
  private static final int MAX_KEYWORD_COUNT = 5;
  private static final double DEFAULT_IMPORTANCE = 0.5;

  private final AiMemoryMapper aiMemoryMapper;
  private final MessageMapper messageMapper;
  private final AiService aiService;
  private final EmbeddingClient embeddingClient;
  private final VectorStoreClient vectorStoreClient;
  private final OpenAiClient openAiClient;
  private final AiConfig aiConfig;

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
   * 检索相关记忆
   */
  public List<String> searchRelevantMemories(final Long aiId, final Long userId, final String query) {
    final AiProfile profile = aiService.getAiProfileById(aiId);
    final String apiKey = aiService.decryptApiKey(profile);

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

    log.debug("向量检索无结果，降级为关键词匹配");
    return searchByKeywords(aiId, userId, query);
  }

  private List<String> searchByKeywords(final Long aiId, final Long userId, final String query) {
    final String[] keywords = query.split("\\s+");

    final LambdaQueryWrapper<AiMemory> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(AiMemory::getAiId, aiId)
        .eq(AiMemory::getUserId, userId);

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

  @Transactional
  public void summarizeIfNeeded(final Long aiId, final Long userId, final String sessionId) {
    final LambdaQueryWrapper<Message> countWrapper = new LambdaQueryWrapper<>();
    countWrapper.eq(Message::getSessionId, sessionId)
        .eq(Message::getSenderType, 0)
        .isNull(Message::getRecallTime);

    final long userMessageCount = messageMapper.selectCount(countWrapper);
    final int threshold = aiConfig.getMemory().getSummarizeThreshold();

    final long memoryCount = countMemories(aiId, userId);
    final long expectedMemories = userMessageCount / threshold;

    if (memoryCount >= expectedMemories) {
      log.debug("记忆摘要已足够: aiId={}, userId={}, memoryCount={}", aiId, userId, memoryCount);
      return;
    }

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

    final StringBuilder conversationText = new StringBuilder();
    for (final Message msg : messages) {
      conversationText.append(msg.getSenderType() == 0 ? "用户：" : "AI：");
      conversationText.append(msg.getContent());
      conversationText.append("\n");
    }

    final AiProfile profile = aiService.getAiProfileById(aiId);
    final String apiKey = aiService.decryptApiKey(profile);
    final String baseUrl = openAiClient.getBaseUrl(profile.getModelProvider());

    final String summary = generateSummary(baseUrl, apiKey, profile.getModel(), conversationText.toString());

    if (summary == null || summary.isEmpty()) {
      log.warn("摘要生成失败: aiId={}, userId={}", aiId, userId);
      return;
    }

    final String keywords = extractKeywords(summary);

    final List<Double> vector = embeddingClient.createEmbedding(summary, apiKey);

    final AiMemory memory = new AiMemory();
    memory.setAiId(aiId);
    memory.setUserId(userId);
    memory.setSummary(summary);
    memory.setKeywords(keywords);
    memory.setImportance(DEFAULT_IMPORTANCE);
    memory.setIsDeleted(0);

    aiMemoryMapper.insert(memory);

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
  }

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

  private String extractKeywords(final String summary) {
    final String[] words = summary.split("[\\s,，。.!！?？;；：:\"\"''()（）]+");
    final List<String> keywords = new ArrayList<>();
    for (final String word : words) {
      if (word.length() > MIN_KEYWORD_LENGTH && keywords.size() < MAX_KEYWORD_COUNT) {
        keywords.add(word);
      }
    }
    return keywords.stream().collect(Collectors.joining(","));
  }

  private long countMemories(final Long aiId, final Long userId) {
    final LambdaQueryWrapper<AiMemory> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(AiMemory::getAiId, aiId)
        .eq(AiMemory::getUserId, userId);
    return aiMemoryMapper.selectCount(wrapper);
  }

  @Transactional
  public void deleteMemory(final Long memoryId, final Long userId) {
    final AiMemory memory = aiMemoryMapper.selectById(memoryId);
    if (memory == null) {
      return;
    }

    if (!memory.getUserId().equals(userId)) {
      log.warn("无权删除记忆: memoryId={}, userId={}", memoryId, userId);
      return;
    }

    vectorStoreClient.deleteVector(memoryId);
    aiMemoryMapper.deleteById(memoryId);

    log.info("记忆删除成功: memoryId={}", memoryId);
  }
}
