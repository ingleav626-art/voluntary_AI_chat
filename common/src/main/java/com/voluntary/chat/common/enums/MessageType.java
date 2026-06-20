package com.voluntary.chat.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MessageType {

    TEXT("文本"),
    IMAGE("图片"),
    AI("AI"),
    RECALL("撤回"),
    FORWARD("转发");

    private final String description;
}
