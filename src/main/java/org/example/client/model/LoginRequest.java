package org.example.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录请求模型
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    /** 手机号 */
    private String phone;

    /** 密码 */
    private String password;

    /** 记住我 */
    private Boolean rememberMe;
}

