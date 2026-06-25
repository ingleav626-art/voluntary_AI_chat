package com.voluntary.chat.server.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 群成员信息响应
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@Builder
public class GroupMemberResponse {

    private Long userId;
    private String username;
    private String avatar;
    private String role;
    private String nickname;
    private LocalDateTime joinTime;
}