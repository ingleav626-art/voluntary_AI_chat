package com.voluntary.chat.server.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 通知设置实体
 *
 * <p>
 * 每个用户一条记录，存储该用户的消息通知偏好。
 * 新用户登录后使用默认设置，可通过 REST API 修改。
 * </p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@TableName("notification_settings")
public class NotificationSettings {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  /** 用户ID，一对一关系 */
  private Long userId;

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

  /** 通知合并窗口（秒），同一会话 N 秒内的消息合并为一条通知 */
  private Integer mergeWindowSeconds;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;

  @TableLogic
  private Integer isDeleted;
}
