package org.example.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册请求模型
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    /** 手机号 */
    private String phone;

    /** 验证码 */
    private String code;

    /** 用户名 */
    private String username;

    /** 密码 */
    private String password;
}
