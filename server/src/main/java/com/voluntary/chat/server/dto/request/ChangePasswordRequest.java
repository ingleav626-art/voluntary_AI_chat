package com.voluntary.chat.server.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求
 *
 * <p>
 * 已登录用户通过短信验证码修改密码。
 * </p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "短信验证码不能为空")
    @Size(min = 6, max = 6, message = "验证码为6位数字")
    private String smsCode;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度6-50个字符")
    private String newPassword;

    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}