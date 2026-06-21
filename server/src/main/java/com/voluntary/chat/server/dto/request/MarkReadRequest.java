package com.voluntary.chat.server.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class MarkReadRequest {

    @NotBlank(message = "会话ID不能为空")
    private String sessionId;

    @NotEmpty(message = "消息ID列表不能为空")
    private List<Long> messageIds;
}
