package org.example.client.model;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 消息相关模型测试
 *
 * <p>覆盖 SendMessageResponse、ImageUploadResponse 等未测试的模型。</p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("消息模型测试")
class MessageModelTest {

    @Test
    @DisplayName("SendMessageResponse 全参构造和 Getter")
    void testSendMessageResponseAllArgs() {
        final LocalDateTime now = LocalDateTime.now();
        final SendMessageResponse response = new SendMessageResponse(1001L, now);

        assertEquals(1001L, response.getMessageId());
        assertEquals(now, response.getCreateTime());
    }

    @Test
    @DisplayName("SendMessageResponse 无参构造和 Setter")
    void testSendMessageResponseSetter() {
        final SendMessageResponse response = new SendMessageResponse();
        final LocalDateTime now = LocalDateTime.now();
        response.setMessageId(2002L);
        response.setCreateTime(now);

        assertEquals(2002L, response.getMessageId());
        assertEquals(now, response.getCreateTime());
    }

    @Test
    @DisplayName("SendMessageResponse 默认值为 null")
    void testSendMessageResponseDefaults() {
        final SendMessageResponse response = new SendMessageResponse();

        assertNull(response.getMessageId());
        assertNull(response.getCreateTime());
    }

    @Test
    @DisplayName("ImageUploadResponse 无参构造和 Setter")
    void testImageUploadResponseSetter() {
        final ImageUploadResponse response = new ImageUploadResponse();
        response.setFileId("file_001");
        response.setUrl("http://example.com/image/001.jpg");
        response.setThumbnailUrl("http://example.com/image/001_thumb.jpg");
        response.setWidth(1920);
        response.setHeight(1080);
        response.setSize(1024000L);
        response.setFileType("image/jpeg");

        assertEquals("file_001", response.getFileId());
        assertEquals("http://example.com/image/001.jpg", response.getUrl());
        assertEquals("http://example.com/image/001_thumb.jpg", response.getThumbnailUrl());
        assertEquals(1920, response.getWidth());
        assertEquals(1080, response.getHeight());
        assertEquals(1024000L, response.getSize());
        assertEquals("image/jpeg", response.getFileType());
    }

    @Test
    @DisplayName("ImageUploadResponse 默认值为 null")
    void testImageUploadResponseDefaults() {
        final ImageUploadResponse response = new ImageUploadResponse();

        assertNull(response.getFileId());
        assertNull(response.getUrl());
        assertNull(response.getThumbnailUrl());
        assertNull(response.getWidth());
        assertNull(response.getHeight());
        assertNull(response.getSize());
        assertNull(response.getFileType());
    }
}
