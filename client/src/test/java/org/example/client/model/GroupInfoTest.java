package org.example.client.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GroupInfo 模型测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
class GroupInfoTest {

    @Test
    @DisplayName("无参构造函数创建空对象")
    void testNoArgsConstructor() {
        final GroupInfo group = new GroupInfo();
        assertNull(group.getGroupId());
        assertNull(group.getName());
        assertNull(group.getAvatar());
        assertNull(group.getMemberCount());
        assertNull(group.getOwnerId());
        assertNull(group.getLastMessage());
        assertNull(group.getLastMessageType());
        assertNull(group.getLastMessageTime());
        assertNull(group.getUnreadCount());
    }

    @Test
    @DisplayName("全参构造函数创建完整对象")
    void testAllArgsConstructor() {
        final GroupInfo group = new GroupInfo(
                1L,
                "测试群组",
                "http://example.com/avatar.jpg",
                10,
                1001L,
                "最后一条消息",
                "TEXT",
                "2024-01-01T10:00:00Z",
                5
        );

        assertEquals(1L, group.getGroupId());
        assertEquals("测试群组", group.getName());
        assertEquals("http://example.com/avatar.jpg", group.getAvatar());
        assertEquals(10, group.getMemberCount());
        assertEquals(1001L, group.getOwnerId());
        assertEquals("最后一条消息", group.getLastMessage());
        assertEquals("TEXT", group.getLastMessageType());
        assertEquals("2024-01-01T10:00:00Z", group.getLastMessageTime());
        assertEquals(5, group.getUnreadCount());
    }

    @Test
    @DisplayName("setter 和 getter 正常工作")
    void testSetterGetter() {
        final GroupInfo group = new GroupInfo();

        group.setGroupId(2L);
        group.setName("新群组");
        group.setAvatar("http://example.com/new-avatar.jpg");
        group.setMemberCount(20);
        group.setOwnerId(1002L);
        group.setLastMessage("新消息");
        group.setLastMessageType("IMAGE");
        group.setLastMessageTime("2024-01-02T10:00:00Z");
        group.setUnreadCount(10);

        assertEquals(2L, group.getGroupId());
        assertEquals("新群组", group.getName());
        assertEquals("http://example.com/new-avatar.jpg", group.getAvatar());
        assertEquals(20, group.getMemberCount());
        assertEquals(1002L, group.getOwnerId());
        assertEquals("新消息", group.getLastMessage());
        assertEquals("IMAGE", group.getLastMessageType());
        assertEquals("2024-01-02T10:00:00Z", group.getLastMessageTime());
        assertEquals(10, group.getUnreadCount());
    }

    @Test
    @DisplayName("equals 和 hashCode 正常工作")
    void testEqualsHashCode() {
        final GroupInfo group1 = new GroupInfo(1L, "群组1", null, 10, 1001L, null, null, null, null);
        final GroupInfo group2 = new GroupInfo(1L, "群组1", null, 10, 1001L, null, null, null, null);
        final GroupInfo group3 = new GroupInfo(2L, "群组2", null, 10, 1001L, null, null, null, null);

        assertEquals(group1, group2);
        assertNotEquals(group1, group3);
        assertEquals(group1.hashCode(), group2.hashCode());
    }

    @Test
    @DisplayName("toString 包含主要字段")
    void testToString() {
        final GroupInfo group = new GroupInfo(1L, "测试群组", null, 10, 1001L, null, null, null, null);
        final String str = group.toString();

        assertTrue(str.contains("groupId=1"));
        assertTrue(str.contains("name=测试群组"));
        assertTrue(str.contains("memberCount=10"));
        assertTrue(str.contains("ownerId=1001"));
    }
}