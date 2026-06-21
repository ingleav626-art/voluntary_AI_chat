package com.voluntary.chat.server.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 转让群主请求 DTO
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
public class TransferOwnerRequest {

    @NotNull(message = "目标用户ID不能为空")
    private Long targetUserId;
}
