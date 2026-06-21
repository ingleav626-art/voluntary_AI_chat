package com.voluntary.chat.server.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 好友申请请求
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
public class FriendApplyRequest {

    /** 目标用户手机号 */
    @NotNull(message = "目标用户手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String targetPhone;

    @Size(max = 200, message = "申请留言不能超过200字符")
    private String message;
}
