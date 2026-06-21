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
 * 群成员实体
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@TableName("group_member")
public class GroupMember {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 群组ID */
    private Long groupId;

    /** 用户ID */
    private Long userId;

    /** 角色：0-普通成员，1-管理员，2-群主 */
    private Integer role;

    /** 群昵称 */
    private String nickname;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}