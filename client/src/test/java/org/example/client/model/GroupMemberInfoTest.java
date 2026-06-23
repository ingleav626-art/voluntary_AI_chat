package org.example.client.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GroupMemberInfo 模型测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
class GroupMemberInfoTest {

    @Test
    @DisplayName("无参构造函数创建空对象")
    void testNoArgsConstructor() {
        final GroupMemberInfo member = new GroupMemberInfo();
        assertNull(member.getUserId());
        assertNull(member.getUsername());
        assertNull(member.getAvatar());
        assertNull(member.getRole());
        assertNull(member.getJoinTime());
        assertNull(member.getNickname());
    }

    @Test
    @DisplayName("全参构造函数创建完整对象")
    void testAllArgsConstructor() {
        final GroupMemberInfo member = new GroupMemberInfo(
                1001L,
                "测试用户",
                "http://example.com/avatar.jpg",
                "OWNER",
                "2024-01-01T00:00:00Z",
                "群主昵称"
        );

        assertEquals(1001L, member.getUserId());
        assertEquals("测试用户", member.getUsername());
        assertEquals("http://example.com/avatar.jpg", member.getAvatar());
        assertEquals("OWNER", member.getRole());
        assertEquals("2024-01-01T00:00:00Z", member.getJoinTime());
        assertEquals("群主昵称", member.getNickname());
    }

    @Test
    @DisplayName("setter 和 getter 正常工作")
    void testSetterGetter() {
        final GroupMemberInfo member = new GroupMemberInfo();

        member.setUserId(1002L);
        member.setUsername("新用户");
        member.setAvatar("http://example.com/new-avatar.jpg");
        member.setRole("ADMIN");
        member.setJoinTime("2024-01-02T00:00:00Z");
        member.setNickname("管理员昵称");

        assertEquals(1002L, member.getUserId());
        assertEquals("新用户", member.getUsername());
        assertEquals("http://example.com/new-avatar.jpg", member.getAvatar());
        assertEquals("ADMIN", member.getRole());
        assertEquals("2024-01-02T00:00:00Z", member.getJoinTime());
        assertEquals("管理员昵称", member.getNickname());
    }

    @Test
    @DisplayName("equals 和 hashCode 正常工作")
    void testEqualsHashCode() {
        final GroupMemberInfo member1 = new GroupMemberInfo(1001L, "用户1", null, "OWNER", null, null);
        final GroupMemberInfo member2 = new GroupMemberInfo(1001L, "用户1", null, "OWNER", null, null);
        final GroupMemberInfo member3 = new GroupMemberInfo(1002L, "用户2", null, "ADMIN", null, null);

        assertEquals(member1, member2);
        assertNotEquals(member1, member3);
        assertEquals(member1.hashCode(), member2.hashCode());
    }

    @Test
    @DisplayName("toString 包含主要字段")
    void testToString() {
        final GroupMemberInfo member = new GroupMemberInfo(1001L, "测试用户", null, "OWNER", null, null);
        final String str = member.toString();

        assertTrue(str.contains("userId=1001"));
        assertTrue(str.contains("username=测试用户"));
        assertTrue(str.contains("role=OWNER"));
    }

    @Test
    @DisplayName("角色类型验证")
    void testRoleTypes() {
        final GroupMemberInfo owner = new GroupMemberInfo();
        owner.setRole("OWNER");

        final GroupMemberInfo admin = new GroupMemberInfo();
        admin.setRole("ADMIN");

        final GroupMemberInfo member = new GroupMemberInfo();
        member.setRole("MEMBER");

        assertEquals("OWNER", owner.getRole());
        assertEquals("ADMIN", admin.getRole());
        assertEquals("MEMBER", member.getRole());
    }
}