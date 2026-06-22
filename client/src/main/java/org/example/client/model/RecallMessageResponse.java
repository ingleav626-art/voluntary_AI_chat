package org.example.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 撤回消息响应模型
 *
 * <p>对应服务端 RecallMessageResponse，用于撤回消息接口返回数据。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecallMessageResponse {

    /** 消息ID */
    private Long messageId;

    /** 会话ID */
    private String sessionId;

    /** 发送者ID */
    private Long senderId;
}
