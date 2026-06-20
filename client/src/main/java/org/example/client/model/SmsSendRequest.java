package org.example.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送短信验证码请求模型
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SmsSendRequest {

    /** 手机号 */
    private String phone;
}
