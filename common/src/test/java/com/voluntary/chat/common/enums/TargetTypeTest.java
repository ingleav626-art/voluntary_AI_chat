package com.voluntary.chat.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TargetType 枚举测试")
class TargetTypeTest {

    @Test
    @DisplayName("USER 枚举值")
    void user() {
        assertEquals("用户", TargetType.USER.getDescription());
    }

    @Test
    @DisplayName("GROUP 枚举值")
    void group() {
        assertEquals("群组", TargetType.GROUP.getDescription());
    }

    @Test
    @DisplayName("枚举数量")
    void enumCount() {
        assertEquals(2, TargetType.values().length);
    }

    @Test
    @DisplayName("valueOf 查找")
    void valueOf() {
        assertEquals(TargetType.USER, TargetType.valueOf("USER"));
        assertEquals(TargetType.GROUP, TargetType.valueOf("GROUP"));
    }
}
