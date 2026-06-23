package org.example.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 修改手机号请求模型
 *
 * <p>已登录用户先验证当前手机号短信码，再绑定新手机号。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangePhoneRequest {

    /** 当前手机号验证码（6位数字） */
    private String smsCode;

    /** 新手机号 */
    private String newPhone;

    /** 新手机号验证码（6位数字） */
    private String newSmsCode;
}