package com.voluntary.chat.server.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 群组实体
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@TableName("`group`")
public class GroupEntity {

  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  /** 群组名称 */
  private String name;

  /** 群头像URL */
  private String avatar;

  /** 群公告 */
  private String announcement;

  /** 公告是否置顶 */
  private Boolean announcementPinned;

  /** 群主ID */
  private Long ownerId;

  /** 最大成员数 */
  private Integer maxMemberCount;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;

  @TableLogic
  private Integer isDeleted;
}