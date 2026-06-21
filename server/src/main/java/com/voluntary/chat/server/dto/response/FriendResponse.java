package com.voluntary.chat.server.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 好友信息响应
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@Builder
public class FriendResponse {

    private Long userId;
    private String username;
    private String avatar;
    private String bio;
    private String remark;
    private boolean online;
}
