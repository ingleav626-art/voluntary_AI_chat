package org.example.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 群组信息响应模型
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupInfo {

    /** 群组ID */
    private Long groupId;

    /** 群名称 */
    private String name;

    /** 群头像URL */
    private String avatar;

    /** 成员数量 */
    private Integer memberCount;

    /** 群主ID */
    private Long ownerId;

    /** 最后一条消息内容 */
    private String lastMessage;

    /** 最后一条消息类型 */
    private String lastMessageType;

    /** 最后一条消息时间 */
    private String lastMessageTime;

    /** 未读消息数 */
    private Integer unreadCount;
}
