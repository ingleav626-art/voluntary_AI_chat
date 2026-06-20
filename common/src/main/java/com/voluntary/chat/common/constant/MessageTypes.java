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
    public static final String STATUS_CHANGE = "STATUS_CHANGE";
    public static final String READ_RECEIPT = "READ_RECEIPT";
    public static final String PING = "PING";
    public static final String PONG = "PONG";
    public static final String RECONNECT = "RECONNECT";
    public static final String RECONNECT_ACK = "RECONNECT_ACK";
}
