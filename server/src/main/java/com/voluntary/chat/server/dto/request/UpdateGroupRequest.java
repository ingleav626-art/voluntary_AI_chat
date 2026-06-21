package com.voluntary.chat.server.dto.request;

import lombok.Data;

/**
 * 修改群信息请求
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
public class UpdateGroupRequest {

    private String name;
    private String announcement;
    private Boolean announcementPinned;
}