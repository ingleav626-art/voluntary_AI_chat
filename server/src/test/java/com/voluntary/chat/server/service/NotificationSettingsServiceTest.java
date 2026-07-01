package com.voluntary.chat.server.service;

import com.voluntary.chat.server.dto.request.UpdateNotificationSettingsRequest;
import com.voluntary.chat.server.dto.response.NotificationSettingsResponse;
import com.voluntary.chat.server.entity.NotificationSettings;
import com.voluntary.chat.server.mapper.NotificationSettingsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NotificationSettingsService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationSettingsService 单元测试")
class NotificationSettingsServiceTest {

    @Mock
    private NotificationSettingsMapper mapper;

    @InjectMocks
    private NotificationSettingsService service;

    private static final Long USER_ID = 1001L;

    @Captor
    private ArgumentCaptor<NotificationSettings> entityCaptor;

    @Nested
    @DisplayName("getSettings()")
    class GetSettings {

        @Test
        @DisplayName("存在设置时返回实体对应的响应")
        void shouldReturnSettingsWhenExists() {
            NotificationSettings entity = createDefaultEntity();
            when(mapper.selectOne(any())).thenReturn(entity);

            NotificationSettingsResponse result = service.getSettings(USER_ID);

            assertNotNull(result);
            assertTrue(result.getMessageNotification());
            assertTrue(result.getMessageSound());
            assertFalse(result.getDoNotDisturb());
            assertEquals(LocalTime.of(22, 0), result.getDndStartTime());
            assertEquals(LocalTime.of(9, 0), result.getDndEndTime());
            assertEquals(5, result.getMergeWindowSeconds());
        }

        @Test
        @DisplayName("不存在设置时返回默认值")
        void shouldReturnDefaultsWhenNotExists() {
            when(mapper.selectOne(any())).thenReturn(null);

            NotificationSettingsResponse result = service.getSettings(USER_ID);

            assertNotNull(result);
            assertTrue(result.getMessageNotification());
            assertTrue(result.getTodoReminder());
            assertFalse(result.getDoNotDisturb());
            assertEquals(5, result.getMergeWindowSeconds());
        }
    }

    @Nested
    @DisplayName("updateSettings()")
    class UpdateSettings {

        @Test
        @DisplayName("存在设置时只覆盖非 null 字段")
        void shouldUpdateOnlyNonNullFields() {
            NotificationSettings entity = createDefaultEntity();
            entity.setId(1L);
            when(mapper.selectOne(any())).thenReturn(entity);

            // 只关闭消息通知，其他字段不应改变
            UpdateNotificationSettingsRequest request = new UpdateNotificationSettingsRequest();
            request.setMessageNotification(false);

            NotificationSettingsResponse result = service.updateSettings(USER_ID, request);

            assertFalse(result.getMessageNotification());
            // 其他字段应保持默认
            assertTrue(result.getMessageSound());
            assertTrue(result.getAiGreetingNotification());
            assertEquals(5, result.getMergeWindowSeconds());
        }

        @Test
        @DisplayName("不存在设置时自动创建再更新")
        void shouldCreateThenUpdateWhenNotExists() {
            when(mapper.selectOne(any())).thenReturn(null);

            UpdateNotificationSettingsRequest request = new UpdateNotificationSettingsRequest();
            request.setDoNotDisturb(true);
            request.setDndStartTime(LocalTime.of(23, 0));
            request.setDndEndTime(LocalTime.of(8, 0));

            NotificationSettingsResponse result = service.updateSettings(USER_ID, request);

            assertTrue(result.getDoNotDisturb());
            assertEquals(LocalTime.of(23, 0), result.getDndStartTime());
            assertEquals(LocalTime.of(8, 0), result.getDndEndTime());
            // 默认值应保留
            assertTrue(result.getMessageNotification());

            // 验证先 insert 再 update
            verify(mapper, times(1)).insert(entityCaptor.capture());
            NotificationSettings inserted = entityCaptor.getValue();
            assertEquals(USER_ID, inserted.getUserId());
            assertTrue(inserted.getMessageNotification());

            verify(mapper, times(1)).updateById(any(NotificationSettings.class));
        }

        @Test
        @DisplayName("部分更新多个字段")
        void shouldUpdateMultipleFields() {
            NotificationSettings entity = createDefaultEntity();
            entity.setId(1L);
            when(mapper.selectOne(any())).thenReturn(entity);

            UpdateNotificationSettingsRequest request = new UpdateNotificationSettingsRequest();
            request.setMessageNotification(false);
            request.setMessageSound(false);
            request.setAiGreetingNotification(false);
            request.setMergeWindowSeconds(10);

            NotificationSettingsResponse result = service.updateSettings(USER_ID, request);

            assertFalse(result.getMessageNotification());
            assertFalse(result.getMessageSound());
            assertFalse(result.getAiGreetingNotification());
            assertEquals(10, result.getMergeWindowSeconds());
            // 未传入的应保持默认
            assertTrue(result.getAiGreetingSound());
            assertTrue(result.getTodoReminder());
        }

        @Test
        @DisplayName("所有字段均可独立更新")
        void shouldUpdateAllFieldsIndependently() {
            NotificationSettings entity = createDefaultEntity();
            entity.setId(1L);
            when(mapper.selectOne(any())).thenReturn(entity);

            // disambiguate the updateById(T) overload for MyBatis-Plus
            doAnswer(invocation -> {
                NotificationSettings arg = invocation.getArgument(0, NotificationSettings.class);
                updatedEntity = arg;
                return 1;
            }).when(mapper).updateById(any(NotificationSettings.class));

            // 1. messageSound
            service.updateSettings(USER_ID, requestWith("messageSound", false));
            assertNotNull(updatedEntity);
            assertFalse(updatedEntity.getMessageSound());

            // 2. todoReminder
            service.updateSettings(USER_ID, requestWith("todoReminder", false));
            assertFalse(updatedEntity.getTodoReminder());

            // 3. mergeWindowSeconds
            service.updateSettings(USER_ID, requestWith("mergeWindowSeconds", 30));
            assertEquals(30, updatedEntity.getMergeWindowSeconds());
        }

        private NotificationSettings updatedEntity;

        private UpdateNotificationSettingsRequest requestWith(String field, Object value) {
            UpdateNotificationSettingsRequest req = new UpdateNotificationSettingsRequest();
            switch (field) {
                case "messageSound" -> req.setMessageSound((Boolean) value);
                case "todoReminder" -> req.setTodoReminder((Boolean) value);
                case "mergeWindowSeconds" -> req.setMergeWindowSeconds((Integer) value);
            }
            return req;
        }
    }

    @Nested
    @DisplayName("isInDoNotDisturb()")
    class IsInDoNotDisturb {

        @Test
        @DisplayName("免打扰关闭时返回 false")
        void shouldReturnFalseWhenDndDisabled() {
            NotificationSettings entity = createDefaultEntity();
            entity.setDoNotDisturb(false);
            when(mapper.selectOne(any())).thenReturn(entity);

            assertFalse(service.isInDoNotDisturb(USER_ID));
        }

        @Test
        @DisplayName("未设置时返回 false")
        void shouldReturnFalseWhenNotConfigured() {
            when(mapper.selectOne(any())).thenReturn(null);

            assertFalse(service.isInDoNotDisturb(USER_ID));
        }

        @Test
        @DisplayName("免打扰开启且当前在时段内返回 true")
        void shouldReturnTrueWhenInDndPeriod() {
            NotificationSettings entity = createDefaultEntity();
            entity.setDoNotDisturb(true);
            entity.setDndStartTime(LocalTime.of(0, 0));
            entity.setDndEndTime(LocalTime.of(23, 59));
            when(mapper.selectOne(any())).thenReturn(entity);

            assertTrue(service.isInDoNotDisturb(USER_ID));
        }

        @Test
        @DisplayName("start/end 为 null 时返回 false")
        void shouldReturnFalseWhenTimeNull() {
            NotificationSettings entity = createDefaultEntity();
            entity.setDoNotDisturb(true);
            entity.setDndStartTime(null);
            entity.setDndEndTime(null);
            when(mapper.selectOne(any())).thenReturn(entity);

            assertFalse(service.isInDoNotDisturb(USER_ID));
        }
    }

    /**
     * 创建默认设置实体（已持久化，带 ID）
     */
    private NotificationSettings createDefaultEntity() {
        NotificationSettings entity = new NotificationSettings();
        entity.setId(1L);
        entity.setUserId(USER_ID);
        entity.setMessageNotification(true);
        entity.setMessageSound(true);
        entity.setAiGreetingNotification(true);
        entity.setAiGreetingSound(true);
        entity.setTodoReminder(true);
        entity.setTodoSound(true);
        entity.setDoNotDisturb(false);
        entity.setDndStartTime(LocalTime.of(22, 0));
        entity.setDndEndTime(LocalTime.of(9, 0));
        entity.setMergeWindowSeconds(5);
        return entity;
    }
}