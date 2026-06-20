package org.example.client.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 好友申请响应模型
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendApplyResponse {

    /** 申请ID */
    private Long applyId;

    /** 申请人ID */
    private Long userId;

    /** 申请人用户名 */
    private String username;

    /** 申请人头像 */
    private String avatar;

    /** 申请留言 */
    private String message;

    /** 状态：PENDING / ACCEPTED / REJECTED */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
