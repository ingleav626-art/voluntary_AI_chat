package org.example.client.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息已读请求模型
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarkReadRequest {

    /** 会话ID */
    private String sessionId;

    /** 已读消息ID列表 */
    private List<Long> messageIds;
}
