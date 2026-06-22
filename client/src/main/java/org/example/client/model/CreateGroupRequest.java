package org.example.client.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建群组请求
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateGroupRequest {

    /** 群名称 */
    private String name;

    /** 初始成员ID列表 */
    private List<Long> memberIds;
}
