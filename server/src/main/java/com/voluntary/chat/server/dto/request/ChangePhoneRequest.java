package com.voluntary.chat.server.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改手机号请求
 *
 * <p>已登录用户先验证当前手机号短信码，再绑定新手机号。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
public class ChangePhoneRequest {

    @NotBlank(message = "当前手机号验证码不能为空")
    @Size(min = 6, max = 6, message = "验证码为6位数字")
    private String smsCode;

    @NotBlank(message = "新手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String newPhone;

    @NotBlank(message = "新手机号验证码不能为空")
    @Size(min = 6, max = 6, message = "验证码为6位数字")
    private String newSmsCode;
}