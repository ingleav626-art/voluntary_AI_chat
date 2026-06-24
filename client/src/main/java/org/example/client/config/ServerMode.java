package org.example.client.config;

/**
 * 服务器启动模式枚举
 *
 * <p>
 * 定义三种启动模式：
 * <ul>
 * <li>LOCAL - 本地模式：内嵌后端，H2数据库，AI隐私数据本地存储</li>
 * <li>HOTSPOT - 热点模式：连接局域网测试服务器，用于开发测试</li>
 * <li>CLOUD - 云端模式：连接公网服务器，真人实时通信</li>
 * </ul>
 * </p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public enum ServerMode {

    /**
     * 本地模式：内嵌后端，H2数据库，AI隐私数据本地存储
     *
     * <p>
     * 适用场景：
     * <ul>
     * <li>纯AI聊天（不含群主）</li>
     * <li>隐私模式开启时</li>
     * <li>云端服务器不可用时</li>
     * </ul>
     * </p>
     */
    LOCAL("local", "本地模式", "http://localhost:8080/api"),

    /**
     * 热点模式：连接局域网测试服务器
     *
     * <p>
     * 适用场景：
     * <ul>
     * <li>开发测试环境</li>
     * <li>局域网多人协作测试</li>
     * <li>功能稳定后推送云端前的验证</li>
     * </ul>
     * </p>
     *
     * <p>
     * 注意：此模式对用户纯粹多余，仅用于测试。
     * </p>
     */
    HOTSPOT("hotspot", "热点模式", null),

    /**
     * 云端模式：连接公网服务器
     *
     * <p>
     * 适用场景：
     * <ul>
     * <li>真人实时聊天</li>
     * <li>群聊（包含AI）</li>
     * <li>多人协作、在线状态管理</li>
     * </ul>
     * </p>
     */
    CLOUD("cloud", "云端模式", null);

    private final String code;
    private final String description;
    private final String defaultBaseUrl;

    ServerMode(final String code, final String description, final String defaultBaseUrl) {
        this.code = code;
        this.description = description;
        this.defaultBaseUrl = defaultBaseUrl;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }

    /**
     * 根据代码获取启动模式
     *
     * @param code 模式代码
     * @return 启动模式，默认返回 LOCAL
     */
    public static ServerMode fromCode(final String code) {
        if (code == null || code.isEmpty()) {
            return LOCAL;
        }
        for (final ServerMode mode : values()) {
            if (mode.code.equalsIgnoreCase(code)) {
                return mode;
            }
        }
        return LOCAL;
    }
}