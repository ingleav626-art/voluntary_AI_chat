package org.example.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 好友申请请求模型
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendApplyRequest {

    /** 目标用户手机号 */
    private String targetPhone;

    /** 申请留言 */
    private String message;
}
