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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AI 记忆服务测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AiMemoryService 测试")
class AiMemoryServiceTest {

    private AiMemoryService aiMemoryService;

    @Mock
    private AiMemoryMapper aiMemoryMapper;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private AiService aiService;

    @Mock
    private EmbeddingClient embeddingClient;

    @Mock
    private VectorStoreClient vectorStoreClient;

    @Mock
    private OpenAiClient openAiClient;

    @Mock
    private AiConfig aiConfig;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private CacheProperties cacheProperties;

    private static final Long AI_ID = 3001L;
    private static final Long USER_ID = 1001L;
    private static final Long MEMORY_ID = 5001L;

    @Mock
    private ListOperations<String, String> listOperations;

    private static final String CACHE_KEY = "ai_memory:3001:1001";

    @BeforeEach
    void setUp() {
        aiMemoryService = new AiMemoryService(
                aiMemoryMapper, messageMapper, aiService,
                embeddingClient, vectorStoreClient, openAiClient, aiConfig,
                redisTemplate, cacheProperties);

        // 默认禁用缓存，确保现有测试走查库逻辑
        when(cacheProperties.isEnabled()).thenReturn(false);

        final AiConfig.ContextConfig contextConfig = new AiConfig.ContextConfig();
        contextConfig.setMaxMemoryCount(3);
        contextConfig.setMemorySimilarityThreshold(0.7);

        final AiConfig.MemoryConfig memoryConfig = new AiConfig.MemoryConfig();
        memoryConfig.setSummarizeThreshold(20);
        memoryConfig.setMaxSummaryLength(500);

        final AiConfig.VectorConfig vectorConfig = new AiConfig.VectorConfig();
        vectorConfig.setEnabled(false);

        when(aiConfig.getContext()).thenReturn(contextConfig);
        when(aiConfig.getMemory()).thenReturn(memoryConfig);
        when(aiConfig.getVector()).thenReturn(vectorConfig);
    }

    @Test
    @DisplayName("获取记忆列表 - 成功")
    void getMemories_shouldReturnList() {
        final AiMemory memory1 = createMemory(MEMORY_ID, "用户喜欢编程");
        final AiMemory memory2 = createMemory(MEMORY_ID + 1, "用户喜欢音乐");

        when(aiMemoryMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);
        when(aiMemoryMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(memory1, memory2));

        final PageResult<AiMemory> result = aiMemoryService.getMemories(AI_ID, USER_ID, 1, 10);

        assertEquals(2, result.getList().size());
        assertEquals(2L, result.getTotal());
    }

    @Test
    @DisplayName("检索相关记忆 - 向量检索成功")
    void searchRelevantMemories_shouldUseVectorSearch() {
        final AiProfile profile = createAiProfile();
        final List<Double> vector = List.of(0.1, 0.2, 0.3);
        final List<Map<String, Object>> searchResults = List.of(
                Map.of("id", MEMORY_ID, "score", 0.8, "summary", "用户喜欢编程"));

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(embeddingClient.createEmbedding(anyString(), anyString())).thenReturn(vector);
        when(vectorStoreClient.searchSimilar(any(VectorStoreClient.VectorSearchConfig.class)))
                .thenReturn(searchResults);

        final AiConfig.VectorConfig vectorConfig = new AiConfig.VectorConfig();
        vectorConfig.setEnabled(true);
        when(aiConfig.getVector()).thenReturn(vectorConfig);

        final List<String> memories = aiMemoryService.searchRelevantMemories(AI_ID, USER_ID, "编程");

        assertEquals(1, memories.size());
        assertEquals("用户喜欢编程", memories.get(0));
    }

    @Test
    @DisplayName("检索相关记忆 - 降级为关键词匹配")
    void searchRelevantMemories_shouldFallbackToKeywords() {
        final AiProfile profile = createAiProfile();
        final AiMemory memory = createMemory(MEMORY_ID, "用户喜欢编程");
        memory.setKeywords("编程,技术");

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(embeddingClient.createEmbedding(anyString(), anyString())).thenReturn(null);
        when(aiMemoryMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(memory));

        final List<String> memories = aiMemoryService.searchRelevantMemories(AI_ID, USER_ID, "编程");

        assertEquals(1, memories.size());
        assertEquals("用户喜欢编程", memories.get(0));
    }

    @Test
    @DisplayName("删除记忆 - 成功")
    void deleteMemory_shouldSucceed() {
        final AiMemory memory = createMemory(MEMORY_ID, "测试记忆");
        memory.setUserId(USER_ID);

        when(aiMemoryMapper.selectById(MEMORY_ID)).thenReturn(memory);
        when(vectorStoreClient.deleteVector(MEMORY_ID)).thenReturn(true);
        when(aiMemoryMapper.deleteById(anyLong())).thenReturn(1);

        aiMemoryService.deleteMemory(MEMORY_ID, USER_ID);

        verify(aiMemoryMapper).deleteById(MEMORY_ID);
        verify(vectorStoreClient).deleteVector(MEMORY_ID);
    }

    @Test
    @DisplayName("删除记忆 - 无权限")
    void deleteMemory_shouldFail_withoutPermission() {
        final AiMemory memory = createMemory(MEMORY_ID, "测试记忆");
        memory.setUserId(USER_ID + 1); // 其他用户

        when(aiMemoryMapper.selectById(MEMORY_ID)).thenReturn(memory);

        aiMemoryService.deleteMemory(MEMORY_ID, USER_ID);

        verify(aiMemoryMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("获取记忆列表 - 空列表")
    void getMemories_shouldReturnEmptyList() {
        when(aiMemoryMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(aiMemoryMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of());

        final PageResult<AiMemory> result = aiMemoryService.getMemories(AI_ID, USER_ID, 1, 10);

        assertEquals(0, result.getList().size());
        assertEquals(0L, result.getTotal());
    }

    @Test
    @DisplayName("检索相关记忆 - 空查询")
    void searchRelevantMemories_shouldReturnEmptyList_withEmptyQuery() {
        final List<String> memories = aiMemoryService.searchRelevantMemories(AI_ID, USER_ID, "");

        assertEquals(0, memories.size());
    }

    @Test
    @DisplayName("检索相关记忆 - 向量检索失败降级")
    void searchRelevantMemories_shouldFallback_whenVectorSearchFails() {
        final AiProfile profile = createAiProfile();
        final AiMemory memory = createMemory(MEMORY_ID, "用户喜欢编程");

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(embeddingClient.createEmbedding(anyString(), anyString())).thenReturn(null);
        when(aiMemoryMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(memory));

        final AiConfig.VectorConfig vectorConfig = new AiConfig.VectorConfig();
        vectorConfig.setEnabled(true);
        when(aiConfig.getVector()).thenReturn(vectorConfig);

        final List<String> memories = aiMemoryService.searchRelevantMemories(AI_ID, USER_ID, "编程");

        assertEquals(1, memories.size());
    }

    @Test
    @DisplayName("存储记忆 - 向量存储禁用")
    void storeMemory_shouldSkipVector_whenDisabled() {
        final AiProfile profile = createAiProfile();

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiMemoryMapper.insert(any(AiMemory.class))).thenReturn(1);

        // 向量存储禁用时不调用向量存储
        verify(vectorStoreClient, never()).storeVector(any(VectorStoreClient.VectorStoreConfig.class));
    }

    @Test
    @DisplayName("检查并生成摘要 - 达到阈值")
    void summarizeIfNeeded_shouldGenerateSummary() {
        final AiProfile profile = createAiProfile();
        final List<Message> messages = List.of(
                createMessage("你好", 0),
                createMessage("你好！有什么可以帮助你的？", 1),
                createMessage("介绍一下Java", 0));

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(messageMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(25L);
        when(aiMemoryMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(messages);
        when(openAiClient.getBaseUrl(anyString())).thenReturn("https://api.openai.com/v1");
        when(openAiClient.chatCompletion(any(OpenAiClient.ChatConfig.class)))
                .thenReturn("用户询问了Java相关内容");
        when(embeddingClient.createEmbedding(anyString(), anyString())).thenReturn(List.of(0.1, 0.2, 0.3));
        when(aiMemoryMapper.insert(any(AiMemory.class))).thenReturn(1);

        aiMemoryService.summarizeIfNeeded(AI_ID, USER_ID, "session-test-001");

        verify(aiMemoryMapper).insert(any(AiMemory.class));
    }

    // ==================== 缓存专用测试 ====================

    @Test
    @DisplayName("检索相关记忆 - 缓存命中直接返回")
    void searchRelevantMemories_shouldReturnCached_whenHit() {
        when(cacheProperties.isEnabled()).thenReturn(true);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range(CACHE_KEY, 0, -1)).thenReturn(List.of("用户喜欢编程", "用户喜欢音乐"));

        final List<String> result = aiMemoryService.searchRelevantMemories(AI_ID, USER_ID, "编程");

        assertEquals(2, result.size());
        assertEquals("用户喜欢编程", result.get(0));
        // 不应查库
        verify(aiMemoryMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("检索相关记忆 - 缓存未命中时查库并回填")
    void searchRelevantMemories_shouldQueryDbAndPopulateCache_whenMiss() {
        when(cacheProperties.isEnabled()).thenReturn(true);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range(CACHE_KEY, 0, -1)).thenReturn(null);

        final AiProfile profile = createAiProfile();
        final AiMemory memory = createMemory(MEMORY_ID, "用户喜欢编程");

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(embeddingClient.createEmbedding(anyString(), anyString())).thenReturn(null);
        when(aiMemoryMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(memory));

        final CacheProperties.CacheItem aiMemoryConfig = mock(CacheProperties.CacheItem.class);
        when(cacheProperties.getAiMemory()).thenReturn(aiMemoryConfig);
        when(aiMemoryConfig.getTtl()).thenReturn(1800L);

        final List<String> result = aiMemoryService.searchRelevantMemories(AI_ID, USER_ID, "编程");

        assertEquals(1, result.size());
        verify(redisTemplate).delete(CACHE_KEY);
        verify(listOperations).rightPush(eq(CACHE_KEY), eq("用户喜欢编程"));
        verify(redisTemplate).expire(eq(CACHE_KEY), eq(1800L), any());
    }

    @Test
    @DisplayName("检索相关记忆 - Redis 异常降级查库")
    void searchRelevantMemories_shouldDegrade_whenRedisFails() {
        when(cacheProperties.isEnabled()).thenReturn(true);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range(CACHE_KEY, 0, -1)).thenThrow(new RuntimeException("Redis 连接失败"));

        final AiProfile profile = createAiProfile();
        final AiMemory memory = createMemory(MEMORY_ID, "用户喜欢编程");

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(embeddingClient.createEmbedding(anyString(), anyString())).thenReturn(null);
        when(aiMemoryMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(memory));

        final List<String> result = aiMemoryService.searchRelevantMemories(AI_ID, USER_ID, "编程");

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("生成摘要后清除缓存")
    void summarizeIfNeeded_shouldInvalidateCache() {
        when(cacheProperties.isEnabled()).thenReturn(true);

        final AiProfile profile = createAiProfile();
        final List<Message> messages = List.of(
                createMessage("你好", 0),
                createMessage("你好！有什么可以帮助你的？", 1),
                createMessage("介绍一下Java", 0));

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(messageMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(25L);
        when(aiMemoryMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(messages);
        when(openAiClient.getBaseUrl(anyString())).thenReturn("https://api.openai.com/v1");
        when(openAiClient.chatCompletion(any(OpenAiClient.ChatConfig.class)))
                .thenReturn("用户询问了Java相关内容");
        when(embeddingClient.createEmbedding(anyString(), anyString())).thenReturn(List.of(0.1, 0.2, 0.3));
        when(aiMemoryMapper.insert(any(AiMemory.class))).thenReturn(1);

        aiMemoryService.summarizeIfNeeded(AI_ID, USER_ID, "session-test-001");

        // 生成摘要后应清除缓存
        verify(redisTemplate).delete(CACHE_KEY);
    }

    @Test
    @DisplayName("删除记忆后清除缓存")
    void deleteMemory_shouldInvalidateCache() {
        when(cacheProperties.isEnabled()).thenReturn(true);

        final AiMemory memory = createMemory(MEMORY_ID, "测试记忆");
        memory.setAiId(AI_ID);
        memory.setUserId(USER_ID);

        when(aiMemoryMapper.selectById(MEMORY_ID)).thenReturn(memory);
        when(vectorStoreClient.deleteVector(MEMORY_ID)).thenReturn(true);
        when(aiMemoryMapper.deleteById(anyLong())).thenReturn(1);

        aiMemoryService.deleteMemory(MEMORY_ID, USER_ID);

        verify(redisTemplate).delete(CACHE_KEY);
    }

    @Test
    @DisplayName("缓存回填异常不中断主流程")
    void populateCache_shouldNotThrow_whenRedisFails() {
        when(cacheProperties.isEnabled()).thenReturn(true);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range(CACHE_KEY, 0, -1)).thenReturn(null);

        final AiProfile profile = createAiProfile();
        final AiMemory memory = createMemory(MEMORY_ID, "用户喜欢编程");

        when(aiService.getAiProfileById(AI_ID)).thenReturn(profile);
        when(aiService.decryptApiKey(profile)).thenReturn("sk-test-key");
        when(embeddingClient.createEmbedding(anyString(), anyString())).thenReturn(null);
        when(aiMemoryMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(memory));

        // 回填缓存时抛出异常
        doThrow(new RuntimeException("Redis 不可用")).when(redisTemplate).delete(CACHE_KEY);

        final List<String> result = aiMemoryService.searchRelevantMemories(AI_ID, USER_ID, "编程");

        assertEquals(1, result.size());
        assertEquals("用户喜欢编程", result.get(0));
    }

    private AiProfile createAiProfile() {
        final AiProfile profile = new AiProfile();
        profile.setId(AI_ID);
        profile.setName("小助手");
        profile.setModelProvider("openai");
        profile.setModel("gpt-4");
        return profile;
    }

    private AiMemory createMemory(final Long id, final String summary) {
        final AiMemory memory = new AiMemory();
        memory.setId(id);
        memory.setAiId(AI_ID);
        memory.setUserId(USER_ID);
        memory.setSummary(summary);
        memory.setKeywords("关键词");
        memory.setImportance(0.5);
        return memory;
    }

    private Message createMessage(final String content, final int senderType) {
        final Message message = new Message();
        message.setContent(content);
        message.setSenderType(senderType);
        return message;
    }
}