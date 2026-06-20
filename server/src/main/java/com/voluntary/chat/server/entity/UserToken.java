package com.voluntary.chat.server.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_token")
public class UserToken {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String accessToken;

    private String refreshToken;

    private LocalDateTime accessExpires;

    private LocalDateTime refreshExpires;

    private String deviceInfo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
