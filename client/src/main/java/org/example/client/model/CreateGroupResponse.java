package org.example.client.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建群组响应
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateGroupResponse {

    /** 群组ID */
    private Long groupId;

    /** 群名称 */
    private String name;
}
