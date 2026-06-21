package org.example.client.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送消息响应模型
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageResponse {

    /** 消息ID */
    private Long messageId;

    /** 创建时间 */
    private LocalDateTime createTime;
}
