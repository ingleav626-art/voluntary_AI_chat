package com.voluntary.chat.server.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SendMessageResponse {

    private Long messageId;
    private LocalDateTime createTime;
}
