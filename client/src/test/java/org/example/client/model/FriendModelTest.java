package org.example.client.model;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 好友相关模型测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("好友模型测试")
class FriendModelTest {

    @Test
    @DisplayName("FriendApplyRequest 构造和 Getter")
    void testFriendApplyRequest() {
        final FriendApplyRequest request = new FriendApplyRequest("13800138002", "你好，认识一下");

        assertEquals("13800138002", request.getTargetPhone());
        assertEquals("你好，认识一下", request.getMessage());
    }

    @Test
    @DisplayName("FriendApplyRequest Setter")
    void testFriendApplyRequestSetter() {
        final FriendApplyRequest request = new FriendApplyRequest();
        request.setTargetPhone("13900139003");
        request.setMessage("加个好友");

        assertEquals("13900139003", request.getTargetPhone());
        assertEquals("加个好友", request.getMessage());
    }

    @Test
    @DisplayName("FriendApplyHandleRequest 构造和 Getter")
    void testFriendApplyHandleRequest() {
        final FriendApplyHandleRequest request = new FriendApplyHandleRequest("ACCEPT");

        assertEquals("ACCEPT", request.getAction());
    }

    @Test
    @DisplayName("FriendApplyHandleRequest Setter")
    void testFriendApplyHandleRequestSetter() {
        final FriendApplyHandleRequest request = new FriendApplyHandleRequest();
        request.setAction("REJECT");

        assertEquals("REJECT", request.getAction());
    }

    @Test
    @DisplayName("FriendApplyResponse 构造和 Getter")
    void testFriendApplyResponse() {
        final LocalDateTime now = LocalDateTime.now();
        final FriendApplyResponse response = new FriendApplyResponse(
                5001L, 1001L, "张三", "http://avatar.jpg", "你好", "PENDING", now);

        assertEquals(5001L, response.getApplyId());
        assertEquals(1001L, response.getUserId());
        assertEquals("张三", response.getUsername());
        assertEquals("http://avatar.jpg", response.getAvatar());
        assertEquals("你好", response.getMessage());
        assertEquals("PENDING", response.getStatus());
        assertEquals(now, response.getCreateTime());
    }

    @Test
    @DisplayName("FriendApplyResponse Setter")
    void testFriendApplyResponseSetter() {
        final FriendApplyResponse response = new FriendApplyResponse();
        response.setApplyId(5002L);
        response.setUserId(1002L);
        response.setUsername("李四");
        response.setAvatar("http://avatar2.jpg");
        response.setMessage("认识一下");
        response.setStatus("ACCEPTED");
        response.setCreateTime(LocalDateTime.now());

        assertEquals(5002L, response.getApplyId());
        assertEquals(1002L, response.getUserId());
        assertEquals("李四", response.getUsername());
        assertEquals("http://avatar2.jpg", response.getAvatar());
        assertEquals("认识一下", response.getMessage());
        assertEquals("ACCEPTED", response.getStatus());
        assertNotNull(response.getCreateTime());
    }

    @Test
    @DisplayName("FriendResponse 构造和 Getter")
    void testFriendResponse() {
        final FriendResponse response = new FriendResponse(
                1002L, "李四", "http://avatar.jpg", "设计师", "四哥", true);

        assertEquals(1002L, response.getUserId());
        assertEquals("李四", response.getUsername());
        assertEquals("http://avatar.jpg", response.getAvatar());
        assertEquals("设计师", response.getBio());
        assertEquals("四哥", response.getRemark());
        assertTrue(response.isOnline());
    }

    @Test
    @DisplayName("FriendResponse Setter")
    void testFriendResponseSetter() {
        final FriendResponse response = new FriendResponse();
        response.setUserId(1003L);
        response.setUsername("王五");
        response.setAvatar("http://avatar3.jpg");
        response.setBio("产品经理");
        response.setRemark("五哥");
        response.setOnline(false);

        assertEquals(1003L, response.getUserId());
        assertEquals("王五", response.getUsername());
        assertEquals("http://avatar3.jpg", response.getAvatar());
        assertEquals("产品经理", response.getBio());
        assertEquals("五哥", response.getRemark());
        assertFalse(response.isOnline());
    }
}
