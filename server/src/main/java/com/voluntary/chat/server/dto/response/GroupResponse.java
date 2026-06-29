package com.voluntary.chat.server.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 群组信息响应
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@Builder
public class GroupResponse {

    private Long groupId;
    private String name;
    private String avatar;
    private Integer memberCount;
    private Long ownerId;
    private String announcement;
    private Boolean announcementPinned;
}