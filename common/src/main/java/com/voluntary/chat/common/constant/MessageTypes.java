package com.voluntary.chat.common.constant;

import lombok.experimental.UtilityClass;

@UtilityClass
public class MessageTypes {

    public static final String SEND_MESSAGE = "SEND_MESSAGE";
    public static final String RECEIVE_MESSAGE = "RECEIVE_MESSAGE";
    public static final String GROUP_MESSAGE = "GROUP_MESSAGE";
    public static final String AI_CHAT = "AI_CHAT";
    public static final String AI_STREAM = "AI_STREAM";
    public static final String MESSAGE_ACK = "MESSAGE_ACK";
    public static final String MESSAGE_RECALL = "MESSAGE_RECALL";
    public static final String STATUS_CHANGE = "STATUS_CHANGE";
    public static final String READ_RECEIPT = "READ_RECEIPT";
    public static final String PING = "PING";
    public static final String PONG = "PONG";
    public static final String RECONNECT = "RECONNECT";
    public static final String RECONNECT_ACK = "RECONNECT_ACK";
    public static final String FORCE_LOGOUT = "FORCE_LOGOUT";

    // 群组事件通知
    public static final String GROUP_MEMBER_JOIN = "GROUP_MEMBER_JOIN";
    public static final String GROUP_MEMBER_LEAVE = "GROUP_MEMBER_LEAVE";
    public static final String GROUP_MEMBER_ROLE_CHANGE = "GROUP_MEMBER_ROLE_CHANGE";
    public static final String GROUP_INFO_CHANGE = "GROUP_INFO_CHANGE";
    public static final String GROUP_DISMISSED = "GROUP_DISMISSED";

    // 通知消息（服务端 → 客户端）
    public static final String NOTIFICATION = "NOTIFICATION";
    public static final String NOTIFICATION_NEW_MESSAGE = "NOTIFICATION_NEW_MESSAGE";
    public static final String NOTIFICATION_AI_GREETING = "NOTIFICATION_AI_GREETING";
    public static final String NOTIFICATION_TODO_REMINDER = "NOTIFICATION_TODO_REMINDER";
    public static final String NOTIFICATION_SYSTEM_EVENT = "NOTIFICATION_SYSTEM_EVENT";
    // 通知设置变更（其他设备修改了通知设置，当前设备需刷新）
    public static final String NOTIFICATION_SETTINGS_CHANGED = "NOTIFICATION_SETTINGS_CHANGED";
}
