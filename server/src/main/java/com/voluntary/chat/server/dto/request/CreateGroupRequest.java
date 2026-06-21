package com.voluntary.chat.server.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建群组请求
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
public class CreateGroupRequest {

    @NotBlank(message = "群组名称不能为空")
    @Size(max = 50, message = "群组名称不能超过50字符")
    private String name;

    @NotEmpty(message = "初始成员不能为空")
    private List<Long> memberIds;
}