package com.voluntary.chat.server.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 邀请成员请求
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
public class InviteMemberRequest {

    @NotEmpty(message = "成员ID列表不能为空")
    private List<Long> userIds;
}