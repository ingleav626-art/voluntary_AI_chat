package com.voluntary.chat.server.dto.request;

import jakarta.validation.constraints.NotBlank;
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

    /**
     * 初始成员ID列表（可选）
     *
     * <p>允许为空或空列表：用户可先创建仅含自己的群组，后续再通过邀请接口添加成员。
     * Service 层会过滤掉创建者自身并处理 null/空集合。</p>
     */
    private List<Long> memberIds;
}