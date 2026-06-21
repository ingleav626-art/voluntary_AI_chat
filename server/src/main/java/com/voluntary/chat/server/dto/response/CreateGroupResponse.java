package com.voluntary.chat.server.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 创建群组响应
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@Builder
public class CreateGroupResponse {

    private Long groupId;
    private String name;
}