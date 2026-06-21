package com.voluntary.chat.server.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个人信息请求
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
public class UpdateProfileRequest {

    @Size(min = 2, max = 50, message = "用户名长度2-50个字符")
    private String username;

    @Size(max = 500, message = "头像URL过长")
    private String avatar;

    @Size(max = 500, message = "签名过长")
    private String bio;

    /** 性别：0-未知，1-男，2-女 */
    @Min(value = 0, message = "性别值不正确")
    @Max(value = 2, message = "性别值不正确")
    private Integer gender;

    /** 年龄：0~200 */
    @Min(value = 0, message = "年龄范围0-200")
    @Max(value = 200, message = "年龄范围0-200")
    private Integer age;

    /** 生日，格式：yyyy-MM-dd */
    private String birthday;

    /** 个人详细说明 */
    @Size(max = 2000, message = "详细说明不能超过2000字符")
    private String detailBio;
}
