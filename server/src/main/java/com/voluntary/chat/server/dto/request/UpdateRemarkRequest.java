package com.voluntary.chat.server.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改好友备注请求
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
public class UpdateRemarkRequest {

    /** 好友备注名，最长 50 字符，可为空字符串（清除备注） */
    @Size(max = 50, message = "备注名最长50个字符")
    private String remark;
}
