package org.example.client.model;

import lombok.Data;

/**
 * API 统一响应模型
 *
 * @param <T> 数据类型
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Data
public class ApiResponse<T> {

    /** 成功状态码 */
    private static final int SUCCESS_CODE = 200;

    /** 业务状态码（200 表示成功） */
    private int code;

    /** 提示消息 */
    private String message;

    /** 业务数据 */
    private T data;

    /**
     * 判断是否成功
     *
     * @return true 表示业务码为 200
     */
    public boolean isSuccess() {
        return code == SUCCESS_CODE;
    }
}

