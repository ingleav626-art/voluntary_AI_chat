package org.example.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 群成员信息响应模型
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberInfo {

    /** 用户ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 头像URL */
    private String avatar;

    /** 角色：OWNER / ADMIN / MEMBER */
    private String role;

    /** 加入时间 */
    private String joinTime;

    /** 群昵称 */
    private String nickname;
}
