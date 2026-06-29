package org.example.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 修改密码请求模型
 *
 * <p>已登录用户通过短信验证码修改密码。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {

    /** 短信验证码（6位数字） */
    private String smsCode;

    /** 新密码（6-50个字符） */
    private String newPassword;

    /** 确认密码 */
    private String confirmPassword;
}