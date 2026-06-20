package org.example.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 好友信息响应模型
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendResponse {

    /** 用户ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 头像URL */
    private String avatar;

    /** 个人简介 */
    private String bio;

    /** 备注 */
    private String remark;

    /** 是否在线 */
    private boolean online;
}
