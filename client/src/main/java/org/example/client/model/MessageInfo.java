package org.example.client.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息信息模型
 *
 * <p>对应后端 MessageResponse，用于聊天记录展示。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageInfo {

    /** 消息ID */
    private Long messageId;

    /** 会话ID */
    private String sessionId;

    /** 发送者ID */
    private Long senderId;

    /** 发送者名称 */
    private String senderName;

    /** 发送者头像 */
    private String senderAvatar;

    /** 发送者类型：USER / AI */
    private String senderType;

    /** 消息类型：TEXT / IMAGE */
    private String type;

    /** 消息内容 */
    private String content;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 是否已撤回 */
    private boolean recalled;

    /** 是否为当前用户发送（前端计算字段） */
    private boolean sentByMe;
}
