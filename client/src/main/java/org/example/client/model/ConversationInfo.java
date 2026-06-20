package org.example.client.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话信息模型
 *
 * <p>对应后端 ConversationResponse，用于会话列表展示。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationInfo {

    /** 会话ID */
    private String sessionId;

    /** 目标ID（用户ID或群组ID） */
    private Long targetId;

    /** 目标类型：USER / GROUP */
    private String targetType;

    /** 目标名称 */
    private String targetName;

    /** 目标头像URL */
    private String targetAvatar;

    /** 最后一条消息内容 */
    private String lastMessage;

    /** 最后一条消息类型 */
    private String lastMessageType;

    /** 最后一条消息时间 */
    private LocalDateTime lastMessageTime;

    /** 未读消息数 */
    private long unreadCount;
}
