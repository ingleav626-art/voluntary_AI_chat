package com.voluntary.chat.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    SUCCESS(200, "成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器错误"),

    PHONE_ALREADY_REGISTERED(1001, "手机号已注册"),
    VERIFICATION_CODE_ERROR(1002, "验证码错误或已过期"),
    USERNAME_ALREADY_EXISTS(1003, "用户名已存在"),
    ACCOUNT_OR_PASSWORD_ERROR(1004, "账号或密码错误"),
    ACCOUNT_LOCKED(1005, "登录次数过多，账号已锁定"),
    REFRESH_TOKEN_INVALID(1006, "Refresh Token 已失效"),

    FRIEND_APPLY_EXISTS(2001, "好友申请已存在（待处理）"),
    ALREADY_FRIENDS(2002, "已是好友关系"),
    CANNOT_ADD_SELF(2003, "不能添加自己为好友"),

    NO_PERMISSION(3001, "无权执行此操作"),
    GROUP_MEMBER_FULL(3002, "群成员已满"),
    ALREADY_IN_GROUP(3003, "已在群中"),

    MESSAGE_RECALL_TIMEOUT(4001, "消息已超过2分钟，不可撤回"),
    NO_PERMISSION_TO_RECALL(4002, "无权撤回他人消息"),
    IMAGE_FORMAT_NOT_SUPPORTED(4003, "图片格式不支持"),
    IMAGE_SIZE_EXCEEDED(4004, "图片大小超出限制"),

    AI_API_KEY_INVALID(5001, "API Key 无效"),
    AI_NOT_FOUND(5002, "AI 不存在或已禁用");

    private final int code;
    private final String message;
}
