package org.example.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送消息请求模型
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {

    /** 会话ID */
    private String sessionId;

    /** 消息类型：TEXT / IMAGE */
    private String type;

    /** 消息内容 */
    private String content;
}
