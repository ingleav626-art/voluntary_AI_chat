package com.voluntary.chat.server.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ConversationResponse {

    private String sessionId;
    private Long targetId;
    private String targetType;
    private String targetName;
    private String targetAvatar;
    private String lastMessage;
    private String lastMessageType;
    private LocalDateTime lastMessageTime;
    private long unreadCount;
}
