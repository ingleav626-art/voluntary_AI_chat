package com.voluntary.chat.server.service;

import com.voluntary.chat.server.config.CacheProperties;
import com.voluntary.chat.server.service.ConversationCacheService.LastMessageCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ConversationCacheService 单元测试")
class ConversationCacheServiceTest {

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private CacheProperties cacheProperties;

  @Mock
  private CacheProperties.CacheItem conversationCacheItem;

  @Mock
  private CacheProperties.CacheItem unreadCacheItem;

  @Mock
  private ValueOperations<String, String> valueOperations;

  @Mock
  private ZSetOperations<String, String> zSetOperations;

  private static final String SESSION_ID = "p_1001_1002";
  private static final Long USER_ID = 1001L;

  private ConversationCacheService createService(boolean enabled) {
    when(cacheProperties.isEnabled()).thenReturn(enabled);
    when(cacheProperties.getConversation()).thenReturn(conversationCacheItem);
    when(cacheProperties.getUnread()).thenReturn(unreadCacheItem);
    when(conversationCacheItem.getTtl()).thenReturn(86400L);
    when(unreadCacheItem.getTtl()).thenReturn(604800L);
    return new ConversationCacheService(redisTemplate, cacheProperties);
  }

  // ==================== 最后一条消息 ====================

  @Test
  @DisplayName("缓存最后一条消息 - 成功")
  void setLastMessage_shouldSucceed() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    ConversationCacheService cacheService = createService(true);
    LastMessageCache cache = new LastMessageCache("你好", 0, LocalDateTime.now(), 1001L);

    cacheService.setLastMessage(SESSION_ID, cache);

    verify(valueOperations).set(
        eq("conv:last_msg:p_1001_1002"),
        anyString(),
        eq(86400L),
        eq(TimeUnit.SECONDS));
  }

  @Test
  @DisplayName("缓存最后一条消息 - 缓存禁用时不写入")
  void setLastMessage_shouldNotWrite_whenDisabled() {
    ConversationCacheService cacheService = createService(false);
    LastMessageCache cache = new LastMessageCache("你好", 0, LocalDateTime.now(), 1001L);

    cacheService.setLastMessage(SESSION_ID, cache);

    verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
  }

  @Test
  @DisplayName("缓存最后一条消息 - Redis 异常不抛出")
  void setLastMessage_shouldNotThrow_whenRedisFails() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    doThrow(new RuntimeException("Redis 失败"))
        .when(valueOperations).set(anyString(), anyString(), anyLong(), any());
    ConversationCacheService cacheService = createService(true);
    LastMessageCache cache = new LastMessageCache("你好", 0, LocalDateTime.now(), 1001L);

    assertDoesNotThrow(() -> cacheService.setLastMessage(SESSION_ID, cache));
  }

  @Test
  @DisplayName("获取最后一条消息 - 命中返回缓存值")
  void getLastMessage_shouldReturn_whenHit() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    LocalDateTime now = LocalDateTime.now();
    String json = "{\"content\":\"你好\",\"type\":0,\"createTime\":\"" + now + "\",\"senderId\":1001}";
    when(valueOperations.get("conv:last_msg:p_1001_1002")).thenReturn(json);
    ConversationCacheService cacheService = createService(true);

    LastMessageCache result = cacheService.getLastMessage(SESSION_ID);

    assertNotNull(result);
    assertEquals("你好", result.content());
    assertEquals(0, result.type());
    assertEquals(1001L, result.senderId());
  }

  @Test
  @DisplayName("获取最后一条消息 - 未命中返回 null")
  void getLastMessage_shouldReturnNull_whenMiss() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("conv:last_msg:p_1001_1002")).thenReturn(null);
    ConversationCacheService cacheService = createService(true);

    LastMessageCache result = cacheService.getLastMessage(SESSION_ID);

    assertNull(result);
  }

  @Test
  @DisplayName("获取最后一条消息 - 禁用时返回 null")
  void getLastMessage_shouldReturnNull_whenDisabled() {
    ConversationCacheService cacheService = createService(false);

    assertNull(cacheService.getLastMessage(SESSION_ID));
  }

  @Test
  @DisplayName("获取最后一条消息 - JSON 异常时返回 null")
  void getLastMessage_shouldReturnNull_whenJsonInvalid() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("conv:last_msg:p_1001_1002")).thenReturn("{invalid json}");
    ConversationCacheService cacheService = createService(true);

    LastMessageCache result = cacheService.getLastMessage(SESSION_ID);

    assertNull(result);
  }

  @Test
  @DisplayName("删除最后一条消息缓存 - 成功")
  void deleteLastMessage_shouldSucceed() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    ConversationCacheService cacheService = createService(true);

    cacheService.deleteLastMessage(SESSION_ID);

    verify(redisTemplate).delete("conv:last_msg:p_1001_1002");
  }

  // ==================== 未读计数 ====================

  @Test
  @DisplayName("增加未读数 - 成功")
  void incrementUnread_shouldSucceed() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    ConversationCacheService cacheService = createService(true);

    cacheService.incrementUnread(USER_ID, SESSION_ID);

    verify(valueOperations).increment("conv:unread:1001:p_1001_1002", 1);
    verify(redisTemplate).expire("conv:unread:1001:p_1001_1002", 604800L, TimeUnit.SECONDS);
  }

  @Test
  @DisplayName("增加未读数 - 禁用时跳过")
  void incrementUnread_shouldSkip_whenDisabled() {
    ConversationCacheService cacheService = createService(false);

    cacheService.incrementUnread(USER_ID, SESSION_ID);

    verify(valueOperations, never()).increment(anyString(), anyLong());
  }

  @Test
  @DisplayName("清零未读数 - 成功")
  void clearUnread_shouldSucceed() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    ConversationCacheService cacheService = createService(true);

    cacheService.clearUnread(USER_ID, SESSION_ID);

    verify(valueOperations).set("conv:unread:1001:p_1001_1002", "0");
  }

  @Test
  @DisplayName("清零未读数 - 禁用时跳过")
  void clearUnread_shouldSkip_whenDisabled() {
    ConversationCacheService cacheService = createService(false);

    cacheService.clearUnread(USER_ID, SESSION_ID);

    verify(valueOperations, never()).set(anyString(), anyString());
  }

  @Test
  @DisplayName("获取未读数 - 命中时返回计数值")
  void getUnread_shouldReturnCount_whenHit() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("conv:unread:1001:p_1001_1002")).thenReturn("5");
    ConversationCacheService cacheService = createService(true);

    assertEquals(5L, cacheService.getUnread(USER_ID, SESSION_ID));
  }

  @Test
  @DisplayName("获取未读数 - 未命中返回 -1")
  void getUnread_shouldReturnMinusOne_whenMiss() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("conv:unread:1001:p_1001_1002")).thenReturn(null);
    ConversationCacheService cacheService = createService(true);

    assertEquals(-1L, cacheService.getUnread(USER_ID, SESSION_ID));
  }

  @Test
  @DisplayName("获取未读数 - 禁用时返回 -1")
  void getUnread_shouldReturnMinusOne_whenDisabled() {
    ConversationCacheService cacheService = createService(false);

    assertEquals(-1L, cacheService.getUnread(USER_ID, SESSION_ID));
  }

  @Test
  @DisplayName("获取未读数 - 异常时返回 -1")
  void getUnread_shouldReturnMinusOne_whenException() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis 异常"));
    ConversationCacheService cacheService = createService(true);

    assertEquals(-1L, cacheService.getUnread(USER_ID, SESSION_ID));
  }

  // ==================== 会话列表（ZSet） ====================

  @Test
  @DisplayName("更新会话列表 - 成功")
  void updateSessionList_shouldSucceed() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    ConversationCacheService cacheService = createService(true);
    LocalDateTime time = LocalDateTime.of(2026, 6, 27, 12, 0, 0);

    cacheService.updateSessionList(USER_ID, SESSION_ID, time);

    verify(zSetOperations).add(eq("conv:list:1001"), eq("p_1001_1002"), anyDouble());
    verify(redisTemplate).expire("conv:list:1001", 86400L, TimeUnit.SECONDS);
  }

  @Test
  @DisplayName("更新会话列表 - null 时间用当前时间")
  void updateSessionList_shouldUseCurrentTime_whenNull() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    ConversationCacheService cacheService = createService(true);

    cacheService.updateSessionList(USER_ID, SESSION_ID, null);

    verify(zSetOperations).add(eq("conv:list:1001"), eq("p_1001_1002"), anyDouble());
  }

  @Test
  @DisplayName("更新会话列表 - 禁用时跳过")
  void updateSessionList_shouldSkip_whenDisabled() {
    ConversationCacheService cacheService = createService(false);

    cacheService.updateSessionList(USER_ID, SESSION_ID, LocalDateTime.now());

    verify(zSetOperations, never()).add(anyString(), anyString(), anyDouble());
  }

  @Test
  @DisplayName("删除会话列表条目 - 成功")
  void removeSessionFromList_shouldSucceed() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    ConversationCacheService cacheService = createService(true);

    cacheService.removeSessionFromList(USER_ID, SESSION_ID);

    verify(zSetOperations).remove("conv:list:1001", "p_1001_1002");
  }

  @Test
  @DisplayName("删除会话列表条目 - 禁用时跳过")
  void removeSessionFromList_shouldSkip_whenDisabled() {
    ConversationCacheService cacheService = createService(false);

    cacheService.removeSessionFromList(USER_ID, SESSION_ID);

    verify(zSetOperations, never()).remove(anyString(), anyString());
  }

  // ==================== 批量删除 ====================

  @Test
  @DisplayName("删除会话所有缓存 - 调用三个子方法")
  void deleteConversationCache_shouldCallSubMethods() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    ConversationCacheService cacheService = createService(true);

    cacheService.deleteConversationCache(USER_ID, SESSION_ID);

    verify(redisTemplate).delete("conv:last_msg:p_1001_1002");
    verify(valueOperations).set("conv:unread:1001:p_1001_1002", "0");
    verify(zSetOperations).remove("conv:list:1001", "p_1001_1002");
  }

  @Test
  @DisplayName("删除会话所有缓存 - 禁用时跳过所有")
  void deleteConversationCache_shouldSkip_whenDisabled() {
    ConversationCacheService cacheService = createService(false);

    cacheService.deleteConversationCache(USER_ID, SESSION_ID);

    verify(redisTemplate, never()).delete(anyString());
    verify(valueOperations, never()).set(anyString(), anyString());
    verify(zSetOperations, never()).remove(anyString(), anyString());
  }

  // ==================== 离线消息队列 ====================

  @Test
  @DisplayName("推送离线消息 - 成功")
  void pushOfflineMessage_shouldSucceed() {
    ListOperations ops = mock(ListOperations.class);
    when(redisTemplate.opsForList()).thenReturn(ops);
    ConversationCacheService cacheService = createService(true);

    cacheService.pushOfflineMessage(USER_ID, "{\"content\":\"msg1\"}");

    verify(ops).leftPush("offline:msg:1001", "{\"content\":\"msg1\"}");
  }

  @Test
  @DisplayName("推送离线消息 - 禁用时跳过")
  void pushOfflineMessage_shouldSkip_whenDisabled() {
    ConversationCacheService cacheService = createService(false);

    cacheService.pushOfflineMessage(USER_ID, "{\"content\":\"msg1\"}");

    verify(redisTemplate, never()).opsForList();
  }

  @Test
  @DisplayName("推送离线消息 - 异常不抛出")
  void pushOfflineMessage_shouldNotThrow_whenException() {
    when(redisTemplate.opsForList()).thenThrow(new RuntimeException("Redis 失败"));
    ConversationCacheService cacheService = createService(true);

    assertDoesNotThrow(() -> cacheService.pushOfflineMessage(USER_ID, "{\"content\":\"msg1\"}"));
  }

  @Test
  @DisplayName("弹出离线消息 - 成功")
  void popOfflineMessage_shouldSucceed() {
    ListOperations ops = mock(ListOperations.class);
    when(redisTemplate.opsForList()).thenReturn(ops);
    when(ops.rightPop("offline:msg:1001")).thenReturn("{\"content\":\"msg1\"}");
    ConversationCacheService cacheService = createService(true);

    String result = cacheService.popOfflineMessage(USER_ID);

    assertEquals("{\"content\":\"msg1\"}", result);
  }

  @Test
  @DisplayName("弹出离线消息 - 无消息返回 null")
  void popOfflineMessage_shouldReturnNull_whenEmpty() {
    ListOperations ops = mock(ListOperations.class);
    when(redisTemplate.opsForList()).thenReturn(ops);
    when(ops.rightPop("offline:msg:1001")).thenReturn(null);
    ConversationCacheService cacheService = createService(true);

    assertNull(cacheService.popOfflineMessage(USER_ID));
  }

  @Test
  @DisplayName("弹出离线消息 - 禁用时返回 null")
  void popOfflineMessage_shouldReturnNull_whenDisabled() {
    ConversationCacheService cacheService = createService(false);

    assertNull(cacheService.popOfflineMessage(USER_ID));
  }

  @Test
  @DisplayName("弹出离线消息 - 异常时返回 null")
  void popOfflineMessage_shouldReturnNull_whenException() {
    when(redisTemplate.opsForList()).thenThrow(new RuntimeException("Redis 失败"));
    ConversationCacheService cacheService = createService(true);

    assertNull(cacheService.popOfflineMessage(USER_ID));
  }
}