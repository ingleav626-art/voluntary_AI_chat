package com.voluntary.chat.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TargetType {

    USER("用户"),
    GROUP("群组");

    private final String description;
}
