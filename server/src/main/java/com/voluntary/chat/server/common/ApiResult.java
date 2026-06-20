package com.voluntary.chat.server.common;

import com.voluntary.chat.common.exception.BusinessException;
import com.voluntary.chat.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResult<T> {

    private int code;
    private String message;
    private T data;

    public static <T> ApiResult<T> ok() {
        return new ApiResult<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null);
    }

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    public static <T> ApiResult<T> ok(String message, T data) {
        return new ApiResult<>(ErrorCode.SUCCESS.getCode(), message, data);
    }

    public static <T> ApiResult<T> error(ErrorCode errorCode) {
        return new ApiResult<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> ApiResult<T> error(int code, String message) {
        return new ApiResult<>(code, message, null);
    }

    public static <T> ApiResult<T> error(BusinessException e) {
        return new ApiResult<>(e.getErrorCode().getCode(), e.getMessage(), null);
    }
}
