package com.voluntary.chat.server.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 设置群昵称请求 DTO
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
public class SetNicknameRequest {

    @Size(max = 50, message = "昵称不能超过50字符")
    private String nickname;
}
