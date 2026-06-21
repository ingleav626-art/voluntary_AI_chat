package com.voluntary.chat.server.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户信息响应
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long userId;
    private String phone;
    private String username;
    private String avatar;
    private String bio;
    /** 性别：0-未知，1-男，2-女 */
    private Integer gender;
    /** 年龄 */
    private Integer age;
    /** 生日 */
    private LocalDate birthday;
    /** 个人详细说明 */
    private String detailBio;
    private LocalDateTime createTime;
}