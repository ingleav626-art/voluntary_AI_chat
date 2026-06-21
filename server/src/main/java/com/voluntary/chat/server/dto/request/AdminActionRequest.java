package com.voluntary.chat.server.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 群管理员操作请求 DTO
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
public class AdminActionRequest {

    @NotNull(message = "目标用户ID不能为空")
    private Long targetUserId;

    @NotBlank(message = "操作类型不能为空")
    private String action; // SET 或 REMOVE
}
