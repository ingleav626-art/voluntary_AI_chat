package com.voluntary.chat.server.dto.response;

import com.voluntary.chat.server.entity.NotificationSettings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
 * 通知设置响应
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSettingsResponse {

    private Boolean messageNotification;
    private Boolean messageSound;
    private Boolean aiGreetingNotification;
    private Boolean aiGreetingSound;
    private Boolean todoReminder;
    private Boolean todoSound;
    private Boolean doNotDisturb;
    private LocalTime dndStartTime;
    private LocalTime dndEndTime;
    private Integer mergeWindowSeconds;

    /**
     * 从实体构造响应
     */
    public static NotificationSettingsResponse fromEntity(final NotificationSettings entity) {
        return NotificationSettingsResponse.builder()
                .messageNotification(entity.getMessageNotification())
                .messageSound(entity.getMessageSound())
                .aiGreetingNotification(entity.getAiGreetingNotification())
                .aiGreetingSound(entity.getAiGreetingSound())
                .todoReminder(entity.getTodoReminder())
                .todoSound(entity.getTodoSound())
                .doNotDisturb(entity.getDoNotDisturb())
                .dndStartTime(entity.getDndStartTime())
                .dndEndTime(entity.getDndEndTime())
                .mergeWindowSeconds(entity.getMergeWindowSeconds())
                .build();
    }

    /**
     * 创建默认通知设置响应
     */
    public static NotificationSettingsResponse defaultSettings() {
        return NotificationSettingsResponse.builder()
                .messageNotification(true)
                .messageSound(true)
                .aiGreetingNotification(true)
                .aiGreetingSound(true)
                .todoReminder(true)
                .todoSound(true)
                .doNotDisturb(false)
                .dndStartTime(LocalTime.of(22, 0))
                .dndEndTime(LocalTime.of(9, 0))
                .mergeWindowSeconds(5)
                .build();
    }
}
