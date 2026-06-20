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
}
