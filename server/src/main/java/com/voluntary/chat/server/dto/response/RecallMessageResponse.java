package com.voluntary.chat.server.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 撤回消息响应
 */
@Data
@Builder
public class RecallMessageResponse {

    private Long messageId;

    private String sessionId;

    private Long senderId;
}
