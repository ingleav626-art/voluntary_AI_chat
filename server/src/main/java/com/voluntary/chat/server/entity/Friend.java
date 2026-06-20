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
 * 好友关系实体
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@TableName("friend")
public class Friend {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long friendId;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    private Integer isDeleted;
}
