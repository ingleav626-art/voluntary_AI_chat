package com.voluntary.chat.server.service;

import com.voluntary.chat.server.config.CacheProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OnlineStatusService 单元测试")
class OnlineStatusServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private CacheProperties cacheProperties;

    @Mock
    private CacheProperties.OnlineStatusConfig onlineStatusConfig;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private static final Long USER_ID_1 = 1001L;
    private static final Long USER_ID_2 = 1002L;
    private static final Long USER_ID_3 = 1003L;

    private OnlineStatusService createService() {
        when(cacheProperties.getOnlineStatus()).thenReturn(onlineStatusConfig);
        when(onlineStatusConfig.getTtl()).thenReturn(30L);
        return new OnlineStatusService(redisTemplate, cacheProperties);
    }

    private OnlineStatusService createServiceWithValueOps() {
        doReturn(valueOperations).when(redisTemplate).opsForValue();
        return createService();
    }

    @Test
    @DisplayName("标记上线 - 写入 Redis 并设置 TTL")
    void markOnline_shouldSetRedisKey() {
        OnlineStatusService service = createServiceWithValueOps();

        service.markOnline(USER_ID_1);

        verify(valueOperations).set("online:user:1001", "1", 30, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("标记上线 - Redis 异常时静默降级")
    void markOnline_shouldDegrade_whenRedisFails() {
        OnlineStatusService service = createServiceWithValueOps();
        doThrow(new RuntimeException("Redis 连接失败"))
                .when(valueOperations).set(anyString(), anyString(), anyLong(), any());

        assertDoesNotThrow(() -> service.markOnline(USER_ID_1));
    }

    @Test
    @DisplayName("心跳续期 - 刷新 TTL")
    void refreshHeartbeat_shouldExpireKey() {
        OnlineStatusService service = createService();

        service.refreshHeartbeat(USER_ID_1);

        verify(redisTemplate).expire("online:user:1001", 30, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("心跳续期 - Redis 异常时静默降级")
    void refreshHeartbeat_shouldDegrade_whenRedisFails() {
        OnlineStatusService service = createService();
        doThrow(new RuntimeException("Redis 连接失败"))
                .when(redisTemplate).expire(anyString(), anyLong(), any());

        assertDoesNotThrow(() -> service.refreshHeartbeat(USER_ID_1));
    }

    @Test
    @DisplayName("标记下线 - 删除 Redis Key")
    void markOffline_shouldDeleteKey() {
        OnlineStatusService service = createService();

        service.markOffline(USER_ID_1);

        verify(redisTemplate).delete("online:user:1001");
    }

    @Test
    @DisplayName("标记下线 - Redis 异常时静默降级")
    void markOffline_shouldDegrade_whenRedisFails() {
        OnlineStatusService service = createService();
        doThrow(new RuntimeException("Redis 连接失败"))
                .when(redisTemplate).delete(anyString());

        assertDoesNotThrow(() -> service.markOffline(USER_ID_1));
    }

    @Test
    @DisplayName("查询在线状态 - 在线时返回 true")
    void isOnline_shouldReturnTrue_whenOnline() {
        OnlineStatusService service = createServiceWithValueOps();
        doReturn("1").when(valueOperations).get("online:user:1001");

        assertTrue(service.isOnline(USER_ID_1));
    }

    @Test
    @DisplayName("查询在线状态 - 离线时返回 false")
    void isOnline_shouldReturnFalse_whenOffline() {
        OnlineStatusService service = createServiceWithValueOps();
        doReturn(null).when(valueOperations).get("online:user:1001");

        assertFalse(service.isOnline(USER_ID_1));
    }

    @Test
    @DisplayName("查询在线状态 - Redis 异常时降级返回 false")
    void isOnline_shouldDegrade_whenRedisFails() {
        OnlineStatusService service = createServiceWithValueOps();
        doThrow(new RuntimeException("Redis 异常")).when(valueOperations).get(anyString());

        assertFalse(service.isOnline(USER_ID_1));
    }

    @Test
    @DisplayName("批量过滤在线用户 - 返回在线用户集合")
    void filterOnline_shouldReturnOnlineUsers() {
        OnlineStatusService service = createServiceWithValueOps();
        doAnswer(invocation -> {
            List<String> keys = invocation.getArgument(0);
            return keys.stream()
                    .map(k -> k.contains("1001") || k.contains("1003") ? "1" : null)
                    .collect(java.util.stream.Collectors.toList());
        }).when(valueOperations).multiGet(anyList());

        Set<Long> onlineUsers = service.filterOnline(Set.of(USER_ID_1, USER_ID_2, USER_ID_3));

        assertEquals(Set.of(USER_ID_1, USER_ID_3), onlineUsers);
    }

    @Test
    @DisplayName("批量过滤在线用户 - 空输入返回空集合")
    void filterOnline_shouldReturnEmpty_whenEmptyInput() {
        OnlineStatusService service = createServiceWithValueOps();

        Set<Long> result = service.filterOnline(Set.of());

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("批量过滤在线用户 - Redis 异常时降级返回空集合")
    void filterOnline_shouldDegrade_whenRedisFails() {
        OnlineStatusService service = createServiceWithValueOps();
        doThrow(new RuntimeException("Redis 异常")).when(valueOperations).multiGet(anyList());

        Set<Long> result = service.filterOnline(Set.of(USER_ID_1));

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("批量查询在线状态 - 按入参顺序返回布尔列表")
    void batchCheckOnline_shouldReturnStatusList() {
        OnlineStatusService service = createServiceWithValueOps();
        doReturn(Arrays.asList("1", null)).when(valueOperations).multiGet(anyList());

        List<Boolean> result = service.batchCheckOnline(List.of(USER_ID_1, USER_ID_2));

        assertEquals(2, result.size());
        assertTrue(result.get(0));
        assertFalse(result.get(1));
    }

    @Test
    @DisplayName("批量查询在线状态 - 空输入返回空列表")
    void batchCheckOnline_shouldReturnEmpty_whenEmptyInput() {
        OnlineStatusService service = createServiceWithValueOps();

        List<Boolean> result = service.batchCheckOnline(List.of());

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("批量查询在线状态 - Redis 异常时全部返回 false")
    void batchCheckOnline_shouldDegrade_whenRedisFails() {
        OnlineStatusService service = createServiceWithValueOps();
        doThrow(new RuntimeException("Redis 异常")).when(valueOperations).multiGet(anyList());

        List<Boolean> result = service.batchCheckOnline(List.of(USER_ID_1));

        assertEquals(1, result.size());
        assertFalse(result.get(0));
    }

    @Test
    @DisplayName("批量查询在线状态 - multiGet 返回 null 时全部返回 false")
    void batchCheckOnline_shouldReturnFalse_whenMultiGetReturnsNull() {
        OnlineStatusService service = createServiceWithValueOps();
        doReturn(null).when(valueOperations).multiGet(anyList());

        List<Boolean> result = service.batchCheckOnline(List.of(USER_ID_1));

        assertEquals(1, result.size());
        assertFalse(result.get(0));
    }

    @Test
    @DisplayName("批量过滤在线用户 - multiGet 返回 null 时返回空集合")
    void filterOnline_shouldReturnEmpty_whenMultiGetReturnsNull() {
        OnlineStatusService service = createServiceWithValueOps();
        doReturn(null).when(valueOperations).multiGet(anyList());

        Set<Long> result = service.filterOnline(Set.of(USER_ID_1));

        assertTrue(result.isEmpty());
    }
}