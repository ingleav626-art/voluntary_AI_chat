package com.voluntary.chat.server.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalTime;

/**
 * 更新通知设置请求
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
public class UpdateNotificationSettingsRequest {

    /** 新消息通知：0-关闭，1-开启 */
    private Boolean messageNotification;

    /** 新消息声音：0-关闭，1-开启 */
    private Boolean messageSound;

    /** AI 主动问候通知：0-关闭，1-开启 */
    private Boolean aiGreetingNotification;

    /** AI 问候声音：0-关闭，1-开启 */
    private Boolean aiGreetingSound;

    /** 待办提醒：0-关闭，1-开启 */
    private Boolean todoReminder;

    /** 待办声音：0-关闭，1-开启 */
    private Boolean todoSound;

    /** 免打扰模式：0-关闭，1-开启 */
    private Boolean doNotDisturb;

    /** 免打扰开始时间 */
    private LocalTime dndStartTime;

    /** 免打扰结束时间 */
    private LocalTime dndEndTime;

    /** 通知合并窗口（秒） */
    @Min(value = 1, message = "合并窗口至少为 1 秒")
    @Max(value = 300, message = "合并窗口最多为 300 秒")
    private Integer mergeWindowSeconds;
}
