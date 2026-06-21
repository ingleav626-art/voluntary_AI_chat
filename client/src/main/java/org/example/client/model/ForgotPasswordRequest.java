package org.example.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 忘记密码请求模型
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForgotPasswordRequest {

    /** 手机号 */
    private String phone;

    /** 验证码 */
    private String code;

    /** 新密码 */
    private String newPassword;

    /** 确认密码 */
    private String confirmPassword;
}
