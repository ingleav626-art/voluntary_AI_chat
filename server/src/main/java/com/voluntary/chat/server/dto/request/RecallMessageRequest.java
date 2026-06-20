package com.voluntary.chat.server.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RecallMessageRequest {

    @NotBlank(message = "消息ID不能为空")
    private String messageId;
}
