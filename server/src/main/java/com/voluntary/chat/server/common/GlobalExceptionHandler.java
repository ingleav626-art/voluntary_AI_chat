package com.voluntary.chat.server.common;

import com.voluntary.chat.common.exception.BusinessException;
import com.voluntary.chat.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResult<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return ApiResult.error(e);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", message);
        return ApiResult.error(ErrorCode.BAD_REQUEST.getCode(), message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        String detail = e.getMostSpecificCause().getMessage();
        log.warn("请求体解析失败: {}", detail);
        return ApiResult.error(ErrorCode.BAD_REQUEST.getCode(), "请求体格式错误，请确保使用双引号的JSON格式");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("参数错误: {}", e.getMessage());
        return ApiResult.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
    }

    /**
     * 处理静态资源/接口未找到异常
     *
     * <p>当请求路径没有匹配的 Controller 时，Spring 会回退到静态资源查找，
     * 查找失败抛出此异常。此前被 {@link #handleException(Exception)} 捕获并返回 500，
     * 掩盖了"接口不存在"的真实原因。此处单独处理，返回 404 并记录 WARN 日志，
     * 便于排查 Controller 未注册或路径错误问题。</p>
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResult<Void> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("接口或资源不存在: {}", e.getMessage());
        return ApiResult.error(ErrorCode.NOT_FOUND.getCode(), "请求的接口不存在");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<Void> handleException(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);
        return ApiResult.error(ErrorCode.INTERNAL_ERROR.getCode(), "服务器内部错误，请稍后重试");
    }
}
