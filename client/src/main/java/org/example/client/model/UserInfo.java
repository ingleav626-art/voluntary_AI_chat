package org.example.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户信息模型
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {

    /** 用户ID */
    private Long userId;

    /** 手机号（脱敏后） */
    private String phone;

    /** 用户名 */
    private String username;

    /** 头像URL */
    private String avatar;

    /** 个人简介 */
    private String bio;

    /** 性别：0-未知，1-男，2-女 */
    private Integer gender;

    /** 年龄 */
    private Integer age;

    /** 生日 */
    private LocalDate birthday;

    /** 个人详细说明 */
    private String detailBio;

    /** 注册时间 */
    private LocalDateTime createTime;
}

