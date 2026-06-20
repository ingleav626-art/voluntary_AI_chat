package com.voluntary.chat.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SenderType {

    USER("用户"),
    AI("AI");

    private final String description;
}
