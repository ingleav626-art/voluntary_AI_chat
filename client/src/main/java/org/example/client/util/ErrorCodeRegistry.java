package org.example.client.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.experimental.UtilityClass;

/**
 * 业务错误码注册表
 *
 * <p>将后端返回的业务错误码映射为用户友好的中文提示。
 * 所有模块的错误码集中管理，避免各 Service 重复映射。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@UtilityClass
public class ErrorCodeRegistry {

    private static final Map<Integer, String> ERROR_MESSAGES = new ConcurrentHashMap<>();

    static {
        // 认证模块 1001-1099
        register(1001, "手机号已注册");
        register(1002, "验证码错误或已过期");
        register(1003, "用户名已存在");
        register(1004, "账号或密码错误");
        register(1005, "登录次数过多，账号已锁定");
        register(1006, "登录已失效，请重新登录");

        // 好友模块 2001-2099
        register(2001, "好友申请已存在，请等待对方处理");
        register(2002, "对方已是你的好友");
        register(2003, "不能添加自己为好友");

        // 群组模块 3001-3099
        register(3001, "无权执行此操作");
        register(3002, "群成员已满");
        register(3003, "你已在群中");

        // 消息模块 4001-4099
        register(4001, "消息已超过2分钟，不可撤回");
        register(4002, "无权撤回他人消息");
        register(4003, "图片格式不支持");
        register(4004, "图片大小超出限制");

        // AI模块 5001-5099
        register(5001, "AI服务配置无效");
        register(5002, "AI不存在或已禁用");

        // 通用 HTTP 错误
        register(400, "请求参数错误，请检查输入");
        register(401, "登录已失效，请重新登录");
        register(403, "无权访问，请先登录");
        register(404, "请求的资源不存在");
        register(500, "服务器异常，请稍后重试");
    }

    /**
     * 注册错误码映射
     *
     * @param code 业务错误码
     * @param message 用户友好的中文提示
     */
    public static void register(final int code, final String message) {
        ERROR_MESSAGES.put(code, message);
    }

    /**
     * 获取错误码对应的中文提示
     *
     * <p>优先使用注册表映射，如果未注册则返回后端原始消息，
     * 如果原始消息也为空则返回默认提示。</p>
     *
     * @param code 业务错误码
     * @param backendMessage 后端返回的原始消息
     * @return 用户友好的错误提示
     */
    public static String getMessage(final int code, final String backendMessage) {
        final String mapped = ERROR_MESSAGES.get(code);
        if (mapped != null) {
            return mapped;
        }
        if (backendMessage != null && !backendMessage.isEmpty()) {
            return backendMessage;
        }
        return "操作失败，请稍后重试";
    }

    /**
     * 获取错误码对应的中文提示（无后端消息）
     *
     * @param code 业务错误码
     * @return 用户友好的错误提示
     */
    public static String getMessage(final int code) {
        return getMessage(code, null);
    }

    /**
     * 判断错误码是否已注册
     *
     * @param code 业务错误码
     * @return true 表示已注册
     */
    public static boolean isRegistered(final int code) {
        return ERROR_MESSAGES.containsKey(code);
    }
}
