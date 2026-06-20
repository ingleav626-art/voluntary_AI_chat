package com.voluntary.chat.server.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendMessageRequest {

    @NotBlank(message = "会话ID不能为空")
    private String sessionId;

    @NotBlank(message = "消息类型不能为空")
    private String type;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 5000, message = "消息内容不能超过5000字符")
    private String content;
}
