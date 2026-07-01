package com.voluntary.chat.server.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MessageResponse {

    private Long messageId;
    private String sessionId;
    private Long senderId;
    private String senderName;
    private String senderAvatar;
    private String senderType;
    private String type;
    private String content;
    private String extra;
    private LocalDateTime createTime;
    private boolean recalled;
    /**
     * 是否已读（仅对当前用户发送的消息有效）
     * 修复：解决已读未读状态丢失问题，需要从 message_read 表查询
     */
    private boolean read;
}
