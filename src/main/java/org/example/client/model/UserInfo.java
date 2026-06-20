package org.example.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息模型
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {

    /** 用户ID */
    private String userId;

    /** 用户名 */
    private String username;

    /** 头像URL */
    private String avatar;
}

