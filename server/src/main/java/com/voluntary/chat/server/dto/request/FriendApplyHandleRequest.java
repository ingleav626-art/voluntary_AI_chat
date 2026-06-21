package com.voluntary.chat.server.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 处理好友申请请求
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
public class FriendApplyHandleRequest {

    @NotBlank(message = "处理动作不能为空")
    private String action;
}
