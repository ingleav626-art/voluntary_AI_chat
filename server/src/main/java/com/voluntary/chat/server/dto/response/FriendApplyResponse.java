package com.voluntary.chat.server.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 好友申请响应
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@Builder
public class FriendApplyResponse {

    private Long applyId;
    private Long userId;
    private String username;
    private String avatar;
    private String message;
    private String status;
    private LocalDateTime createTime;
}
