package com.voluntary.chat.server.service;

import com.voluntary.chat.server.config.CacheProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GroupCacheService 单元测试")
class GroupCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private CacheProperties cacheProperties;

    @Mock
    private CacheProperties.CacheItem groupMembersConfig;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private static final Long GROUP_ID = 1L;
    private static final Long USER_ID_1 = 1001L;
    private static final Long USER_ID_2 = 1002L;

    private GroupCacheService createService(boolean enabled) {
        when(cacheProperties.isEnabled()).thenReturn(enabled);
        when(cacheProperties.getGroupMembers()).thenReturn(groupMembersConfig);
        return new GroupCacheService(redisTemplate, cacheProperties);
    }

    private GroupCacheService createServiceWithOps(boolean enabled) {
        doReturn(setOperations).when(redisTemplate).opsForSet();
        doReturn(valueOperations).when(redisTemplate).opsForValue();
        return createService(enabled);
    }

    @Test
    @DisplayName("获取群成员 - 命中缓存返回成员集合")
    void getMemberIds_shouldReturn_whenHit() {
        GroupCacheService service = createServiceWithOps(true);
        when(setOperations.members("group:members:1")).thenReturn(Set.of("1001", "1002"));

        Set<Long> result = service.getMemberIds(GROUP_ID);

        assertEquals(Set.of(1001L, 1002L), result);
    }

    @Test
    @DisplayName("获取群成员 - 缓存为空返回 null")
    void getMemberIds_shouldReturnNull_whenEmpty() {
        GroupCacheService service = createServiceWithOps(true);
        when(setOperations.members("group:members:1")).thenReturn(Set.of());

        assertNull(service.getMemberIds(GROUP_ID));
    }

    @Test
    @DisplayName("获取群成员 - 缓存未设置返回 null")
    void getMemberIds_shouldReturnNull_whenMiss() {
        GroupCacheService service = createServiceWithOps(true);
        when(setOperations.members("group:members:1")).thenReturn(null);

        assertNull(service.getMemberIds(GROUP_ID));
    }

    @Test
    @DisplayName("获取群成员 - 禁用时返回 null")
    void getMemberIds_shouldReturnNull_whenDisabled() {
        GroupCacheService service = createServiceWithOps(false);

        assertNull(service.getMemberIds(GROUP_ID));
    }

    @Test
    @DisplayName("获取群成员 - Redis 异常降级返回 null")
    void getMemberIds_shouldDegrade_whenRedisFails() {
        GroupCacheService service = createServiceWithOps(true);
        when(setOperations.members(anyString())).thenThrow(new RuntimeException("Redis 异常"));

        assertNull(service.getMemberIds(GROUP_ID));
    }

    @Test
    @DisplayName("设置群成员 - 成功")
    void setMemberIds_shouldSucceed() {
        GroupCacheService service = createServiceWithOps(true);

        service.setMemberIds(GROUP_ID, List.of(USER_ID_1, USER_ID_2));

        verify(redisTemplate).delete("group:members:1");
        verify(setOperations).add(eq("group:members:1"), eq("1001"), eq("1002"));
    }

    @Test
    @DisplayName("设置群成员 - 空列表不清除时也删除旧缓存")
    void setMemberIds_shouldDeleteKey_whenEmpty() {
        GroupCacheService service = createServiceWithOps(true);

        service.setMemberIds(GROUP_ID, List.of());

        verify(redisTemplate).delete("group:members:1");
        verify(setOperations, never()).add(anyString(), anyString());
    }

    @Test
    @DisplayName("添加成员 - 成功")
    void addMember_shouldSucceed() {
        GroupCacheService service = createServiceWithOps(true);

        service.addMember(GROUP_ID, USER_ID_1);

        verify(setOperations).add("group:members:1", "1001");
        verify(valueOperations).increment("group:member_count:1", 1);
    }

    @Test
    @DisplayName("添加成员 - 禁用时跳过")
    void addMember_shouldSkip_whenDisabled() {
        GroupCacheService service = createServiceWithOps(false);

        service.addMember(GROUP_ID, USER_ID_1);

        verify(setOperations, never()).add(anyString(), anyString());
    }

    @Test
    @DisplayName("移除成员 - 成功")
    void removeMember_shouldSucceed() {
        GroupCacheService service = createServiceWithOps(true);

        service.removeMember(GROUP_ID, USER_ID_1);

        verify(setOperations).remove("group:members:1", "1001");
        verify(valueOperations).decrement("group:member_count:1", 1);
    }

    @Test
    @DisplayName("移除成员 - 禁用时跳过")
    void removeMember_shouldSkip_whenDisabled() {
        GroupCacheService service = createServiceWithOps(false);

        service.removeMember(GROUP_ID, USER_ID_1);

        verify(setOperations, never()).remove(anyString(), anyString());
    }

    @Test
    @DisplayName("获取成员数量 - 命中返回计数值")
    void getMemberCount_shouldReturn_whenHit() {
        GroupCacheService service = createServiceWithOps(true);
        when(valueOperations.get("group:member_count:1")).thenReturn("10");

        assertEquals(10L, service.getMemberCount(GROUP_ID));
    }

    @Test
    @DisplayName("获取成员数量 - 未命中返回 -1")
    void getMemberCount_shouldReturnMinusOne_whenMiss() {
        GroupCacheService service = createServiceWithOps(true);
        when(valueOperations.get("group:member_count:1")).thenReturn(null);

        assertEquals(-1L, service.getMemberCount(GROUP_ID));
    }

    @Test
    @DisplayName("获取成员数量 - 禁用时返回 -1")
    void getMemberCount_shouldReturnMinusOne_whenDisabled() {
        GroupCacheService service = createServiceWithOps(false);

        assertEquals(-1L, service.getMemberCount(GROUP_ID));
    }

    @Test
    @DisplayName("设置成员数量 - 成功")
    void setMemberCount_shouldSucceed() {
        GroupCacheService service = createServiceWithOps(true);

        service.setMemberCount(GROUP_ID, 5L);

        verify(valueOperations).set("group:member_count:1", "5");
    }

    @Test
    @DisplayName("设置成员数量 - 禁用时跳过")
    void setMemberCount_shouldSkip_whenDisabled() {
        GroupCacheService service = createServiceWithOps(false);

        service.setMemberCount(GROUP_ID, 5L);

        verify(valueOperations, never()).set(anyString(), anyString());
    }

    @Test
    @DisplayName("删除群缓存 - 删除成员集合和计数")
    void deleteGroupCache_shouldDeleteBothKeys() {
        GroupCacheService service = createServiceWithOps(true);

        service.deleteGroupCache(GROUP_ID);

        verify(redisTemplate).delete("group:members:1");
        verify(redisTemplate).delete("group:member_count:1");
    }

    @Test
    @DisplayName("删除群缓存 - 禁用时跳过")
    void deleteGroupCache_shouldSkip_whenDisabled() {
        GroupCacheService service = createServiceWithOps(false);

        service.deleteGroupCache(GROUP_ID);

        verify(redisTemplate, never()).delete(anyString());
    }
}