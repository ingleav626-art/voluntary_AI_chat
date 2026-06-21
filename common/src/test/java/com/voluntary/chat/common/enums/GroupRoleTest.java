package com.voluntary.chat.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GroupRole 枚举测试")
class GroupRoleTest {

    @Test
    @DisplayName("MEMBER 枚举值")
    void member() {
        assertEquals(0, GroupRole.MEMBER.getCode());
        assertEquals("普通成员", GroupRole.MEMBER.getDescription());
    }

    @Test
    @DisplayName("ADMIN 枚举值")
    void admin() {
        assertEquals(1, GroupRole.ADMIN.getCode());
        assertEquals("管理员", GroupRole.ADMIN.getDescription());
    }

    @Test
    @DisplayName("OWNER 枚举值")
    void owner() {
        assertEquals(2, GroupRole.OWNER.getCode());
        assertEquals("群主", GroupRole.OWNER.getDescription());
    }

    @Test
    @DisplayName("枚举数量")
    void enumCount() {
        assertEquals(3, GroupRole.values().length);
    }

    @Test
    @DisplayName("valueOf 查找")
    void valueOf() {
        assertEquals(GroupRole.MEMBER, GroupRole.valueOf("MEMBER"));
        assertEquals(GroupRole.ADMIN, GroupRole.valueOf("ADMIN"));
        assertEquals(GroupRole.OWNER, GroupRole.valueOf("OWNER"));
    }

    @Test
    @DisplayName("所有 code 值唯一")
    void allCodesShouldBeUnique() {
        long distinctCount = java.util.Arrays.stream(GroupRole.values())
                .mapToInt(GroupRole::getCode)
                .distinct()
                .count();
        assertEquals(GroupRole.values().length, distinctCount);
    }
}
